import { formatDateTime, getLocationText } from "../services/api";
import { Pencil, Trash2 } from "lucide-react";

export default function AttendanceTable({ records, members, sessions, canManage, onEdit, onDelete }) {
  if (!records.length) {
    return <div className="table-card empty-inline">No attendance records found.</div>;
  }

  const memberMap = Object.fromEntries(members.map((member) => [String(member.id), member.name]));
  const sessionMap = Object.fromEntries(sessions.map((session) => [String(session.id), session.title]));

  return (
    <div className="table-card">
      <table>
        <thead>
          <tr>
            <th>Member</th>
            <th>Session</th>
            <th>Marked At</th>
            <th>Method</th>
            <th>Location</th>
            {canManage && <th>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {records.map((record) => (
            <tr key={record.id}>
              <td>{memberMap[String(record.memberId)] || `Member ${record.memberId}`}</td>
              <td>{sessionMap[String(record.sessionId)] || `Session ${record.sessionId}`}</td>
              <td>{formatDateTime(record.markedAt)}</td>
              <td>
                <span className={`method-badge ${String(record.attendanceMethod).toLowerCase()}`}>
                  {record.attendanceMethod}
                </span>
              </td>
              <td>{getLocationText(record.latitude, record.longitude)}</td>
              {canManage && (
                <td>
                  <div className="table-actions">
                    <button type="button" className="icon-action" onClick={() => onEdit(record)} aria-label="Edit attendance" title="Edit attendance"><Pencil size={15} /></button>
                    <button type="button" className="icon-action danger" onClick={() => onDelete(record)} aria-label="Delete attendance" title="Delete attendance"><Trash2 size={15} /></button>
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
