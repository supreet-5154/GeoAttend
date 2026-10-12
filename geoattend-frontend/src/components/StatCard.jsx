export default function StatCard({
  icon,
  label,
  value,
  subtext,
  tone = "default",
  onClick,
}) {
  return (
    <div
      className={`stat-card ${tone} ${onClick ? "stat-card-clickable" : ""}`}
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={onClick ? (e) => (e.key === "Enter" || e.key === " ") && onClick() : undefined}
    >
      <div className="stat-icon-wrap" aria-hidden="true">{icon}</div>
      <div className="stat-meta">
        <span className="stat-label">{label}</span>
        <strong className="stat-value">{value}</strong>
        {subtext && <span className="stat-subtext">{subtext}</span>}
      </div>
    </div>
  );
}
