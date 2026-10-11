export default function StatCard({ icon, label, value, tone = "default" }) {
  return (
    <div className={`stat-card ${tone}`}>
      <div className="stat-icon">{icon}</div>
      <div className="stat-body">
        <p>{label}</p>
        <h3>{value}</h3>
      </div>
    </div>
  );
}
