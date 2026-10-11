export default function Toast({ toast }) {
  if (!toast) {
    return null;
  }

  return (
    <div className={`toast toast-${toast.type || "success"}`} role="status" aria-live="polite">
      <span className="toast-icon">{toast.type === "error" ? "✕" : toast.type === "info" ? "ℹ" : "✓"}</span>
      <span>{toast.message}</span>
    </div>
  );
}
