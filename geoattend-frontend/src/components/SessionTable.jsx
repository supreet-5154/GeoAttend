import { Pencil, Trash2 } from "lucide-react";

const getSessionStatus = (session) => {
  const now = new Date();
  const start = new Date(session.startTime);
  const end = new Date(session.endTime);

  if (now < start) {
    return "Upcoming";
  }

  if (now >= start && now <= end) {
    return "Active";
  }

  return "Completed";
};

export default function SessionTable({ sessions, canManage, onEdit, onDelete }) {
  if (!sessions.length) {
    return <div className="table-card empty-inline">No attendance sessions yet.</div>;
  }

  return (
    <div className="table-card">
      <table>
        <thead>
          <tr>
            <th>Session</th>
            <th>Start</th>
            <th>End</th>
            <th>Location</th>
            <th>Radius</th>
            <th>Mode</th>
            <th>Status</th>
            {canManage && <th>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {sessions.map((session) => (
            <tr key={session.id}>
              <td>{session.title}</td>
              <td>{new Date(session.startTime).toLocaleString()}</td>
              <td>{new Date(session.endTime).toLocaleString()}</td>
              <td>
                {session.locationEnabled ? `${session.latitude || 0}, ${session.longitude || 0}` : "Manual only"}
              </td>
              <td>{session.locationEnabled ? `${session.radiusMeters || 0}m` : "—"}</td>
              <td>
                <span className={`method-badge ${session.locationEnabled ? "geofence" : "manual"}`}>
                  {session.locationEnabled ? "GEOFENCE" : "MANUAL"}
                </span>
              </td>
              <td>
                <span className={`status-badge ${getSessionStatus(session).toLowerCase()}`}>
                  {getSessionStatus(session)}
                </span>
              </td>
              {canManage && (
                <td>
                  <div className="table-actions">
                    <button type="button" className="icon-action" onClick={() => onEdit(session)} aria-label={`Edit ${session.title}`} title="Edit session"><Pencil size={15} /></button>
                    <button type="button" className="icon-action danger" onClick={() => onDelete(session)} aria-label={`Delete ${session.title}`} title="Delete session"><Trash2 size={15} /></button>
                  </div>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
