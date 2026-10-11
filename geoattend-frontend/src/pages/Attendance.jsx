import { useEffect, useMemo, useState } from "react";
import Loading from "../components/Loading";
import AttendanceTable from "../components/AttendanceTable";
import attendanceService from "../services/attendanceService";
import memberService from "../services/memberService";
import sessionService from "../services/sessionService";
import { ArrowDownWideNarrow, ArrowUpWideNarrow, Check, LocateFixed, Plus, X } from "lucide-react";

const initialForm = {
  sessionId: "",
  memberId: "",
  method: "GEOFENCE",
  latitude: "",
  longitude: "",
  markedAt: "",
};

const toDateTimeInput = (value) => {
  const date = new Date(value);
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return localDate.toISOString().slice(0, 16);
};

export default function Attendance({ user, showToast }) {
  const [sessions, setSessions] = useState([]);
  const [members, setMembers] = useState([]);
  const [records, setRecords] = useState([]);
  const [trackingRecords, setTrackingRecords] = useState([]);
  const [locationAudit, setLocationAudit] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [filters, setFilters] = useState({ sessionId: "all", memberId: "all", method: "all" });
  const [form, setForm] = useState(initialForm);
  const [geoMessage, setGeoMessage] = useState("");
  const [sortOrder, setSortOrder] = useState("newest");

  const canMarkAttendance = user?.role === "ADMIN" || user?.role === "CLUB_USER";
  const canManageAttendance = user?.role === "ADMIN";
  const selectedSession = sessions.find((session) => String(session.id) === String(form.sessionId));
  const isSelectedSessionGeofence = Boolean(selectedSession?.locationEnabled ?? true);

  const getAllAttendance = async (silent = false) => {
    try {
      if (!silent) setLoading(true);
      const [sessionList, memberList] = await Promise.all([
        sessionService.listSessions(),
        memberService.listMembers(),
      ]);

      setSessions(sessionList);
      setMembers(memberList);

      const attendanceBySession = await Promise.all(
        sessionList.map((session) => attendanceService.getAttendanceForSession(session.id)),
      );
      const trackingBySession = await Promise.all(
        sessionList.map((session) => attendanceService.getTrackingForSession(session.id)),
      );
      const auditBySession = user?.role === "ADMIN"
        ? await Promise.all(sessionList.map((session) => attendanceService.getLocationChecksForSession(session.id)))
        : [];
      setRecords(
        attendanceBySession.flatMap((data, index) =>
          data.map((record) => ({ ...record, sessionTitle: sessionList[index].title })),
        ),
      );
      setTrackingRecords(trackingBySession.flat());
      setLocationAudit(auditBySession.flat());
    } catch (error) {
      if (!silent) showToast(error.message || "Unable to load attendance records.", "error");
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => {
    getAllAttendance();
    const timer = window.setInterval(() => getAllAttendance(true), 30000);
    return () => window.clearInterval(timer);
  }, [showToast]);

  const filteredRecords = useMemo(() => {
    return records.filter((record) => {
      const matchesSession = filters.sessionId === "all" || String(record.sessionId) === String(filters.sessionId);
      const matchesMember = filters.memberId === "all" || String(record.memberId) === String(filters.memberId);
      const matchesMethod = filters.method === "all" || record.attendanceMethod === filters.method;

      return matchesSession && matchesMember && matchesMethod;
    }).sort((left, right) => {
      const difference = new Date(left.markedAt) - new Date(right.markedAt);
      return sortOrder === "newest" ? -difference : difference;
    });
  }, [records, filters, sortOrder]);

  const sortedLocationAudit = useMemo(
    () => [...locationAudit].sort((left, right) => {
      const difference = new Date(left.checkedAt) - new Date(right.checkedAt);
      return sortOrder === "newest" ? -difference : difference;
    }),
    [locationAudit, sortOrder],
  );

  const reviewRecords = trackingRecords.filter((record) => record.status === "REVIEW");

  const closeModal = () => {
    setShowModal(false);
    setEditingRecord(null);
    setForm(initialForm);
    setGeoMessage("");
  };

  const openEditModal = (record) => {
    setEditingRecord(record);
    setForm({
      sessionId: String(record.sessionId),
      memberId: String(record.memberId),
      method: record.attendanceMethod,
      latitude: record.latitude ?? "",
      longitude: record.longitude ?? "",
      markedAt: toDateTimeInput(record.markedAt),
    });
    setShowModal(true);
  };

  const handleDelete = async (record) => {
    if (!window.confirm("Delete this attendance record? This cannot be undone.")) return;
    try {
      await attendanceService.deleteAttendance(record.id);
      showToast("Attendance record deleted.", "success");
      getAllAttendance();
    } catch (error) {
      showToast(error.message || "Unable to delete attendance record.", "error");
    }
  };

  const approveManualOverride = async (tracking) => {
    const confirmed = window.confirm(
      `Record a manual attendance override for ${tracking.memberName} in “${tracking.sessionTitle}”?`,
    );
    if (!confirmed) return;

    try {
      await attendanceService.createManualAttendance({
        sessionId: tracking.sessionId,
        memberId: tracking.memberId,
      });
      showToast("Manual override recorded. Attendance is finalized.", "success");
      getAllAttendance();
    } catch (error) {
      showToast(error.message || "Unable to record manual override.", "error");
    }
  };

  const requestGeolocation = () => {
    if (!navigator.geolocation) {
      setGeoMessage("Geolocation is unavailable in this browser. You may use manual attendance instead.");
      return;
    }

    setGeoMessage("Requesting your current location...");

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setForm((current) => ({
          ...current,
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        }));
        setGeoMessage("Location captured successfully.");
      },
      () => {
        setGeoMessage("Location access was denied. Please use manual attendance if you are authorized.");
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
      if (editingRecord) {
        await attendanceService.updateAttendance(editingRecord.id, {
          sessionId: Number(form.sessionId),
          memberId: Number(form.memberId),
          attendanceMethod: form.method,
          latitude: form.method === "GEOFENCE" ? Number(form.latitude) : null,
          longitude: form.method === "GEOFENCE" ? Number(form.longitude) : null,
          markedAt: form.markedAt,
        });
        showToast("Attendance record updated.", "success");
        closeModal();
        getAllAttendance();
        return;
      }

      if (form.method === "GEOFENCE") {
        const payload = {
          sessionId: Number(form.sessionId),
          memberId: Number(form.memberId),
          latitude: Number(form.latitude),
          longitude: Number(form.longitude),
        };

        if (!payload.latitude || !payload.longitude) {
          setGeoMessage("Location is required for geofence attendance. Please allow location access.");
          return;
        }

        await attendanceService.createAttendance(payload);
        showToast("Attendance marked successfully.", "success");
      } else {
        await attendanceService.createManualAttendance({
          sessionId: Number(form.sessionId),
          memberId: Number(form.memberId),
        });
        showToast("Manual attendance recorded successfully.", "success");
      }

      closeModal();
      getAllAttendance();
    } catch (error) {
      showToast(error.message || "Unable to record attendance.", "error");
    }
  };

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">Records</p>
          <h1>Attendance</h1>
        </div>

        <div className="page-header-actions">
          <div className="sort-control" role="group" aria-label="Sort attendance">
            <button type="button" className={sortOrder === "newest" ? "selected" : ""} aria-pressed={sortOrder === "newest"} onClick={() => setSortOrder("newest")}>
              <ArrowDownWideNarrow size={15} /> Newest
            </button>
            <button type="button" className={sortOrder === "oldest" ? "selected" : ""} aria-pressed={sortOrder === "oldest"} onClick={() => setSortOrder("oldest")}>
              <ArrowUpWideNarrow size={15} /> Oldest
            </button>
          </div>
          {canMarkAttendance && (
            <button type="button" className="primary-button" onClick={() => setShowModal(true)}>
              <Plus size={17} /> Mark Attendance
            </button>
          )}
        </div>
      </div>

      <div className="filter-row">
        <label>
          <span>Session</span>
          <select value={filters.sessionId} onChange={(event) => setFilters((current) => ({ ...current, sessionId: event.target.value }))}>
            <option value="all">All sessions</option>
            {sessions.map((session) => (
              <option key={session.id} value={session.id}>
                {session.title}
              </option>
            ))}
          </select>
        </label>

        <label>
          <span>Member</span>
          <select value={filters.memberId} onChange={(event) => setFilters((current) => ({ ...current, memberId: event.target.value }))}>
            <option value="all">All members</option>
            {members.map((member) => (
              <option key={member.id} value={member.id}>
                {member.name}
              </option>
            ))}
          </select>
        </label>

        <label>
          <span>Method</span>
          <select value={filters.method} onChange={(event) => setFilters((current) => ({ ...current, method: event.target.value }))}>
            <option value="all">All methods</option>
            <option value="GEOFENCE">GEOFENCE</option>
            <option value="MANUAL">MANUAL</option>
          </select>
        </label>
      </div>

      <section className="panel attendance-review-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Staff action</p>
            <h3>Needs review <span className="history-count">{reviewRecords.length}</span></h3>
          </div>
          <span className="review-policy-note">Under 80% of expected checks inside the fence</span>
        </div>
        {reviewRecords.length ? (
          <div className="table-card">
            <table>
              <thead>
                <tr>
                  <th>Member</th>
                  <th>Session</th>
                  <th>Inside checks</th>
                  <th>Last location check</th>
                  <th>Review</th>
                </tr>
              </thead>
              <tbody>
                {reviewRecords.map((tracking) => (
                  <tr key={tracking.id}>
                    <td>{tracking.memberName}</td>
                    <td>{tracking.sessionTitle}</td>
                    <td>{tracking.insideChecks} / {tracking.expectedChecks}</td>
                    <td>{new Date(tracking.lastCheckAt).toLocaleString()}</td>
                    <td>
                      <button
                        type="button"
                        className="action-button review-approve-button"
                        disabled={!canMarkAttendance}
                        onClick={() => approveManualOverride(tracking)}
                      >
                        <Check size={14} /> Manual override
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="review-empty">No attendance cases are waiting for review.</p>
        )}
      </section>

      {user?.role === "ADMIN" && (
        <section className="panel location-audit-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Admin only · precise location history</p>
              <h3>Geofence check audit <span className="history-count">{locationAudit.length}</span></h3>
            </div>
          </div>
          {sortedLocationAudit.length ? (
            <div className="table-card">
              <table>
                <thead>
                  <tr>
                    <th>Member</th>
                    <th>Session</th>
                    <th>Check</th>
                    <th>Checked at</th>
                    <th>Coordinates</th>
                    <th>GPS accuracy</th>
                    <th>Center distance</th>
                    <th>Result</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedLocationAudit.map((check) => (
                    <tr key={`${check.trackingId}-${check.checkNumber}`}>
                      <td>{check.memberName}</td>
                      <td>{check.sessionTitle}</td>
                      <td>{check.checkNumber + 1}</td>
                      <td>{new Date(check.checkedAt).toLocaleString()}</td>
                      <td>{Number(check.latitude).toFixed(6)}, {Number(check.longitude).toFixed(6)}</td>
                      <td>{check.accuracyMeters == null ? "N/A" : `±${Number(check.accuracyMeters).toFixed(0)} m`}</td>
                      <td>{Number(check.distanceMeters).toFixed(1)} m</td>
                      <td><span className={`method-badge ${check.insideGeofence ? "geofence" : "manual"}`}>{check.insideGeofence ? "INSIDE" : "OUTSIDE"}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="review-empty">No location checks have been recorded yet.</p>
          )}
        </section>
      )}

      {loading ? (
        <Loading message="Loading attendance..." />
      ) : (
        <AttendanceTable
          records={filteredRecords}
          members={members}
          sessions={sessions}
          canManage={canManageAttendance}
          onEdit={openEditModal}
          onDelete={handleDelete}
        />
      )}

      {showModal && (
        <div className="modal-backdrop" onClick={closeModal}>
          <div className="modal-card large" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingRecord ? "Edit Attendance Record" : "Mark Attendance"}</h3>
              <button type="button" className="close-button" onClick={closeModal} aria-label="Close dialog">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="form-grid two-column">
              <label>
                <span>Select Session</span>
                <select
                  value={form.sessionId}
                  onChange={(event) => {
                    const selected = sessions.find((session) => String(session.id) === String(event.target.value));
                    setForm((current) => ({
                      ...current,
                      sessionId: event.target.value,
                      method: selected?.locationEnabled ? "GEOFENCE" : "MANUAL",
                    }));
                  }}
                  required
                >
                  <option value="">Select a session</option>
                  {sessions.map((session) => (
                    <option key={session.id} value={session.id}>
                      {session.title}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                <span>Select Member</span>
                <select value={form.memberId} onChange={(event) => setForm((current) => ({ ...current, memberId: event.target.value }))} required>
                  <option value="">Select a member</option>
                  {members.map((member) => (
                    <option key={member.id} value={member.id}>
                      {member.name}
                    </option>
                  ))}
                </select>
              </label>

              {editingRecord && (
                <label>
                  <span>Attendance time</span>
                  <input
                    type="datetime-local"
                    value={form.markedAt}
                    onChange={(event) => setForm((current) => ({ ...current, markedAt: event.target.value }))}
                    required
                  />
                </label>
              )}

              <div className="full-width radio-group">
                <label className="radio-option">
                  <input
                    type="radio"
                    name="method"
                    value="GEOFENCE"
                    checked={form.method === "GEOFENCE"}
                    onChange={(event) => setForm((current) => ({ ...current, method: event.target.value }))}
                    disabled={!isSelectedSessionGeofence}
                  />
                  <span>Geofence</span>
                </label>

                <label className="radio-option">
                  <input
                    type="radio"
                    name="method"
                    value="MANUAL"
                    checked={form.method === "MANUAL"}
                    onChange={(event) => setForm((current) => ({ ...current, method: event.target.value }))}
                  />
                  <span>Manual</span>
                </label>
              </div>

              {!isSelectedSessionGeofence && (
                <div className="full-width notice-box">
                  <strong>This session is not geo-fence enabled.</strong>
                  <p>Manual attendance is allowed for this session, but geofence validation is disabled.</p>
                </div>
              )}

              {form.method === "GEOFENCE" && isSelectedSessionGeofence && (
                <>
                  <label>
                    <span>Latitude</span>
                    <input
                      type="number"
                      step="any"
                      value={form.latitude}
                      onChange={(event) => setForm((current) => ({ ...current, latitude: event.target.value }))}
                    />
                  </label>

                  <label>
                    <span>Longitude</span>
                    <input
                      type="number"
                      step="any"
                      value={form.longitude}
                      onChange={(event) => setForm((current) => ({ ...current, longitude: event.target.value }))}
                    />
                  </label>

                  <div className="full-width">
                    <button type="button" className="secondary-button full-width" onClick={requestGeolocation}>
                      <LocateFixed size={17} /> Use Current Location
                    </button>
                    {geoMessage && <p className="helper-text">{geoMessage}</p>}
                  </div>
                </>
              )}

              {form.method === "MANUAL" && (
                <div className="full-width notice-box">
                  <strong>Manual attendance is an authorized override.</strong>
                  <p>It does not use location verification and should only be used when geofence attendance is not possible.</p>
                </div>
              )}

              <div className="modal-actions full-width">
                <button type="button" className="secondary-button" onClick={closeModal}>
                  Cancel
                </button>
                <button type="submit" className="primary-button">
                  {editingRecord ? "Save Changes" : form.method === "MANUAL" ? "Confirm Manual Attendance" : "Mark Attendance"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
