import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  ArrowLeft,
  Calendar,
  CheckCircle2,
  Clock,
  Compass,
  FileSpreadsheet,
  Filter,
  History,
  MapPin,
  Search,
  ShieldCheck,
} from "lucide-react";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import ActiveFilterBadges from "../components/ActiveFilterBadges";
import Pagination from "../components/Pagination";
import StatCard from "../components/StatCard";
import { useDebounce } from "../hooks/useDebounce";
import attendanceService from "../services/attendanceService";

export default function MemberAttendance({ showToast }) {
  const [attendance, setAttendance] = useState([]);
  const [tracking, setTracking] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const debouncedSearchTerm = useDebounce(searchTerm, 350);
  const [methodFilter, setMethodFilter] = useState("all"); // "all" | "GEOFENCE" | "MANUAL"
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const fetchAttendance = useCallback(async () => {
    try {
      setLoading(true);
      const [data, trackingData] = await Promise.all([
        attendanceService.getMyAttendance(),
        attendanceService.getMyTracking(),
      ]);
      setAttendance(data || []);
      setTracking(trackingData || []);
    } catch (error) {
      showToast(error.message || "Unable to load your attendance history.", "error");
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    fetchAttendance();
  }, [fetchAttendance]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearchTerm, methodFilter, fromDate, toDate]);

  // Statistics calculation
  const stats = useMemo(() => {
    const total = attendance.length;
    const geofence = attendance.filter((a) => a.attendanceMethod === "GEOFENCE").length;
    const manual = attendance.filter((a) => a.attendanceMethod === "MANUAL").length;
    const geofenceRate = total > 0 ? Math.round((geofence / total) * 100) : 0;
    return { total, geofence, manual, geofenceRate };
  }, [attendance]);

  // Filtered attendance list
  const filteredRecords = useMemo(() => {
    return attendance.filter((item) => {
      const titleMatch = (item.sessionTitle || "Attendance")
        .toLowerCase()
        .includes(debouncedSearchTerm.toLowerCase());
      const methodMatch =
        methodFilter === "all" || item.attendanceMethod === methodFilter;

      let dateMatch = true;
      if (fromDate || toDate) {
        const itemDate = new Date(item.markedAt);
        if (fromDate) {
          const from = new Date(fromDate + "T00:00:00");
          if (itemDate < from) dateMatch = false;
        }
        if (toDate) {
          const to = new Date(toDate + "T23:59:59.999");
          if (itemDate > to) dateMatch = false;
        }
      }

      return titleMatch && methodMatch && dateMatch;
    });
  }, [attendance, debouncedSearchTerm, methodFilter, fromDate, toDate]);

  const pagedRecords = useMemo(() => {
    const start = page * pageSize;
    return filteredRecords.slice(start, start + pageSize);
  }, [filteredRecords, page, pageSize]);

  const totalPages = Math.ceil(filteredRecords.length / pageSize);

  const activeFilters = useMemo(() => {
    const list = [];
    if (debouncedSearchTerm.trim()) {
      list.push({
        key: "search",
        label: "Search",
        value: debouncedSearchTerm.trim(),
        onRemove: () => setSearchTerm(""),
      });
    }
    if (methodFilter !== "all") {
      list.push({
        key: "method",
        label: "Method",
        value: methodFilter,
        onRemove: () => setMethodFilter("all"),
      });
    }
    if (fromDate) {
      list.push({
        key: "fromDate",
        label: "From",
        value: fromDate,
        onRemove: () => setFromDate(""),
      });
    }
    if (toDate) {
      list.push({
        key: "toDate",
        label: "To",
        value: toDate,
        onRemove: () => setToDate(""),
      });
    }
    return list;
  }, [debouncedSearchTerm, methodFilter, fromDate, toDate]);

  const clearAllFilters = useCallback(() => {
    setSearchTerm("");
    setMethodFilter("all");
    setFromDate("");
    setToDate("");
    setPage(0);
  }, []);

  if (loading) {
    return <Loading message="Loading attendance records..." />;
  }

  return (
    <div className="member-history-page">
      {/* Top Header & Back Button */}
      <div className="page-header compact">
        <div>
          <Link to="/member" className="quiet-link back-link">
            <ArrowLeft size={16} /> Back to Live Portal
          </Link>
          <p className="eyebrow">Personal Attendance Record</p>
          <h1>My Attendance History</h1>
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div className="stats-grid member-stats-row">
        <StatCard
          icon={<CheckCircle2 size={22} />}
          label="Verified Sessions"
          value={stats.total}
          subtext="Total marked sessions"
          tone="green"
        />
        <StatCard
          icon={<Compass size={22} />}
          label="Geofence Rate"
          value={`${stats.geofenceRate}%`}
          subtext={`${stats.geofence} verified via GPS`}
          tone="lime"
        />
        <StatCard
          icon={<FileSpreadsheet size={22} />}
          label="Manual Check-ins"
          value={stats.manual}
          subtext="Staff approved"
          tone="blue"
        />
      </div>

      {/* Search, Method & Date Filters */}
      <div className="filter-bar member-history-filter">
        <div className="search-wrap">
          <Search size={16} />
          <input
            type="text"
            placeholder="Search by session title..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            aria-label="Search attendance records"
          />
        </div>

        <div className="filter-controls">
          <select
            id="method-filter"
            value={methodFilter}
            onChange={(e) => setMethodFilter(e.target.value)}
            className="filter-select"
            aria-label="Filter by check-in method"
          >
            <option value="all">All Methods</option>
            <option value="GEOFENCE">Geofence (GPS)</option>
            <option value="MANUAL">Manual Override</option>
          </select>

          <div className="date-filter-inline">
            <label className="date-label">Date</label>
            <input
              type="date"
              className="filter-date-input"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              aria-label="Filter marked from date"
              title="Attendance on or after"
            />
            <span className="date-sep">to</span>
            <input
              type="date"
              className="filter-date-input"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              aria-label="Filter marked to date"
              title="Attendance on or before"
            />
          </div>
        </div>
      </div>

      <ActiveFilterBadges
        activeFilters={activeFilters}
        onClearAll={clearAllFilters}
        totalCount={filteredRecords.length}
      />

      {/* Tracking verification breakdown if available */}
      {tracking.length > 0 && (
        <section className="member-tracking-summary-section">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Session Monitoring</p>
              <h3>Periodic Geofence Checks</h3>
              <p className="section-subtitle">Real-time interval verification across recent sessions.</p>
            </div>
          </div>

          <div className="tracking-summary-cards">
            {tracking.slice(0, 4).map((item) => {
              const expected = item.expectedChecks || 10;
              const inside = item.insideChecks || 0;
              const ratio = Math.min(100, Math.round((inside / expected) * 100));
              const statusType =
                item.status === "PRESENT"
                  ? "present"
                  : item.status === "REVIEW"
                    ? "review"
                    : item.status === "MANUAL_OVERRIDE"
                      ? "override"
                      : "tracking";
              const statusLabel =
                item.status === "PRESENT"
                  ? "Present"
                  : item.status === "REVIEW"
                    ? "Needs Review"
                    : item.status === "MANUAL_OVERRIDE"
                      ? "Manual Override"
                      : item.status === "WAITING_FOR_GEOFENCE"
                        ? "Waiting"
                        : "Tracking";

              return (
                <div key={item.id} className={`tracking-summary-card tracking-card-${statusType}`}>
                  <div className="tracking-summary-header">
                    <strong className="tracking-card-title">{item.sessionTitle || "Club Session"}</strong>
                    <span className={`tracking-status-pill status-${statusType}`}>{statusLabel}</span>
                  </div>

                  <div className="tracking-metric-row">
                    <div className="tracking-ratio-display">
                      <span className="ratio-number">{ratio}%</span>
                      <span className="ratio-label">Presence</span>
                    </div>
                    <div className="tracking-checks-display">
                      <ShieldCheck size={15} />
                      <span>
                        <strong>{inside}</strong> of {expected} intervals
                      </span>
                    </div>
                  </div>

                  <div className="tracking-progress-track" aria-hidden="true">
                    <div
                      className={`tracking-progress-fill progress-${statusType}`}
                      style={{ width: `${ratio}%` }}
                    />
                  </div>

                  <div className="tracking-summary-footer">
                    <span className="tracking-threshold-hint">
                      {ratio >= 80 ? "✓ 80% threshold achieved" : "80% presence required for auto-verification"}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* Attendance Records: Responsive Table on Desktop + Cards on Mobile */}
      {filteredRecords.length > 0 ? (
        <div className="records-container">
          {/* Desktop Table View */}
          <div className="table-card desktop-only">
            <table>
              <thead>
                <tr>
                  <th>Session</th>
                  <th>Date & Time</th>
                  <th>Method</th>
                  <th>Coordinates</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {pagedRecords.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <strong>{item.sessionTitle || "Club Session"}</strong>
                    </td>
                    <td>
                      <div className="datetime-cell">
                        <span>{new Date(item.markedAt).toLocaleDateString([], { month: "short", day: "numeric", year: "numeric" })}</span>
                        <small>{new Date(item.markedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</small>
                      </div>
                    </td>
                    <td>
                      <span className={`method-badge ${String(item.attendanceMethod).toLowerCase()}`}>
                        {item.attendanceMethod === "GEOFENCE" ? <Compass size={13} /> : <MapPin size={13} />}
                        {item.attendanceMethod}
                      </span>
                    </td>
                    <td>
                      {item.latitude && item.longitude ? (
                        <span className="coord-text">
                          {Number(item.latitude).toFixed(4)}, {Number(item.longitude).toFixed(4)}
                        </span>
                      ) : (
                        <span className="coord-muted">N/A</span>
                      )}
                    </td>
                    <td>
                      <span className="badge badge-success">
                        <CheckCircle2 size={13} /> Present
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Card List View */}
          <div className="mobile-cards-list mobile-only">
            {pagedRecords.map((item) => (
              <div key={item.id} className="mobile-record-card">
                <div className="mobile-card-top">
                  <strong>{item.sessionTitle || "Club Session"}</strong>
                  <span className="badge badge-success">
                    <CheckCircle2 size={13} /> Present
                  </span>
                </div>

                <div className="mobile-card-meta">
                  <span className="meta-row">
                    <Calendar size={14} />
                    {new Date(item.markedAt).toLocaleDateString([], {
                      weekday: "short",
                      month: "short",
                      day: "numeric",
                    })}
                  </span>
                  <span className="meta-row">
                    <Clock size={14} />
                    {new Date(item.markedAt).toLocaleTimeString([], {
                      hour: "numeric",
                      minute: "2-digit",
                    })}
                  </span>
                </div>

                <div className="mobile-card-footer">
                  <span className={`method-badge ${String(item.attendanceMethod).toLowerCase()}`}>
                    {item.attendanceMethod === "GEOFENCE" ? <Compass size={13} /> : <MapPin size={13} />}
                    {item.attendanceMethod}
                  </span>
                  {item.latitude && item.longitude && (
                    <small className="coord-text">
                      {Number(item.latitude).toFixed(3)}, {Number(item.longitude).toFixed(3)}
                    </small>
                  )}
                </div>
              </div>
            ))}
          </div>

          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={filteredRecords.length}
            size={pageSize}
            onPageChange={setPage}
            onSizeChange={setPageSize}
          />
        </div>
      ) : (
        <EmptyState
          icon={<History size={36} />}
          title="No attendance records found"
          description={
            activeFilters.length > 0
              ? "Try clearing your search query or filters."
              : "You have not marked attendance for any sessions yet."
          }
          actionLabel={activeFilters.length > 0 ? "Reset filters" : undefined}
          onAction={clearAllFilters}
        />
      )}
    </div>
  );
}
