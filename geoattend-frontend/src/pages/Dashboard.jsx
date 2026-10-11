import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Activity, ArrowUpRight, CalendarDays, Clock3, MapPin, UsersRound } from "lucide-react";
import Loading from "../components/Loading";
import EmptyState from "../components/EmptyState";
import StatCard from "../components/StatCard";
import memberService from "../services/memberService";
import sessionService from "../services/sessionService";
import attendanceService from "../services/attendanceService";
import { formatDateTime, getLocationText } from "../services/api";

export default function Dashboard({ user, showToast }) {
  const [now] = useState(() => Date.now());
  const [today] = useState(() => new Date(now));
  const [members, setMembers] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [attendance, setAttendance] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadDashboard = async () => {
      try {
        setLoading(true);
        const [memberList, sessionList] = await Promise.all([
          memberService.listMembers(),
          sessionService.listSessions(),
        ]);

        setMembers(memberList);
        setSessions(sessionList);

        const sessionAttendance = await Promise.all(
          sessionList.map((session) => attendanceService.getAttendanceForSession(session.id)),
        );
        setAttendance(
          sessionAttendance.flatMap((records, index) =>
            records.map((record) => ({ ...record, sessionTitle: sessionList[index].title })),
          ),
        );
      } catch (error) {
        showToast(error.message || "Unable to load dashboard data.", "error");
      } finally {
        setLoading(false);
      }
    };

    loadDashboard();
  }, [showToast]);

  const recentAttendance = useMemo(
    () =>
      [...attendance]
        .sort((a, b) => new Date(b.markedAt) - new Date(a.markedAt))
        .slice(0, 5),
    [attendance],
  );

  const recentSessions = useMemo(
    () =>
      [...sessions]
        .sort((a, b) => new Date(b.startTime) - new Date(a.startTime))
        .slice(0, 5),
    [sessions],
  );

  const memberMap = useMemo(
    () => Object.fromEntries(members.map((member) => [String(member.id), member.name])),
    [members],
  );

  const activeSessions = sessions.filter((session) => {
    return now >= new Date(session.startTime).getTime() && now <= new Date(session.endTime).getTime();
  });
  const todayCheckIns = attendance.filter((record) => {
    const markedAt = new Date(record.markedAt);
    return markedAt.toDateString() === today.toDateString();
  }).length;
  const liveSession = activeSessions[0];

  const sessionStatus = (session) => {
    if (now < new Date(session.startTime).getTime()) return "Upcoming";
    if (now <= new Date(session.endTime).getTime()) return "Active";
    return "Completed";
  };

  if (loading) {
    return <Loading message="Loading dashboard data..." />;
  }

  return (
    <div className="page-shell dashboard-page">
      <div className="page-header dashboard-intro">
        <div>
          <p className="eyebrow">Operations overview</p>
          <h1>Good {today.getHours() < 12 ? "morning" : today.getHours() < 18 ? "afternoon" : "evening"}, {user?.name?.split(" ")[0] || "there"}.</h1>
          <p className="dashboard-date">Here is the attendance picture for today.</p>
        </div>
        <Link className="quiet-link" to="/attendance">Open attendance log <ArrowUpRight size={16} /></Link>
      </div>

      <div className="stats-grid">
        <StatCard icon={<UsersRound size={19} />} label="Members" value={members.length} tone="green" />
        <StatCard icon={<CalendarDays size={19} />} label="Sessions" value={sessions.length} tone="coral" />
        <StatCard icon={<Activity size={19} />} label="Live now" value={activeSessions.length} tone="lime" />
        <StatCard icon={<Clock3 size={19} />} label="Check-ins today" value={todayCheckIns} tone="blue" />
      </div>

      <section className={`live-operations ${liveSession ? "live-operations-active" : ""}`}>
        <div className="live-operations-copy">
          <div className="live-status"><span className="live-status-dot" />{liveSession ? "SESSION IN PROGRESS" : "NO LIVE SESSION"}</div>
          <h2>{liveSession?.title || "The floor is clear."}</h2>
          <p>{liveSession ? "Attendance is being recorded for this session." : "There are no attendance sessions running right now."}</p>
        </div>
        {liveSession ? (
          <div className="live-session-facts">
            <span><MapPin size={17} />{liveSession.locationEnabled ? `Geofence · ${liveSession.radiusMeters} m` : "Manual check-in"}</span>
            <span><Clock3 size={17} />Ends {new Date(liveSession.endTime).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</span>
          </div>
        ) : (
          <div className="live-session-facts"><span><MapPin size={17} />Geofence ready</span></div>
        )}
        <Link className="live-operations-action" to="/sessions">{liveSession ? "View sessions" : "Plan a session"}<ArrowUpRight size={17} /></Link>
      </section>

      <div className="content-grid two-col dashboard-lower-grid">
        <section className="panel dashboard-panel">
          <div className="section-heading">
            <div><p className="eyebrow">Latest activity</p><h3>Recent check-ins</h3></div>
            <Link className="quiet-link" to="/attendance" aria-label="View all attendance">View all <ArrowUpRight size={15} /></Link>
          </div>

          {recentAttendance.length ? (
            <div className="table-card">
              <table>
                <thead>
                  <tr>
                    <th>Member</th>
                    <th>Method</th>
                    <th>Marked At</th>
                    <th>Location</th>
                  </tr>
                </thead>
                <tbody>
                  {recentAttendance.map((record) => (
                    <tr key={record.id}>
                      <td>{memberMap[String(record.memberId)] || `Member ${record.memberId}`}</td>
                      <td>
                        <span className={`method-badge ${String(record.attendanceMethod).toLowerCase()}`}>
                          {record.attendanceMethod}
                        </span>
                      </td>
                      <td>{formatDateTime(record.markedAt)}</td>
                      <td>{getLocationText(record.latitude, record.longitude)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="No attendance records" description="Attendance will appear here as it is recorded." />
          )}
        </section>

        <section className="panel dashboard-panel">
          <div className="section-heading">
            <div><p className="eyebrow">Session schedule</p><h3>Recent sessions</h3></div>
            <Link className="quiet-link" to="/sessions" aria-label="View all sessions">View all <ArrowUpRight size={15} /></Link>
          </div>

          {recentSessions.length ? (
            <div className="stack-list">
              {recentSessions.map((session) => (
                <article key={session.id} className="session-card">
                  <div className="session-card-title">
                    <h4>{session.title}</h4>
                    <p><CalendarDays size={14} />{new Date(session.startTime).toLocaleDateString([], { month: "short", day: "numeric", year: "numeric" })}</p>
                  </div>
                  <div className="session-card-meta">
                    <span className={`status-badge ${sessionStatus(session).toLowerCase()}`}>{sessionStatus(session)}</span>
                    <span>{session.locationEnabled ? `Geofence · ${session.radiusMeters} m` : "Manual only"}</span>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <EmptyState title="No sessions yet" description="Create your first attendance session to get started." />
          )}
        </section>
      </div>
    </div>
  );
}
