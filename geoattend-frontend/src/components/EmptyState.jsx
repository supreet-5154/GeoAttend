export default function EmptyState({
  icon,
  title,
  description,
  action,
  actionLabel,
  onAction,
}) {
  return (
    <div className="empty-state" role="status">
      {icon && <div className="empty-state-icon" aria-hidden="true">{icon}</div>}
      <h4>{title}</h4>
      {description && <p>{description}</p>}
      {action ? (
        <div className="empty-state-action">{action}</div>
      ) : actionLabel && onAction ? (
        <button type="button" className="secondary-button empty-action-btn" onClick={onAction}>
          {actionLabel}
        </button>
      ) : null}
    </div>
  );
}
