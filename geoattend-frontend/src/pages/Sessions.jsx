import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowDownWideNarrow,
  ArrowUpWideNarrow,
  Calendar,
  Clock,
  LocateFixed,
  MapPin,
  Plus,
  Search,
  X,
} from "lucide-react";
import ConfirmModal from "../components/ConfirmModal";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import SessionTable from "../components/SessionTable";
import ActiveFilterBadges from "../components/ActiveFilterBadges";
import Pagination from "../components/Pagination";
import { useDebounce } from "../hooks/useDebounce";
import sessionService from "../services/sessionService";

const initialForm = {
  title: "",
  startTime: "",
  endTime: "",
  latitude: "",
  longitude: "",
  radiusMeters: "",
  locationAccuracy: "",
};

const toDateTimeInput = (value) => {
  const date = new Date(value);
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return localDate.toISOString().slice(0, 16);
};

export default function Sessions({ user, showToast }) {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingSession, setEditingSession] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [geoMessage, setGeoMessage] = useState("");
  const [sortOrder, setSortOrder] = useState("newest");

  // Search & Filter state
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 350);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  // Pagination state
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Confirm delete modal state
  const [deleteConfirm, setDeleteConfirm] = useState({ open: false, session: null });

  const canCreateSessions = user?.role === "ADMIN" || user?.role === "CLUB_USER";
  const canManageSessions = user?.role === "ADMIN";

  const fetchSessions = useCallback(async () => {
    try {
      setLoading(true);
      const params = {
        search: debouncedSearch.trim() || undefined,
        status: statusFilter !== "ALL" ? statusFilter : undefined,
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        sortBy: "startTime",
        sortDirection: sortOrder === "newest" ? "DESC" : "ASC",
        page,
        size: pageSize,
      };

      const result = await sessionService.listSessions(params);
      if (result && Array.isArray(result.content)) {
        setSessions(result.content);
        setTotalPages(result.totalPages ?? 0);
        setTotalElements(result.totalElements ?? 0);
      } else if (Array.isArray(result)) {
        setSessions(result);
        setTotalPages(1);
        setTotalElements(result.length);
      } else {
        setSessions([]);
        setTotalPages(0);
        setTotalElements(0);
      }
    } catch (error) {
      showToast(error.message || "Unable to load sessions.", "error");
    } finally {
      setLoading(false);
    }
  }, [debouncedSearch, statusFilter, fromDate, toDate, sortOrder, page, pageSize, showToast]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, statusFilter, fromDate, toDate, sortOrder]);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  const activeFilters = useMemo(() => {
    const list = [];
    if (debouncedSearch.trim()) {
      list.push({
        key: "search",
        label: "Title",
        value: debouncedSearch.trim(),
        onRemove: () => setSearch(""),
      });
    }
    if (statusFilter !== "ALL") {
      list.push({
        key: "status",
        label: "Status",
        value: statusFilter.charAt(0) + statusFilter.slice(1).toLowerCase(),
        onRemove: () => setStatusFilter("ALL"),
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
  }, [debouncedSearch, statusFilter, fromDate, toDate]);

  const clearAllFilters = useCallback(() => {
    setSearch("");
    setStatusFilter("ALL");
    setFromDate("");
    setToDate("");
    setPage(0);
  }, []);

  const closeModal = () => {
    setShowModal(false);
    setEditingSession(null);
    setForm(initialForm);
    setGeoMessage("");
  };

  const openEditModal = (session) => {
    setEditingSession(session);
    setForm({
      title: session.title,
      startTime: toDateTimeInput(session.startTime),
      endTime: toDateTimeInput(session.endTime),
      latitude: session.latitude != null ? String(session.latitude) : "",
      longitude: session.longitude != null ? String(session.longitude) : "",
      radiusMeters: session.radiusMeters != null ? String(session.radiusMeters) : "",
      locationAccuracy: "",
    });
    setShowModal(true);
  };

  const handleConfirmDelete = async () => {
    const session = deleteConfirm.session;
    if (!session) return;

    try {
      await sessionService.deleteSession(session.id);
      showToast(`Session "${session.title}" deleted.`, "success");
      setDeleteConfirm({ open: false, session: null });
      fetchSessions();
    } catch (error) {
      showToast(error.message || "Unable to delete session. Sessions with attendance records cannot be deleted.", "error");
    }
  };

  const requestGeolocation = () => {
    if (!navigator.geolocation) {
      setGeoMessage("Geolocation is unavailable in this browser. Please enter coordinates manually.");
      return;
    }

    setGeoMessage("Acquiring venue GPS location...");

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setForm((current) => ({
          ...current,
          latitude: String(position.coords.latitude),
          longitude: String(position.coords.longitude),
          locationAccuracy: String(position.coords.accuracy),
        }));
        setGeoMessage(`Location captured with estimated accuracy ±${Math.round(position.coords.accuracy)} m.`);
      },
      () => {
        setGeoMessage("Location access was denied. Please enter coordinates manually.");
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
      },
    );
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      const payload = {
        title: form.title,
        startTime: form.startTime,
        endTime: form.endTime,
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
        radiusMeters: Number(form.radiusMeters),
      };

      if (
        form.latitude.trim() === "" ||
        form.longitude.trim() === "" ||
        !Number.isFinite(payload.latitude) ||
        payload.latitude < -90 ||
        payload.latitude > 90 ||
        !Number.isFinite(payload.longitude) ||
        payload.longitude < -180 ||
        payload.longitude > 180 ||
        !Number.isInteger(payload.radiusMeters) ||
        payload.radiusMeters <= 0
      ) {
        showToast("Enter valid GPS coordinates and a geofence radius greater than zero.", "error");
        return;
      }

      if (editingSession) {
        await sessionService.updateSession(editingSession.id, payload);
      } else {
        await sessionService.createSession(payload);
      }

      showToast(editingSession ? "Session updated successfully." : "Session created successfully.", "success");
      closeModal();
      fetchSessions();
    } catch (error) {
      showToast(error.message || "Unable to save session.", "error");
    }
  };

  return (
    <div className="page-shell sessions-page">
      <div className="page-header">
        <div>
          <p className="eyebrow">Attendance Planning</p>
          <h1>Attendance Sessions</h1>
        </div>

        <div className="page-header-actions">
          <div className="sort-control" role="group" aria-label="Sort sessions">
            <button
              type="button"
              className={sortOrder === "newest" ? "selected" : ""}
              aria-pressed={sortOrder === "newest"}
              onClick={() => setSortOrder("newest")}
            >
              <ArrowDownWideNarrow size={15} /> Newest
            </button>
            <button
              type="button"
              className={sortOrder === "oldest" ? "selected" : ""}
              aria-pressed={sortOrder === "oldest"}
              onClick={() => setSortOrder("oldest")}
            >
              <ArrowUpWideNarrow size={15} /> Oldest
            </button>
          </div>

          {canCreateSessions && (
            <button
              type="button"
              className="primary-button"
              onClick={() => {
                setEditingSession(null);
                setForm(initialForm);
                setShowModal(true);
              }}
            >
              <Plus size={17} /> Create Session
            </button>
          )}
        </div>
      </div>

      {/* Filter toolbar */}
      <div className="filter-bar">
        <div className="search-wrap">
          <Search size={16} />
          <input
            type="text"
            placeholder="Search sessions by title..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="filter-controls">
          <select
            className="filter-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            aria-label="Filter sessions by status"
          >
            <option value="ALL">All Statuses</option>
            <option value="UPCOMING">Upcoming</option>
            <option value="ACTIVE">Active Now</option>
            <option value="COMPLETED">Completed</option>
          </select>

        </div>
      </div>

      <ActiveFilterBadges
        activeFilters={activeFilters}
        onClearAll={clearAllFilters}
        totalCount={totalElements}
      />

      {loading ? (
        <Loading message="Loading scheduled sessions..." />
      ) : sessions.length ? (
        <>
          <SessionTable
            sessions={sessions}
            canManage={canManageSessions}
            onEdit={openEditModal}
            onDelete={(session) => setDeleteConfirm({ open: true, session })}
          />

          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            size={pageSize}
            onPageChange={setPage}
            onSizeChange={setPageSize}
          />
        </>
      ) : (
        <EmptyState
          icon={<Calendar size={36} />}
          title="No sessions found"
          description={
            activeFilters.length > 0
              ? "No attendance sessions match the current filter selection."
              : "Create your first session with a geofence perimeter to start tracking attendance."
          }
          actionLabel={canCreateSessions && activeFilters.length === 0 ? "Create Session" : undefined}
          onAction={canCreateSessions && activeFilters.length === 0 ? () => setShowModal(true) : undefined}
        />
      )}

      {/* Create / Edit Modal */}
      {showModal && (
        <div className="modal-backdrop" onClick={closeModal} role="presentation">
          <div
            className="modal-card large"
            role="dialog"
            aria-modal="true"
            aria-labelledby="session-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h3 id="session-modal-title">{editingSession ? "Edit Session" : "Create New Session"}</h3>
              <button
                type="button"
                className="modal-close"
                onClick={closeModal}
                aria-label="Close dialog"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="form-grid two-column">
              <label className="full-width">
                <span>Session Title</span>
                <input
                  type="text"
                  placeholder="e.g. Weekly Club Assembly, Algorithmic Workshop"
                  value={form.title}
                  onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
                  required
                />
              </label>

              <label>
                <span>Start Date & Time</span>
                <input
                  type="datetime-local"
                  value={form.startTime}
                  onChange={(event) => setForm((current) => ({ ...current, startTime: event.target.value }))}
                  required
                />
              </label>

              <label>
                <span>End Date & Time</span>
                <input
                  type="datetime-local"
                  value={form.endTime}
                  onChange={(event) => setForm((current) => ({ ...current, endTime: event.target.value }))}
                  required
                />
              </label>

              <div className="full-width geofence-required-note">
                <LocateFixed size={18} />
                <div>
                  <strong>Geofence Perimeter Verification</strong>
                  <p>
                    Every session validates member physical presence within this radius using
                    10-interval checks during the active window.
                  </p>
                </div>
              </div>

              <label>
                <span>Latitude (-90 to +90)</span>
                <input
                  type="number"
                  step="any"
                  min="-90"
                  max="90"
                  placeholder="e.g. 12.9716"
                  value={form.latitude}
                  onChange={(event) => setForm((current) => ({ ...current, latitude: event.target.value }))}
                  required
                />
              </label>

              <label>
                <span>Longitude (-180 to +180)</span>
                <input
                  type="number"
                  step="any"
                  min="-180"
                  max="180"
                  placeholder="e.g. 77.5946"
                  value={form.longitude}
                  onChange={(event) => setForm((current) => ({ ...current, longitude: event.target.value }))}
                  required
                />
              </label>

              <div className="full-width">
                <button
                  type="button"
                  className="secondary-button location-button full-width"
                  onClick={requestGeolocation}
                >
                  <LocateFixed size={17} /> Capture Current Device Location
                </button>
                {geoMessage && <p className="helper-text">{geoMessage}</p>}
                {Number(form.locationAccuracy) > Number(form.radiusMeters) && Number(form.radiusMeters) > 0 && (
                  <p className="helper-text location-accuracy-warning">
                    Captured GPS accuracy (±{Math.round(form.locationAccuracy)}m) is broader than your geofence radius. Consider expanding radius to accommodate device variance.
                  </p>
                )}
              </div>

              <label className="full-width">
                <span>Geofence Radius (Meters)</span>
                <input
                  type="number"
                  min="5"
                  step="1"
                  placeholder="e.g. 50 (Standard Room / Hall)"
                  value={form.radiusMeters}
                  onChange={(event) => setForm((current) => ({ ...current, radiusMeters: event.target.value }))}
                  required
                />
              </label>

              <div className="modal-actions full-width">
                <button type="button" className="secondary-button" onClick={closeModal}>
                  Cancel
                </button>
                <button type="submit" className="primary-button">
                  {editingSession ? "Save Changes" : "Create Session"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirm Delete Modal */}
      <ConfirmModal
        isOpen={deleteConfirm.open}
        title="Delete Session"
        message={`Delete attendance session "${deleteConfirm.session?.title}"? Note: Sessions that already have recorded attendance records cannot be deleted.`}
        confirmText="Delete Session"
        tone="danger"
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteConfirm({ open: false, session: null })}
      />
    </div>
  );
}
