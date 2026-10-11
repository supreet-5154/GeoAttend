export default function Loading({ message = "Loading..." }) {
  return (
    <div className="loading-state">
      <div className="spinner" aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}
