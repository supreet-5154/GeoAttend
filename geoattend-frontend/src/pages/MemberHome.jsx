import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, CalendarDays, Clock3, MapPin, ShieldCheck } from "lucide-react";
import Loading from "../components/Loading";
import attendanceService from "../services/attendanceService";
import sessionService from "../services/sessionService";

export default function MemberHome({ user, showToast }) {
  const [sessions, setSessions] = useState([]);
  const [attendance, setAttendance] = useState([]);
  const [trackingList, setTrackingList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentTime, setCurrentTime] = useState(Date.now);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [sessionList, attendanceList, memberTracking] = await Promise.all([
        sessionService.listSessions(),
        attendanceService.getMyAttendance(),
        attendanceService.getMyTracking(),
      ]);

      setSessions(sessionList);
      setAttendance(attendanceList || []);
      setTrackingList(memberTracking || []);
    } catch (error) {
      showToast(error.message || "Unable to load your attendance data.", "error");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [showToast]);

  useEffect(() => {
    const timer = window.setInterval(async () => {
      try {
        const [memberTracking, attendanceList] = await Promise.all([
          attendanceService.getMyTracking(),
          attendanceService.getMyAttendance(),
        ]);
        setTrackingList(memberTracking || []);
        setAttendance(attendanceList || []);
      } catch (error) {
        showToast(error.message || "Unable to refresh attendance status.", "error");
      }
    }, 30000);
    return () => window.clearInterval(timer);
  }, [showToast]);

  useEffect(() => {
    const timer = window.setInterval(() => setCurrentTime(Date.now()), 30000);
    return () => window.clearInterval(timer);
  }, []);

  const activeSessions = useMemo(() => {
    return sessions.filter((session) => {
      const start = new Date(session.startTime).getTime();
      const end = new Date(session.endTime).getTime();
      return currentTime >= start && currentTime <= end;
    });
  }, [sessions, currentTime]);

  const checkInSession = activeSessions.find((session) => Boolean(session.locationEnabled ?? true));
  const latestTracking = trackingList[0];
  const currentTracking = checkInSession
    ? trackingList.find((item) => String(item.sessionId) === String(checkInSession.id))
    : null;
  const checkIntervalMinutes = currentTracking?.checkIntervalSeconds
    ? Math.max(1, Math.round(currentTracking.checkIntervalSeconds / 60))
    : null;
  const alreadyCheckedIn = checkInSession
    ? attendance.some((item) => String(item.sessionId) === String(checkInSession.id))
    : false;

  const recentAttendance = useMemo(() => {
    return attendance.slice(0, 5);
  }, [attendance]);

  if (loading) {
    return <Loading message="Loading your attendance portal..." />;
  }

  return (
    <div className="member-dashboard">
      <section className="member-greeting">
        <div>
          <p className="eyebrow">Member portal</p>
          <h2>Welcome back, {user?.name?.split(" ")[0] || "Member"}.</h2>
          <p>Your check-in status and recent activity.</p>
        </div>
      </section>

      <section className={`member-checkin ${checkInSession ? "member-checkin-ready" : ""}`}>
        <div className="member-checkin-topline">
          <span className={`checkin-state ${checkInSession ? "ready" : "idle"}`}>
            <span className="checkin-state-dot" />{checkInSession ? "GEOFENCE SESSION LIVE" : "CHECK-IN UNAVAILABLE"}
          </span>
          <span className="checkin-secure"><ShieldCheck size={15} /> Verified on site</span>
        </div>

        {checkInSession ? (
          <>
            <div className="member-checkin-content">
              <div>
                <p className="eyebrow">Active session</p>
                <h3>{checkInSession.title}</h3>
                <p className="member-checkin-prompt">
                  {alreadyCheckedIn
                    ? "Your attendance has been finalized for this session."
                    : currentTracking?.status === "TRACKING"
                      ? "Automatic location checks are running while this page stays open."
                      : currentTracking?.status === "WAITING_FOR_GEOFENCE"
                        ? `The session is being monitored. Location checks run every ${checkIntervalMinutes || "scheduled"} minute${checkIntervalMinutes === 1 ? "" : "s"}.`
                      : "Location verification starts automatically when you are inside the venue."}
                </p>
              </div>
              <div className="member-session-meta">
                <span><CalendarDays size={16} />{new Date(checkInSession.startTime).toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" })}</span>
                <span><Clock3 size={16} />{new Date(checkInSession.startTime).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })} – {new Date(checkInSession.endTime).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</span>
                <span><MapPin size={16} />Within {checkInSession.radiusMeters} m of the session</span>
              </div>
            </div>

            <div className={`tracking-status ${alreadyCheckedIn ? "finalized" : currentTracking?.status === "REVIEW" ? "needs-review" : ""}`}>
              <span className="tracking-status-pip" />
              <div>
                <strong>
                  {alreadyCheckedIn
                    ? "Attendance finalized"
                    : currentTracking?.status === "TRACKING"
                      ? "Automatic tracking active"
                      : currentTracking?.status === "WAITING_FOR_GEOFENCE"
                        ? currentTracking.totalChecks === 0 ? "Waiting for your first location check" : "Outside the session geofence"
                      : currentTracking?.status === "REVIEW"
                        ? "Staff review required"
                        : "Waiting for an in-geofence location check"}
                </strong>
                <small>
                  {alreadyCheckedIn
                    ? "Your attendance was recorded when the session ended."
                    : currentTracking?.status === "TRACKING"
                      ? `${currentTracking.insideChecks} of ${currentTracking.expectedChecks} expected checks are currently inside the geofence. Next check is automatic.`
                      : currentTracking?.status === "WAITING_FOR_GEOFENCE"
                        ? currentTracking.totalChecks === 0
                          ? "Allow location access and keep this page open; automatic checks are already scheduled."
                          : `${currentTracking.insideChecks} of ${currentTracking.expectedChecks} expected checks are inside so far. Checks run every ${checkIntervalMinutes || "scheduled"} minute${checkIntervalMinutes === 1 ? "" : "s"}.`
                      : currentTracking?.status === "REVIEW"
                        ? `${currentTracking.insideChecks} of ${currentTracking.expectedChecks} expected checks were inside the geofence. Contact staff if this needs correction.`
                        : "Allow browser location access and keep this page open during the session."}
                </small>
              </div>
            </div>
          </>
        ) : (
          <div className="member-checkin-empty">
            <div className="checkin-empty-icon"><MapPin size={21} /></div>
            <div>
              <h3>{latestTracking?.status === "REVIEW" ? "Your last session needs review" : "No geofence session is active"}</h3>
              <p>
                {latestTracking?.status === "REVIEW"
                  ? `${latestTracking.insideChecks} of ${latestTracking.expectedChecks} expected checks were inside the geofence. Staff can review this in person.`
                  : latestTracking?.status === "PRESENT"
                    ? `Attendance for ${latestTracking.sessionTitle} was finalized automatically.`
                    : activeSessions.length
                      ? "An active session is not configured for member location check-in."
                      : "When your session opens, location checks will start automatically while this page is open."}
              </p>
            </div>
          </div>
        )}
      </section>

      <section className="member-history-preview">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Your record</p>
            <h3>Recent attendance <span className="history-count">{attendance.length}</span></h3>
          </div>
          <Link className="quiet-link" to="/member/attendance">Full history <ArrowUpRight size={15} /></Link>
        </div>

        {recentAttendance.length ? (
          <div className="member-list">
            {recentAttendance.map((item) => (
              <div key={item.id} className="member-item">
                <div>
                  <strong>{item.sessionTitle || "Attendance"}</strong>
                  <small><MapPin size={13} />{item.attendanceMethod || "GEOFENCE"}</small>
                </div>
                <span>{new Date(item.markedAt).toLocaleDateString([], { month: "short", day: "numeric" })}</span>
              </div>
            ))}
          </div>
        ) : (
          <div className="member-empty">No attendance recorded yet.</div>
        )}
      </section>
    </div>
  );
}
