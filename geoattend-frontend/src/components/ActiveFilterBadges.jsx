import { RotateCcw, X } from "lucide-react";

export default function ActiveFilterBadges({
  activeFilters = [],
  onClearAll,
  totalCount,
}) {
  if (activeFilters.length === 0) {
    return null;
  }

  return (
    <div className="active-filters-bar" role="region" aria-label="Active filters">
      <div className="active-filters-list">
        <span className="active-filters-label">Active filters:</span>
        {activeFilters.map((filter) => (
          <span key={filter.key} className="active-filter-badge">
            <span className="active-filter-name">{filter.label}:</span>
            <span className="active-filter-value">{filter.value}</span>
            <button
              type="button"
              className="active-filter-remove"
              onClick={filter.onRemove}
              aria-label={`Remove filter ${filter.label}: ${filter.value}`}
            >
              <X size={12} />
            </button>
          </span>
        ))}
      </div>

      <div className="active-filters-actions">
        {totalCount !== undefined && (
          <span className="active-filters-count">
            {totalCount} {totalCount === 1 ? "result" : "results"} found
          </span>
        )}
        <button
          type="button"
          className="clear-all-filters-btn"
          onClick={onClearAll}
          aria-label="Clear all filters"
        >
          <RotateCcw size={13} />
          <span>Clear all</span>
        </button>
      </div>
    </div>
  );
}
