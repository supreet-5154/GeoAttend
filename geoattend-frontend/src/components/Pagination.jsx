import { ChevronLeft, ChevronRight } from "lucide-react";

export default function Pagination({
  page = 0,
  totalPages = 0,
  totalElements = 0,
  size = 10,
  onPageChange,
  onSizeChange,
  sizeOptions = [10, 20, 50],
}) {
  if (totalElements <= 0) {
    return null;
  }

  const safeSize = Number.isFinite(size) && size > 0 ? size : 10;
  const calculatedPages = Math.ceil(totalElements / safeSize);
  const effectiveTotalPages = Math.max(1, totalPages > 0 ? totalPages : calculatedPages);
  const currentPage = Math.min(Math.max(0, page || 0), effectiveTotalPages - 1);
  const startRecord = Math.min(currentPage * safeSize + 1, totalElements);
  const endRecord = Math.min((currentPage + 1) * safeSize, totalElements);

  // Generate page numbers with ellipsis
  const getPageNumbers = () => {
    const pages = [];
    const maxVisible = 5;

    if (effectiveTotalPages <= maxVisible + 2) {
      for (let i = 0; i < effectiveTotalPages; i++) {
        pages.push(i);
      }
    } else {
      pages.push(0);
      let start = Math.max(1, currentPage - 1);
      let end = Math.min(effectiveTotalPages - 2, currentPage + 1);

      if (currentPage <= 2) {
        start = 1;
        end = 3;
      } else if (currentPage >= effectiveTotalPages - 3) {
        start = effectiveTotalPages - 4;
        end = effectiveTotalPages - 2;
      }

      if (start > 1) {
        pages.push("ellipsis-1");
      }

      for (let i = start; i <= end; i++) {
        pages.push(i);
      }

      if (end < effectiveTotalPages - 2) {
        pages.push("ellipsis-2");
      }

      pages.push(effectiveTotalPages - 1);
    }

    return pages;
  };

  return (
    <div className="pagination-bar" role="navigation" aria-label="Pagination">
      <div className="pagination-info">
        {startRecord === endRecord ? (
          <>
            Showing <strong>{startRecord}</strong> of <strong>{totalElements}</strong>{" "}
            {totalElements === 1 ? "record" : "records"}
          </>
        ) : (
          <>
            Showing <strong>{startRecord}</strong>–<strong>{endRecord}</strong> of{" "}
            <strong>{totalElements}</strong> records
          </>
        )}
      </div>

      <div className="pagination-controls">
        {onSizeChange && (
          <div className="pagination-size-select">
            <label htmlFor="page-size-selector">Rows per page:</label>
            <select
              id="page-size-selector"
              value={safeSize}
              onChange={(e) => {
                onSizeChange(Number(e.target.value));
                if (onPageChange) onPageChange(0);
              }}
              aria-label="Records per page"
            >
              {sizeOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="pagination-buttons">
          <button
            type="button"
            className="pagination-btn pagination-arrow"
            onClick={() => onPageChange && onPageChange(currentPage - 1)}
            disabled={currentPage <= 0}
            aria-label="Previous page"
          >
            <ChevronLeft size={16} />
            <span>Prev</span>
          </button>

          {getPageNumbers().map((p, idx) => {
            if (typeof p === "string") {
              return (
                <span key={`ellipsis-${idx}`} className="pagination-ellipsis" aria-hidden="true">
                  …
                </span>
              );
            }

            const isCurrent = p === currentPage;
            return (
              <button
                key={p}
                type="button"
                className={`pagination-btn pagination-page-num ${isCurrent ? "active" : ""}`}
                onClick={() => onPageChange && onPageChange(p)}
                aria-current={isCurrent ? "page" : undefined}
                aria-label={`Page ${p + 1}`}
              >
                {p + 1}
              </button>
            );
          })}

          <button
            type="button"
            className="pagination-btn pagination-arrow"
            onClick={() => onPageChange && onPageChange(currentPage + 1)}
            disabled={currentPage >= effectiveTotalPages - 1}
            aria-label="Next page"
          >
            <span>Next</span>
            <ChevronRight size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
