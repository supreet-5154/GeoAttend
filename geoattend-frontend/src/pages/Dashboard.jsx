import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  CheckCircle2,
  Clock3,
  Layers,
  MapPin,
  ShieldAlert,
  UserCheck,
  UsersRound,
} from "lucide-react";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import StatCard from "../components/StatCard";
import attendanceService from "../services/attendanceService";
import batchService from "../services/batchService";
import memberService from "../services/memberService";
import sessionService from "../services/sessionService";
import { formatDateTime } from "../services/api";

export default function Dashboard({ user, showToast }) {
  const [now, setNow] = useState(() => Date.now());
  const [members, setMembers] = useState([]);
  const [batches, setBatches] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [attendance, setAttendance] = useState([]);
  const [pendingSignupsCount, setPendingSignupsCount] = useState(0);
  const [loading, setLoading] = useState(true);

  // Periodic clock update
  useEffect(() => {
    const clockTimer = window.setInterval(() => setNow(Date.now()), 15000);
    return () => window.clearInterval(clockTimer);
  }, []);

  const loadDashboard = async (silent = false) => {
    try {
      if (!silent) setLoading(true);

      const canReviewSignups = user?.role === "ADMIN" || user?.role === "CLUB_USER";

      const [memberList, batchList, sessionList, signupsList] = await Promise.all([
        memberService.listMembers(),
        batchService.listBatches(),
        sessionService.listSessions(),
        canReviewSignups ? memberService.listSignups("PENDING") : Promise.resolve([]),
      ]);

      setMembers(memberList || []);
      setBatches(batchList || []);
      setSessions(sessionList || []);
      setPendingSignupsCount(Array.isArray(signupsList) ? signupsList.length : 0);

      const sessionAttendance = await Promise.all(
        (sessionList || []).map((session) => attendanceService.getAttendanceForSession(session.id)),
      );

      setAttendance(
        sessionAttendance.flatMap((records, index) =>
          (records || []).map((record) => ({
            ...record,
            sessionTitle: sessionList[index]?.title || "Session",
          })),
        ),
      );
    } catch (error) {
      if (!silent) showToast(error.message || "Unable to load dashboard data.", "error");
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboard();
    const refreshTimer = window.setInterval(() => loadDashboard(true), 30000);
    return () => window.clearInterval(refreshTimer);
  }, [showToast, user?.role]);

  const memberMap = useMemo(
    () => Object.fromEntries(members.map((member) => [String(member.id), member.name])),
    [members],
  );

  const activeSessions = useMemo(() => {
    return sessions.filter((session) => {
      const start = new Date(session.startTime).getTime();
      const end = new Date(session.endTime).getTime();
      return now >= start && now <= end;
    });
  }, [sessions, now]);

  const liveSession = activeSessions[0];

  const liveSessionAttendanceCount = useMemo(() => {
    if (!liveSession) return 0;
    return attendance.filter((a) => String(a.sessionId) === String(liveSession.id)).length;
  }, [attendance, liveSession]);

  const todayCheckIns = useMemo(() => {
    const todayStr = new Date(now).toDateString();
    return attendance.filter((record) => {
      return new Date(record.markedAt).toDateString() === todayStr;
    }).length;
  }, [attendance, now]);

  const recentAttendance = useMemo(
    () =>
      [...attendance]
        .sort((a, b) => new Date(b.markedAt) - new Date(a.markedAt))
        .slice(0, 6),
    [attendance],
  );

  const recentSessions = useMemo(
    () =>
      [...sessions]
        .sort((a, b) => new Date(b.startTime) - new Date(a.startTime))
        .slice(0, 5),
    [sessions],
  );

  const sessionStatus = (session) => {
    const start = new Date(session.startTime).getTime();
    const end = new Date(session.endTime).getTime();
    if (now < start) return "Upcoming";
    if (now <= end) return "Active";
    return "Completed";
  };

  const getSessionProgressPercent = (session) => {
    const start = new Date(session.startTime).getTime();
    const end = new Date(session.endTime).getTime();
    if (now < start) return 0;
    if (now >= end) return 100;
    const total = end - start;
    const elapsed = now - start;
    return Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));
  };

  if (loading) {
    return <Loading message="Loading workspace operations..." />;
  }

  const todayDate = new Date(now);

  return (
    <div className="page-shell dashboard-page">
      {/* Intro Header */}
      <div className="page-header dashboard-intro">
        <div>
          <p className="eyebrow">
            {user?.role === "ADMIN" ? "Administrator Overview" : "Club Staff Workspace"}
          </p>
          <h1>
            Good{" "}
            {todayDate.getHours() < 12
              ? "morning"
              : todayDate.getHours() < 18
                ? "afternoon"
                : "evening"}
            , {user?.name?.split(" ")[0] || "there"}!
          </h1>
          <p className="dashboard-date">Here is the real-time operational status for today.</p>
        </div>

        <div className="header-quick-links">
          {pendingSignupsCount > 0 && (
            <Link to="/members" className="pending-signups-alert-pill">
              <ShieldAlert size={16} />
              <span>{pendingSignupsCount} Registration{pendingSignupsCount > 1 ? "s" : ""} Pending Review</span>
            </Link>
          )}
          <Link className="quiet-link" to="/attendance">
            Attendance Log <ArrowUpRight size={16} />
          </Link>
        </div>
      </div>

      {/* KPI Stats Grid */}
      <div className="stats-grid dashboard-stats">
        <StatCard
          icon={<UsersRound size={22} />}
          label="Registered Members"
          value={members.length}
          subtext="Active club roster"
          tone="green"
        />
        <StatCard
          icon={<Layers size={22} />}
          label="Cohorts & Batches"
          value={batches.filter((b) => b.status === "ACTIVE").length}
          subtext={`${batches.length} total batches`}
          tone="coral"
        />
        <StatCard
          icon={<Activity size={22} />}
          label="Live Sessions"
          value={activeSessions.length}
          subtext={activeSessions.length > 0 ? "Currently tracking" : "No live geofence"}
          tone="lime"
        />
        <StatCard
          icon={<Clock3 size={22} />}
          label="Check-ins Today"
          value={todayCheckIns}
          subtext="Verified physical presence"
          tone="blue"
        />
      </div>

      {/* Hero Live Session Operations Card */}
      <section className={`live-operations ${liveSession ? "live-operations-active" : ""}`}>
        <div className="live-operations-copy">
          <div className="live-status">
            <span className="live-status-dot" />
            {liveSession ? "SESSION IN PROGRESS" : "NO ACTIVE SESSION"}
          </div>
          <h2>{liveSession?.title || "Venue Floor Is Clear"}</h2>
          <p>
            {liveSession
              ? `Real-time geofence tracking is running. ${liveSessionAttendanceCount} members currently marked present.`
              : "There are no attendance sessions actively in progress right now."}
          </p>

          {liveSession && (
            <div className="live-session-progress-wrap">
              <div className="progress-labels">
                <span>Session Progress ({getSessionProgressPercent(liveSession)}%)</span>
                <span>
                  Ends at{" "}
                  {new Date(liveSession.endTime).toLocaleTimeString([], {
                    hour: "numeric",
                    minute: "2-digit",
                  })}
                </span>
              </div>
              <div className="session-progress-bar">
                <div
                  className="session-progress-fill"
                  style={{ width: `${getSessionProgressPercent(liveSession)}%` }}
                />
              </div>
            </div>
          )}
        </div>

        <div className="live-session-facts">
          {liveSession ? (
            <>
              <span className="fact-item">
                <MapPin size={17} />
                {liveSession.locationEnabled
                  ? `Geofence Perimeter · ${liveSession.radiusMeters} m`
                  : "Manual Check-in"}
              </span>
              <span className="fact-item">
                <UserCheck size={17} />
                {liveSessionAttendanceCount} Present
              </span>
            </>
          ) : (
            <span className="fact-item">
              <MapPin size={17} /> Geofence System Armed
            </span>
          )}
        </div>

        <div className="live-operations-actions">
          {liveSession ? (
            <Link className="primary-button" to="/attendance">
              <span>View Live Attendance</span>
              <ArrowRight size={17} />
            </Link>
          ) : (
            <Link className="secondary-button" to="/sessions">
              <span>{user?.role === "ADMIN" ? "Schedule a Session" : "View Sessions"}</span>
              <ArrowUpRight size={17} />
            </Link>
          )}
        </div>
      </section>

      {/* Two Column Layout: Recent Attendance and Recent Sessions */}
      <div className="content-grid two-col dashboard-lower-grid">
        {/* Left Column: Recent Check-ins */}
        <section className="panel dashboard-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Recent Activity</p>
              <h3>Live Check-ins</h3>
            </div>
            <Link className="quiet-link" to="/attendance">
              View all <ArrowUpRight size={15} />
            </Link>
          </div>

          {recentAttendance.length ? (
            <div className="table-card">
              <table>
                <thead>
                  <tr>
                    <th>Member</th>
                    <th>Session</th>
                    <th>Method</th>
                    <th>Time</th>
                  </tr>
                </thead>
                <tbody>
                  {recentAttendance.map((record) => (
                    <tr key={record.id}>
                      <td>
                        <strong>{memberMap[String(record.memberId)] || `Member ${record.memberId}`}</strong>
                      </td>
                      <td>{record.sessionTitle}</td>
                      <td>
                        <span className={`method-badge ${String(record.attendanceMethod).toLowerCase()}`}>
                          {record.attendanceMethod}
                        </span>
                      </td>
                      <td>
                        <small>{formatDateTime(record.markedAt)}</small>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState
              icon={<CheckCircle2 size={32} />}
              title="No check-ins yet"
              description="Member attendance records will appear here as they are confirmed."
            />
          )}
        </section>

        {/* Right Column: Sessions Schedule */}
        <section className="panel dashboard-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Schedule</p>
              <h3>Recent Sessions</h3>
            </div>
            <Link className="quiet-link" to="/sessions">
              View all <ArrowUpRight size={15} />
            </Link>
          </div>

          {recentSessions.length ? (
            <div className="stack-list">
              {recentSessions.map((session) => (
                <article key={session.id} className="session-card">
                  <div className="session-card-title">
                    <h4>{session.title}</h4>
                    <p>
                      <CalendarDays size={14} />
                      {new Date(session.startTime).toLocaleDateString([], {
                        month: "short",
                        day: "numeric",
                        year: "numeric",
                      })}
                    </p>
                  </div>
                  <div className="session-card-meta">
                    <span className={`status-badge status-${sessionStatus(session).toLowerCase()}`}>
                      {sessionStatus(session)}
                    </span>
                    <span className="radius-pill">
                      {session.locationEnabled
                        ? `Radius: ${session.radiusMeters} m`
                        : "Manual Only"}
                    </span>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <EmptyState
              icon={<CalendarDays size={32} />}
              title="No sessions scheduled"
              description="Create a session to begin tracking member attendance."
              actionLabel={user?.role === "ADMIN" ? "Create Session" : undefined}
              onAction={() => (window.location.href = "/sessions")}
            />
          )}
        </section>
      </div>
    </div>
  );
}
