import { useEffect, useMemo, useState } from "react";
import { ArrowDownWideNarrow, ArrowUpWideNarrow, LocateFixed, Plus, X } from "lucide-react";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import SessionTable from "../components/SessionTable";
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

  const canCreateSessions = user?.role === "ADMIN" || user?.role === "CLUB_USER";
  const canManageSessions = user?.role === "ADMIN";
  const sortedSessions = useMemo(
    () => [...sessions].sort((left, right) => {
      const difference = new Date(left.startTime) - new Date(right.startTime);
      return sortOrder === "newest" ? -difference : difference;
    }),
    [sessions, sortOrder],
  );

  const fetchSessions = async () => {
    try {
      setLoading(true);
      const data = await sessionService.listSessions();
      setSessions(data);
    } catch (error) {
      showToast(error.message || "Unable to load sessions.", "error");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, [showToast]);

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
      latitude: session.latitude ?? "",
      longitude: session.longitude ?? "",
      radiusMeters: session.radiusMeters ?? "",
      locationAccuracy: "",
    });
    setShowModal(true);
  };

  const handleDelete = async (session) => {
    if (!window.confirm(`Delete “${session.title}”? Sessions with attendance records cannot be deleted.`)) return;
    try {
      await sessionService.deleteSession(session.id);
      showToast("Session deleted.", "success");
      fetchSessions();
    } catch (error) {
      showToast(error.message || "Unable to delete session.", "error");
    }
  };

  const requestGeolocation = () => {
    if (!navigator.geolocation) {
      setGeoMessage("Geolocation is unavailable in this browser. Please enter latitude and longitude manually.");
      return;
    }

    setGeoMessage("Requesting current location...");

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
        showToast("Enter valid coordinates and a geofence radius greater than zero.", "error");
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
      showToast(error.message || "Unable to create session.", "error");
    }
  };

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">Attendance planning</p>
          <h1>Attendance Sessions</h1>
        </div>

        <div className="page-header-actions">
          <div className="sort-control" role="group" aria-label="Sort sessions">
            <button type="button" className={sortOrder === "newest" ? "selected" : ""} aria-pressed={sortOrder === "newest"} onClick={() => setSortOrder("newest")}>
              <ArrowDownWideNarrow size={15} /> Newest
            </button>
            <button type="button" className={sortOrder === "oldest" ? "selected" : ""} aria-pressed={sortOrder === "oldest"} onClick={() => setSortOrder("oldest")}>
              <ArrowUpWideNarrow size={15} /> Oldest
            </button>
          </div>
          {canCreateSessions && (
            <button type="button" className="primary-button" onClick={() => setShowModal(true)}>
              <Plus size={17} /> Create Session
            </button>
          )}
        </div>
      </div>

      {loading ? (
        <Loading message="Loading sessions..." />
      ) : sessions.length ? (
        <SessionTable sessions={sortedSessions} canManage={canManageSessions} onEdit={openEditModal} onDelete={handleDelete} />
      ) : (
        <EmptyState title="No attendance sessions yet" description="Create a session to start tracking attendance." />
      )}

      {showModal && (
        <div className="modal-backdrop" onClick={closeModal}>
          <div className="modal-card large" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingSession ? "Edit Session" : "Create Session"}</h3>
              <button type="button" className="close-button" onClick={closeModal} aria-label="Close dialog" title="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="form-grid two-column">
              <label className="full-width">
                <span>Session Title</span>
                <input
                  type="text"
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
                  <strong>Geofence required</strong>
                  <p>Every session verifies member check-ins against this location. If a member cannot check in with GPS, staff can record a manual override from Attendance.</p>
                </div>
              </div>

              <label>
                <span>Latitude</span>
                <input
                  type="number"
                  step="any"
                  min="-90"
                  max="90"
                  value={form.latitude}
                  onChange={(event) => setForm((current) => ({ ...current, latitude: event.target.value }))}
                  required
                />
              </label>

              <label>
                <span>Longitude</span>
                <input
                  type="number"
                  step="any"
                  min="-180"
                  max="180"
                  value={form.longitude}
                  onChange={(event) => setForm((current) => ({ ...current, longitude: event.target.value }))}
                  required
                />
              </label>

              <div className="full-width">
                <button type="button" className="secondary-button location-button" onClick={requestGeolocation}>
                  <LocateFixed size={17} /> Use Current Location
                </button>
                {geoMessage && <p className="helper-text">{geoMessage}</p>}
                {Number(form.locationAccuracy) > Number(form.radiusMeters) && Number(form.radiusMeters) > 0 && (
                  <p className="helper-text location-accuracy-warning">
                    The radius is smaller than this device’s captured accuracy; use a more accurate fix or choose a larger geofence radius.
                  </p>
                )}
                <p className="helper-text">If location access is unavailable, enter the venue coordinates above.</p>
              </div>

              <label>
                <span>Geofence radius in meters</span>
                <input
                  type="number"
                  min="1"
                  step="1"
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
    </div>
  );
}
