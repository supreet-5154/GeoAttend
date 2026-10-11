import { useEffect, useState } from "react";
import Loading from "../components/Loading";
import attendanceService from "../services/attendanceService";

export default function MemberAttendance({ showToast }) {
  const [attendance, setAttendance] = useState([]);
  const [tracking, setTracking] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchAttendance = async () => {
    try {
      setLoading(true);
      const [data, trackingData] = await Promise.all([
        attendanceService.getMyAttendance(),
        attendanceService.getMyTracking(),
      ]);
      setAttendance(data || []);
      setTracking(trackingData || []);
    } catch (error) {
      showToast(error.message || "Unable to load your attendance history.", "error");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAttendance();
  }, [showToast]);

  if (loading) {
    return <Loading message="Loading your attendance history..." />;
  }

  return (
    <div className="member-history">
      <div className="page-header compact">
        <div>
          <p className="eyebrow">My attendance</p>
          <h1>Attendance History</h1>
        </div>
      </div>

      {tracking.length > 0 && (
        <section className="member-tracking-history">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Automatic attendance</p>
              <h3>Session tracking results</h3>
            </div>
          </div>
          <div className="member-list">
            {tracking.map((item) => (
              <div className="member-item" key={item.id}>
                <div>
                  <strong>{item.sessionTitle}</strong>
                  <small>{item.insideChecks} of {item.expectedChecks} expected checks inside the geofence</small>
                </div>
                <span className={`tracking-result-badge ${String(item.status).toLowerCase()}`}>
                  {item.status === "PRESENT" ? "Present" : item.status === "REVIEW" ? "Needs staff review" : item.status === "MANUAL_OVERRIDE" ? "Manual override" : item.status === "WAITING_FOR_GEOFENCE" ? "Waiting for location" : "Tracking"}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {attendance.length ? (
        <div className="table-card">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Method</th>
                <th>Location</th>
              </tr>
            </thead>
            <tbody>
              {attendance.map((item) => (
                <tr key={item.id}>
                  <td>{new Date(item.markedAt).toLocaleString()}</td>
                  <td>{item.attendanceMethod || "GEOFENCE"}</td>
                  <td>{item.latitude && item.longitude ? `${item.latitude}, ${item.longitude}` : "N/A"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">
          <h4>No attendance history yet</h4>
          <p>Your marked sessions will appear here.</p>
        </div>
      )}
    </div>
  );
}
