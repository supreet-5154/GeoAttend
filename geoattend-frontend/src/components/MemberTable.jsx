export default function MemberTable({ members, onEdit, onDelete, canManage }) {
  if (!members.length) {
    return <div className="table-card empty-inline">No members found.</div>;
  }

  return (
    <div className="table-card">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Email</th>
            <th>Role</th>
            <th>Student ID</th>
            <th>Batch / Generation</th>
            {canManage && <th>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {members.map((member) => (
            <tr key={member.id}>
              <td>{member.id}</td>
              <td className="font-semibold">{member.name}</td>
              <td>{member.email}</td>
              <td>
                <span className={`status-pill ${member.role?.toLowerCase()}`}>
                  {member.role || "MEMBER"}
                </span>
              </td>
              <td>{member.studentId || "—"}</td>
              <td>
                <span className={`batch-pill ${member.batchName ? "assigned" : "unassigned"}`}>
                  {member.batchName || "Unassigned"}
                </span>
              </td>
              {canManage && (
                <td>
                  <div className="table-actions">
                    <button type="button" className="action-button secondary" onClick={() => onEdit(member)}>
                      Edit
                    </button>
                    <button type="button" className="action-button danger" onClick={() => onDelete(member)}>
                      Delete
                    </button>
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
