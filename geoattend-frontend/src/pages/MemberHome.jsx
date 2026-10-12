import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  ArrowUpRight,
  Calendar,
  CalendarDays,
  CheckCircle2,
  Clock,
  Clock3,
  Compass,
  History,
  Info,
  MapPin,
  Navigation,
  RefreshCw,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import attendanceService from "../services/attendanceService";
import sessionService from "../services/sessionService";

const MAX_STALE_POSITION_AGE_MS = 45 * 1000;

export default function MemberHome({ user, showToast }) {
  const [sessions, setSessions] = useState([]);
  const [attendance, setAttendance] = useState([]);
  const [trackingList, setTrackingList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState("");
  const [currentTime, setCurrentTime] = useState(Date.now);
  const [telemetry, setTelemetry] = useState(null);
  const [checkingLocation, setCheckingLocation] = useState(false);
  const [permissionState, setPermissionState] = useState("unknown"); // "granted" | "prompt" | "denied" | "unknown"
  const [activeTab, setActiveTab] = useState("active"); // "active" | "upcoming" | "history"

  const fetchData = useCallback(async (silent = false) => {
    try {
      if (!silent) {
        setLoading(true);
        setFetchError("");
      }
      const [sessionList, attendanceList, memberTracking] = await Promise.all([
        sessionService.listSessions(),
        attendanceService.getMyAttendance(),
        attendanceService.getMyTracking(),
      ]);

      setSessions(sessionList || []);
      setAttendance(attendanceList || []);
      setTrackingList(memberTracking || []);
    } catch (error) {
      const msg = error.message || "Unable to load attendance data.";
      if (!silent) setFetchError(msg);
      showToast(msg, "error");
    } finally {
      if (!silent) setLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Query browser location permission state if supported
  useEffect(() => {
    if (navigator.permissions && navigator.permissions.query) {
      navigator.permissions
        .query({ name: "geolocation" })
        .then((status) => {
          setPermissionState(status.state);
          status.onchange = () => setPermissionState(status.state);
        })
        .catch(() => {
          setPermissionState("unknown");
        });
    }
  }, []);

  // Listen to live telemetry from GeoAttendanceTracker
  useEffect(() => {
    const handleTelemetry = (event) => {
      if (event.detail) {
        setTelemetry(event.detail);
      }
    };
    window.addEventListener("geoattend:telemetry", handleTelemetry);
    return () => window.removeEventListener("geoattend:telemetry", handleTelemetry);
  }, []);

  // Periodic polling for status refresh
  useEffect(() => {
    const timer = window.setInterval(() => {
      fetchData(true);
    }, 25000);
    return () => window.clearInterval(timer);
  }, [fetchData]);

  // Update clock every 10 seconds for session timing calculations
  useEffect(() => {
    const timer = window.setInterval(() => setCurrentTime(Date.now()), 10000);
    return () => window.clearInterval(timer);
  }, []);

  // Filter sessions by lifecycle status
  const { activeSessions, upcomingSessions, completedSessions } = useMemo(() => {
    const active = [];
    const upcoming = [];
    const completed = [];

    sessions.forEach((session) => {
      const start = new Date(session.startTime).getTime();
      const end = new Date(session.endTime).getTime();
      if (currentTime >= start && currentTime <= end) {
        active.push(session);
      } else if (currentTime < start) {
        upcoming.push(session);
      } else {
        completed.push(session);
      }
    });

    upcoming.sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
    completed.sort((a, b) => new Date(b.endTime) - new Date(a.endTime));

    return {
      activeSessions: active,
      upcomingSessions: upcoming,
      completedSessions: completed,
    };
  }, [sessions, currentTime]);

  const activeSession = activeSessions.find((s) => Boolean(s.locationEnabled ?? true)) || activeSessions[0];
  const activeTracking = activeSession
    ? trackingList.find((item) => String(item.sessionId) === String(activeSession.id))
    : null;
  const alreadyCheckedIn = activeSession
    ? attendance.some((item) => String(item.sessionId) === String(activeSession.id))
    : false;

  const checkIntervalMinutes = activeTracking?.checkIntervalSeconds
    ? Math.max(1, Math.round(activeTracking.checkIntervalSeconds / 60))
    : null;

  // Manual Check-in action triggered directly by the member
  const handleManualCheckIn = async () => {
    if (!activeSession) return;
    if (!navigator.geolocation) {
      showToast("Geolocation is not supported by your browser.", "error");
      return;
    }

    try {
      setCheckingLocation(true);

      const location = await new Promise((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(
          (pos) => {
            const now = Date.now();
            const posTime = Number(pos.timestamp) || now;
            if (now - posTime > MAX_STALE_POSITION_AGE_MS) {
              reject(new Error("Stale GPS fix returned. Please retry."));
              return;
            }
            resolve({
              latitude: pos.coords.latitude,
              longitude: pos.coords.longitude,
              accuracyMeters: pos.coords.accuracy,
            });
          },
          (err) => reject(err),
          { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 },
        );
      });

      // Ensure tracking session exists
      if (!activeTracking) {
        await attendanceService.startMyTracking({ sessionId: activeSession.id });
      }

      const result = await attendanceService.recordLocationCheck({
        sessionId: activeSession.id,
        latitude: location.latitude,
        longitude: location.longitude,
        accuracyMeters: location.accuracyMeters,
      });

      setTelemetry({
        sessionId: activeSession.id,
        sessionTitle: activeSession.title,
        status: result.status,
        insideGeofence: result.insideGeofence,
        distanceMeters: result.distanceMeters,
        accuracyMeters: result.accuracyMeters,
        accuracyStatus: result.accuracyStatus,
        boundaryToleranceMeters: result.boundaryToleranceMeters,
        evaluationReason: result.evaluationReason,
        radiusMeters: result.radiusMeters,
        nextCheckAt: result.nextCheckAt ? new Date(result.nextCheckAt).getTime() : Date.now() + 60000,
        sampleRecorded: result.sampleRecorded,
        lastUpdated: Date.now(),
        error: null,
      });

      if (result.insideGeofence) {
        showToast(
          `Location verified! You are inside the venue (±${Math.round(location.accuracyMeters)}m).`,
          "success",
        );
      } else {
        showToast(
          `Location recorded: ${result.distanceMeters != null ? Number(result.distanceMeters).toFixed(0) : "?"}m from venue center (radius: ${result.radiusMeters}m).`,
          result.accuracyStatus === "POOR_ACCURACY" ? "error" : "info",
        );
      }

      await fetchData(true);
    } catch (error) {
      let msg = error.message || "Failed to acquire device location.";
      if (error.code === 1) {
        msg = "Location permission denied. Please allow location access in your browser settings.";
        setPermissionState("denied");
      } else if (error.code === 2) {
        msg = "Location unavailable. Ensure GPS is enabled on your device.";
      } else if (error.code === 3) {
        msg = "Location acquisition timed out. Please retry near a window or with Wi-Fi enabled.";
      }
      showToast(msg, "error");
    } finally {
      setCheckingLocation(false);
    }
  };

  const getTimeUntilStart = (startTime) => {
    const diffMs = new Date(startTime).getTime() - currentTime;
    if (diffMs <= 0) return "Starting now";
    const mins = Math.floor(diffMs / 60000);
    if (mins < 60) return `Starts in ${mins}m`;
    const hours = Math.floor(mins / 60);
    const remMins = mins % 60;
    return `Starts in ${hours}h ${remMins > 0 ? `${remMins}m` : ""}`;
  };

  if (loading) {
    return <Loading message="Loading member attendance portal..." />;
  }

  if (fetchError && !sessions.length && !attendance.length) {
    return (
      <div className="member-dashboard">
        <EmptyState
          icon={<AlertCircle size={36} className="text-danger" />}
          title="Unable to load portal"
          description={fetchError}
          actionLabel="Retry"
          onAction={() => fetchData(false)}
        />
      </div>
    );
  }

  return (
    <div className="member-dashboard">
      {/* Greeting Header */}
      <section className="member-greeting-card">
        <div className="member-greeting-info">
          <span className="member-badge-eyebrow">
            <Smartphone size={14} /> Oyster Kode Club · Member Portal
          </span>
          <h2>Welcome back, {user?.name?.split(" ")[0] || "Member"}!</h2>
          <p className="member-greeting-date">
            <Calendar size={14} />
            {new Date(currentTime).toLocaleDateString([], {
              weekday: "long",
              month: "short",
              day: "numeric",
            })}
          </p>
        </div>

        <div className="member-quick-stats">
          <div className="member-mini-stat">
            <span className="mini-stat-value">{attendance.length}</span>
            <span className="mini-stat-label">Total Attended</span>
          </div>
          <div className="member-mini-stat">
            <span className="mini-stat-value">{activeSessions.length}</span>
            <span className="mini-stat-label">Live Now</span>
          </div>
        </div>
      </section>

      {/* Location Permission Guidance Alert if not granted */}
      {permissionState === "denied" && (
        <div className="member-permission-alert danger" role="alert">
          <AlertTriangle size={20} />
          <div className="permission-alert-copy">
            <strong>Location access is blocked</strong>
            <p>
              Your browser is blocking location permissions. To verify geofence presence, tap the
              lock/site-settings icon in your address bar and set <em>Location</em> to <em>Allow</em>.
            </p>
          </div>
          <button
            type="button"
            className="secondary-button compact"
            onClick={handleManualCheckIn}
          >
            Retry Permission
          </button>
        </div>
      )}

      {permissionState === "prompt" && (
        <div className="member-permission-alert info" role="status">
          <Info size={20} />
          <div className="permission-alert-copy">
            <strong>Location permission pending</strong>
            <p>
              Automatic geofence verification requires device GPS permission. When prompted, tap
              &quot;Allow while visiting&quot;.
            </p>
          </div>
          <button
            type="button"
            className="primary-button compact"
            onClick={handleManualCheckIn}
          >
            Allow Location
          </button>
        </div>
      )}

      {/* Navigation Tabs for Mobile & Desktop: Active / Upcoming / History */}
      <div className="member-tabs-nav" role="tablist" aria-label="Session categories">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "active"}
          className={`member-tab-button ${activeTab === "active" ? "active" : ""}`}
          onClick={() => setActiveTab("active")}
        >
          <span className="tab-dot live" />
          <span>Live Check-in</span>
          {activeSessions.length > 0 && <span className="tab-count live">{activeSessions.length}</span>}
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "upcoming"}
          className={`member-tab-button ${activeTab === "upcoming" ? "active" : ""}`}
          onClick={() => setActiveTab("upcoming")}
        >
          <CalendarDays size={15} />
          <span>Upcoming</span>
          {upcomingSessions.length > 0 && <span className="tab-count">{upcomingSessions.length}</span>}
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "history"}
          className={`member-tab-button ${activeTab === "history" ? "active" : ""}`}
          onClick={() => setActiveTab("history")}
        >
          <History size={15} />
          <span>Recent Sessions</span>
        </button>
      </div>

      {/* TAB 1: ACTIVE / LIVE CHECK-IN */}
      {activeTab === "active" && (
        <div className="member-tab-content">
          {activeSession ? (
            <section className={`member-checkin-hero ${alreadyCheckedIn ? "hero-finalized" : "hero-active"}`}>
              {/* Header Badge */}
              <div className="member-checkin-topline">
                <span className={`checkin-state ${alreadyCheckedIn ? "success" : "ready"}`}>
                  <span className="checkin-state-dot" />
                  {alreadyCheckedIn
                    ? "ATTENDANCE FINALIZED & VERIFIED"
                    : "GEOFENCE SESSION IN PROGRESS"}
                </span>
                <span className="checkin-secure">
                  <ShieldCheck size={15} /> Authoritative Server Verification
                </span>
              </div>

              {/* Session Core Info */}
              <div className="member-checkin-body">
                <div className="member-checkin-details">
                  <span className="session-category-label">Active Club Session</span>
                  <h3 className="member-session-title">{activeSession.title}</h3>

                  <div className="member-session-meta-grid">
                    <span className="meta-pill">
                      <Clock size={14} />
                      {new Date(activeSession.startTime).toLocaleTimeString([], {
                        hour: "numeric",
                        minute: "2-digit",
                      })}{" "}
                      –{" "}
                      {new Date(activeSession.endTime).toLocaleTimeString([], {
                        hour: "numeric",
                        minute: "2-digit",
                      })}
                    </span>
                    <span className="meta-pill">
                      <MapPin size={14} />
                      Radius: {activeSession.radiusMeters} m
                    </span>
                  </div>
                </div>

                {/* Primary Action Button */}
                <div className="member-action-cta">
                  {alreadyCheckedIn ? (
                    <div className="checked-in-banner">
                      <CheckCircle2 size={24} className="text-success" />
                      <div>
                        <strong>You are marked present!</strong>
                        <p>Attendance recorded successfully for this session.</p>
                      </div>
                    </div>
                  ) : (
                    <button
                      type="button"
                      className="primary-button hero-checkin-btn"
                      onClick={handleManualCheckIn}
                      disabled={checkingLocation}
                    >
                      {checkingLocation ? (
                        <>
                          <RefreshCw size={18} className="spinner-icon" />
                          <span>Acquiring GPS fix...</span>
                        </>
                      ) : (
                        <>
                          <Navigation size={18} />
                          <span>Verify My Location Now</span>
                        </>
                      )}
                    </button>
                  )}
                </div>
              </div>

              {/* 10-Slot Tracking Progress Bar */}
              {!alreadyCheckedIn && activeTracking && (
                <div className="member-slot-tracker">
                  <div className="slot-tracker-header">
                    <span className="slot-title">
                      <strong>10-Interval Presence Verification</strong>
                    </span>
                    <span className="slot-summary">
                      {activeTracking.insideChecks} / {activeTracking.expectedChecks || 10} intervals inside
                      ({Math.round(((activeTracking.insideChecks || 0) / (activeTracking.expectedChecks || 10)) * 100)}%)
                    </span>
                  </div>

                  <div className="slot-progress-bar">
                    <div
                      className="slot-progress-fill"
                      style={{
                        width: `${Math.min(
                          100,
                          ((activeTracking.insideChecks || 0) / (activeTracking.expectedChecks || 10)) * 100,
                        )}%`,
                      }}
                    />
                  </div>
                  <div className="slot-hint">
                    <Info size={13} />
                    <span>
                      80% in-geofence presence required to verify attendance automatically. Keep this tab
                      open. Checks run every {checkIntervalMinutes || 1} min.
                    </span>
                  </div>
                </div>
              )}

              {/* Live Telemetry Card */}
              <div className="telemetry-diagnostics-card">
                <div className="telemetry-header">
                  <div className="telemetry-title">
                    <Compass size={17} className="telemetry-icon" />
                    <span>Real-Time Geolocation Diagnostics</span>
                  </div>
                  {telemetry?.accuracyStatus && (
                    <span className={`telemetry-accuracy-badge ${telemetry.accuracyStatus.toLowerCase()}`}>
                      {telemetry.accuracyStatus === "HIGH"
                        ? "HIGH ACCURACY GPS"
                        : telemetry.accuracyStatus === "ACCEPTABLE"
                          ? "ACCEPTABLE FIX"
                          : "POOR ACCURACY"}
                    </span>
                  )}
                </div>

                {telemetry?.error ? (
                  <div className="telemetry-error-alert" role="alert">
                    <AlertTriangle size={16} />
                    <div>
                      <strong>
                        {telemetry.error.category === "PERMISSION_DENIED"
                          ? "Location Permission Denied"
                          : "Location Signal Issue"}
                      </strong>
                      <p>{telemetry.error.message}</p>
                    </div>
                  </div>
                ) : telemetry ? (
                  <div className="telemetry-metrics-grid">
                    <div className="telemetry-metric">
                      <span className="telemetry-label">Reported Accuracy</span>
                      <span className="telemetry-value">
                        {telemetry.accuracyMeters != null
                          ? `±${Number(telemetry.accuracyMeters).toFixed(1)} m`
                          : "Unavailable"}
                      </span>
                      <span className="telemetry-hint">
                        {telemetry.accuracyStatus === "HIGH"
                          ? "Satellite lock acquired"
                          : telemetry.accuracyStatus === "ACCEPTABLE"
                            ? "Cellular / Wi-Fi fix"
                            : "Low precision"}
                      </span>
                    </div>

                    <div className="telemetry-metric">
                      <span className="telemetry-label">Distance to Center</span>
                      <span className="telemetry-value">
                        {telemetry.distanceMeters != null
                          ? `${Number(telemetry.distanceMeters).toFixed(1)} m`
                          : "Calculating..."}
                      </span>
                      <span className="telemetry-hint">
                        Geofence: {telemetry.radiusMeters || activeSession.radiusMeters} m
                      </span>
                    </div>

                    <div className="telemetry-metric">
                      <span className="telemetry-label">Perimeter Status</span>
                      <span className={`telemetry-value ${telemetry.insideGeofence ? "text-success" : "text-danger"}`}>
                        {telemetry.insideGeofence ? "Inside Venue" : "Outside Geofence"}
                      </span>
                      <span className="telemetry-hint">
                        {telemetry.insideGeofence ? "Valid check recorded" : "Move closer to venue center"}
                      </span>
                    </div>

                    <div className="telemetry-metric">
                      <span className="telemetry-label">Next Automatic Check</span>
                      <span className="telemetry-value">
                        {telemetry.nextCheckAt
                          ? new Date(telemetry.nextCheckAt).toLocaleTimeString([], {
                              hour: "numeric",
                              minute: "2-digit",
                            })
                          : "Scheduled"}
                      </span>
                      <span className="telemetry-hint">
                        {alreadyCheckedIn
                          ? "Complete"
                          : "Automatic while page stays open"}
                      </span>
                    </div>
                  </div>
                ) : (
                  <div className="telemetry-standby">
                    <Navigation size={18} className="telemetry-standby-icon" />
                    <p>
                      Keep this page active. Device location checks run periodically during the session.
                    </p>
                  </div>
                )}

                <div className="telemetry-disclosure-note">
                  <small>
                    <strong>GPS & Privacy Disclosure:</strong> GeoAttend checks your coordinates solely
                    against the scheduled venue boundary during club sessions to confirm physical presence.
                    No background location is recorded outside session windows.
                  </small>
                </div>
              </div>
            </section>
          ) : (
            <div className="member-idle-card">
              <div className="idle-card-icon">
                <MapPin size={28} />
              </div>
              <div className="idle-card-copy">
                <h3>No Geofence Session Is Active</h3>
                <p>
                  There are no scheduled attendance sessions running at this moment. You will be able
                  to verify your location here as soon as the club session begins.
                </p>
                {upcomingSessions.length > 0 && (
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => setActiveTab("upcoming")}
                  >
                    View Next Session ({getTimeUntilStart(upcomingSessions[0].startTime)})
                  </button>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: UPCOMING SESSIONS */}
      {activeTab === "upcoming" && (
        <div className="member-tab-content">
          <div className="member-section-header">
            <div>
              <h3>Upcoming Sessions</h3>
              <p>Schedule of upcoming Oyster Kode Club meetings and workshops.</p>
            </div>
          </div>

          {upcomingSessions.length > 0 ? (
            <div className="upcoming-sessions-list">
              {upcomingSessions.map((session) => (
                <div key={session.id} className="upcoming-session-card">
                  <div className="upcoming-card-header">
                    <div>
                      <span className="upcoming-time-tag">
                        {getTimeUntilStart(session.startTime)}
                      </span>
                      <h4>{session.title}</h4>
                    </div>
                  </div>

                  <div className="upcoming-card-meta">
                    <span className="meta-item">
                      <Calendar size={15} />
                      {new Date(session.startTime).toLocaleDateString([], {
                        weekday: "short",
                        month: "short",
                        day: "numeric",
                      })}
                    </span>
                    <span className="meta-item">
                      <Clock3 size={15} />
                      {new Date(session.startTime).toLocaleTimeString([], {
                        hour: "numeric",
                        minute: "2-digit",
                      })}{" "}
                      –{" "}
                      {new Date(session.endTime).toLocaleTimeString([], {
                        hour: "numeric",
                        minute: "2-digit",
                      })}
                    </span>
                    <span className="meta-item">
                      <MapPin size={15} />
                      Geofence Radius: {session.radiusMeters} m
                    </span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              icon={<CalendarDays size={32} />}
              title="No upcoming sessions"
              description="Club organizers have not scheduled any upcoming sessions yet."
            />
          )}
        </div>
      )}

      {/* TAB 3: RECENT COMPLETED SESSIONS */}
      {activeTab === "history" && (
        <div className="member-tab-content">
          <div className="member-section-header">
            <div>
              <h3>Recent Sessions</h3>
              <p>Past sessions and your attendance records.</p>
            </div>
            <Link to="/member/attendance" className="quiet-link">
              Full Attendance History <ArrowRight size={15} />
            </Link>
          </div>

          {completedSessions.length > 0 ? (
            <div className="completed-sessions-list">
              {completedSessions.slice(0, 10).map((session) => {
                const attended = attendance.some((att) => String(att.sessionId) === String(session.id));
                const tracking = trackingList.find((tr) => String(tr.sessionId) === String(session.id));
                return (
                  <div key={session.id} className="completed-session-card">
                    <div className="completed-card-info">
                      <h4>{session.title}</h4>
                      <div className="completed-card-meta">
                        <span>
                          <Calendar size={14} />
                          {new Date(session.startTime).toLocaleDateString([], {
                            month: "short",
                            day: "numeric",
                          })}
                        </span>
                        <span>
                          <Clock3 size={14} />
                          {new Date(session.startTime).toLocaleTimeString([], {
                            hour: "numeric",
                            minute: "2-digit",
                          })}
                        </span>
                      </div>
                    </div>

                    <div className="completed-card-status">
                      {attended ? (
                        <span className="badge badge-success">
                          <CheckCircle2 size={13} /> Present
                        </span>
                      ) : tracking?.status === "REVIEW" ? (
                        <span className="badge badge-warning">
                          <AlertTriangle size={13} /> Staff Review
                        </span>
                      ) : (
                        <span className="badge badge-neutral">Absent</span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <EmptyState
              icon={<History size={32} />}
              title="No completed sessions"
              description="Past session records will be displayed here once sessions finish."
            />
          )}
        </div>
      )}

      {/* Quick History Preview Footer Link */}
      <section className="member-history-summary-card">
        <div className="summary-card-text">
          <History size={20} className="summary-icon" />
          <div>
            <strong>Personal Attendance Log</strong>
            <p>
              View detailed timestamps, geofence methods, and tracking verification breakdown.
            </p>
          </div>
        </div>
        <Link to="/member/attendance" className="secondary-button compact">
          Open History <ArrowUpRight size={15} />
        </Link>
      </section>
    </div>
  );
}
