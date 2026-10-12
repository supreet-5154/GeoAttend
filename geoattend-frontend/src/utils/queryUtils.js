export function buildQueryString(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (
      value !== undefined &&
      value !== null &&
      value !== "" &&
      value !== "all" &&
      value !== "ALL"
    ) {
      query.set(key, String(value).trim());
    }
  });
  const qs = query.toString();
  return qs ? `?${qs}` : "";
}

export function formatDateForInput(date) {
  if (!date) return "";
  const d = new Date(date);
  if (isNaN(d.getTime())) return "";
  return d.toISOString().split("T")[0];
}
