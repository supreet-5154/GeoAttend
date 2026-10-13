import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowDownWideNarrow,
  ArrowUpWideNarrow,
  Check,
  CheckCircle2,
  Compass,
  FileSpreadsheet,
  Layers,
  LocateFixed,
  Plus,
  Search,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  X,
} from "lucide-react";
import AttendanceTable from "../components/AttendanceTable";
import ConfirmModal from "../components/ConfirmModal";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import ActiveFilterBadges from "../components/ActiveFilterBadges";
import Pagination from "../components/Pagination";
import { useDebounce } from "../hooks/useDebounce";
import attendanceService from "../services/attendanceService";
import batchService from "../services/batchService";
import memberService from "../services/memberService";
import sessionService from "../services/sessionService";

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
  const [batches, setBatches] = useState([]);
  const [records, setRecords] = useState([]);
  const [trackingRecords, setTrackingRecords] = useState([]);
  const [locationAudit, setLocationAudit] = useState([]);
  const [loading, setLoading] = useState(true);

  // Tab state: "records" | "review" | "audit"
  const [activeTab, setActiveTab] = useState("records");

  // Fast manual check-in modal state
  const [showFastCheckIn, setShowFastCheckIn] = useState(false);
  const [fastSessionId, setFastSessionId] = useState("");
  const [fastMemberSearch, setFastMemberSearch] = useState("");
  const [fastBatchFilter, setFastBatchFilter] = useState("all");
  const [markingMemberId, setMarkingMemberId] = useState(null);

  // Standard CRUD Modal state
  const [showModal, setShowModal] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [geoMessage, setGeoMessage] = useState("");

  // Filters & Sorting state
  const [filters, setFilters] = useState({
    sessionId: "all",
    memberId: "all",
    method: "all",
    batchId: "all",
    status: "all",
    fromDate: "",
    toDate: "",
  });
  const [searchMemberQuery, setSearchMemberQuery] = useState("");
  const debouncedSearch = useDebounce(searchMemberQuery, 350);
  const [sortOrder, setSortOrder] = useState("newest");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Confirmation Modals state
  const [deleteConfirm, setDeleteConfirm] = useState({ open: false, record: null });
  const [reviewConfirm, setReviewConfirm] = useState({ open: false, tracking: null });

  const canMarkAttendance = user?.role === "ADMIN" || user?.role === "CLUB_USER";
  const canManageAttendance = user?.role === "ADMIN";

  const memberMap = useMemo(() => new Map(members.map((m) => [m.id, m])), [members]);

  // Find currently active session (if any)
  const [now] = useState(() => Date.now());
  const currentActiveSession = useMemo(() => {
    return sessions.find((s) => {
      const start = new Date(s.startTime).getTime();
      const end = new Date(s.endTime).getTime();
      return now >= start && now <= end;
    });
  }, [sessions, now]);

  const loadMetadata = useCallback(async () => {
    try {
      const [sessionList, memberList, batchList] = await Promise.all([
        sessionService.listSessions(),
        memberService.listMembers(),
        batchService.listBatches(),
      ]);
      setSessions(sessionList || []);
      setMembers(memberList || []);
      setBatches(batchList || []);
    } catch (error) {
      console.error("Failed to load metadata", error);
    }
  }, []);

  const getAllAttendance = useCallback(
    async (silent = false) => {
      try {
        if (!silent) setLoading(true);
        const params = {
          sessionId: filters.sessionId !== "all" ? filters.sessionId : undefined,
          batchId: filters.batchId !== "all" && filters.batchId !== "unassigned" ? filters.batchId : undefined,
          method: filters.method !== "all" ? filters.method : undefined,
          status: filters.status !== "all" ? filters.status : undefined,
          search: debouncedSearch.trim() || undefined,
          fromDate: filters.fromDate || undefined,
          toDate: filters.toDate || undefined,
          sortBy: "markedAt",
          sortDirection: sortOrder === "newest" ? "desc" : "asc",
          page,
          size: pageSize,
        };

        const data = await attendanceService.getAttendance(params);
        if (data && data.content !== undefined) {
          setRecords(data.content);
          setTotalPages(data.totalPages);
          setTotalElements(data.totalElements);
        } else if (Array.isArray(data)) {
          setRecords(data);
          setTotalPages(Math.ceil(data.length / pageSize));
          setTotalElements(data.length);
        }
      } catch (error) {
        if (!silent) showToast(error.message || "Unable to load attendance records.", "error");
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [filters, debouncedSearch, sortOrder, page, pageSize, showToast],
  );

  const loadReviewAndAudit = useCallback(async () => {
    try {
      if (sessions.length > 0) {
        const trackingBySession = await Promise.all(
          sessions.map((session) => attendanceService.getTrackingForSession(session.id)),
        );
        setTrackingRecords(trackingBySession.flat());

        if (user?.role === "ADMIN") {
          const auditBySession = await Promise.all(
            sessions.map((session) =>
              attendanceService.getLocationChecksForSession(session.id),
            ),
          );
          setLocationAudit(auditBySession.flat());
        }
      }
    } catch (error) {
      console.error("Failed to load review/audit records", error);
    }
  }, [sessions, user]);

  useEffect(() => {
    loadMetadata();
  }, [loadMetadata]);

  // Reset page to 0 on filter change
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, filters, sortOrder]);

  useEffect(() => {
    getAllAttendance();
    const timer = window.setInterval(() => getAllAttendance(true), 30000);
    return () => window.clearInterval(timer);
  }, [getAllAttendance]);

  useEffect(() => {
    if (activeTab === "review" || activeTab === "audit") {
      loadReviewAndAudit();
    }
  }, [activeTab, loadReviewAndAudit]);

  // Set default fastSessionId to current active session or first session
  useEffect(() => {
    if (currentActiveSession) {
      setFastSessionId(String(currentActiveSession.id));
    } else if (sessions.length > 0 && !fastSessionId) {
      setFastSessionId(String(sessions[0].id));
    }
  }, [currentActiveSession, sessions, fastSessionId]);

  const filteredRecords = records;

  const activeFilterList = useMemo(() => {
    const list = [];
    if (debouncedSearch.trim()) {
      list.push({
        key: "search",
        label: "Search",
        value: debouncedSearch.trim(),
        onRemove: () => setSearchMemberQuery(""),
      });
    }
    if (filters.sessionId !== "all") {
      const s = sessions.find((item) => String(item.id) === String(filters.sessionId));
      list.push({
        key: "session",
        label: "Session",
        value: s?.title || `Session #${filters.sessionId}`,
        onRemove: () => setFilters((curr) => ({ ...curr, sessionId: "all" })),
      });
    }
    if (filters.batchId !== "all") {
      const b = batches.find((item) => String(item.id) === String(filters.batchId));
      list.push({
        key: "batch",
        label: "Batch",
        value: filters.batchId === "unassigned" ? "Unassigned" : b?.name || `Batch #${filters.batchId}`,
        onRemove: () => setFilters((curr) => ({ ...curr, batchId: "all" })),
      });
    }
    if (filters.method !== "all") {
      list.push({
        key: "method",
        label: "Method",
        value: filters.method,
        onRemove: () => setFilters((curr) => ({ ...curr, method: "all" })),
      });
    }
    if (filters.status !== "all") {
      list.push({
        key: "status",
        label: "Status",
        value: filters.status,
        onRemove: () => setFilters((curr) => ({ ...curr, status: "all" })),
      });
    }
    if (filters.fromDate) {
      list.push({
        key: "fromDate",
        label: "From",
        value: filters.fromDate,
        onRemove: () => setFilters((curr) => ({ ...curr, fromDate: "" })),
      });
    }
    if (filters.toDate) {
      list.push({
        key: "toDate",
        label: "To",
        value: filters.toDate,
        onRemove: () => setFilters((curr) => ({ ...curr, toDate: "" })),
      });
    }
    return list;
  }, [debouncedSearch, filters, sessions, batches]);

  const clearAllFilters = useCallback(() => {
    setSearchMemberQuery("");
    setFilters({
      sessionId: "all",
      memberId: "all",
      method: "all",
      batchId: "all",
      status: "all",
      fromDate: "",
      toDate: "",
    });
    setPage(0);
  }, []);

  const reviewRecords = useMemo(() => {
    return trackingRecords.filter((record) => {
      if (record.status !== "REVIEW") return false;
      const matchesSession =
        filters.sessionId === "all" || String(record.sessionId) === String(filters.sessionId);
      const matchesMember =
        filters.memberId === "all" || String(record.memberId) === String(filters.memberId);

      const member = memberMap.get(record.memberId);
      let matchesBatch = true;
      if (filters.batchId === "unassigned") {
        matchesBatch = !member?.batchId;
      } else if (filters.batchId !== "all") {
        matchesBatch = String(member?.batchId) === String(filters.batchId);
      }

      return matchesSession && matchesMember && matchesBatch;
    });
  }, [trackingRecords, filters, memberMap]);

  // Batch breakdown calculation for the selected session / all sessions
  const batchStats = useMemo(() => {
    const relevantRecords =
      filters.sessionId === "all"
        ? records
        : records.filter((r) => String(r.sessionId) === String(filters.sessionId));

    const counts = {};
    let unassignedCount = 0;

    batches.forEach((b) => {
      counts[b.id] = { id: b.id, name: b.name, count: 0 };
    });

    relevantRecords.forEach((r) => {
      const m = memberMap.get(r.memberId);
      if (m?.batchId && counts[m.batchId]) {
        counts[m.batchId].count += 1;
      } else {
        unassignedCount += 1;
      }
    });

    return {
      batchList: Object.values(counts),
      unassigned: unassignedCount,
      total: relevantRecords.length,
    };
  }, [records, filters.sessionId, batches, memberMap]);

  // Review Queue Pagination state
  const [reviewPage, setReviewPage] = useState(0);
  const [reviewPageSize, setReviewPageSize] = useState(10);
  const reviewTotalPages = Math.ceil(reviewRecords.length / reviewPageSize);
  const pagedReviewRecords = useMemo(() => {
    const start = reviewPage * reviewPageSize;
    return reviewRecords.slice(start, start + reviewPageSize);
  }, [reviewRecords, reviewPage, reviewPageSize]);

  useEffect(() => {
    setReviewPage(0);
  }, [reviewRecords.length]);

  // GPS Audit Log Pagination state
  const [auditPage, setAuditPage] = useState(0);
  const [auditPageSize, setAuditPageSize] = useState(10);
  const auditTotalPages = Math.ceil(locationAudit.length / auditPageSize);
  const pagedAuditRecords = useMemo(() => {
    const start = auditPage * auditPageSize;
    return locationAudit.slice(start, start + auditPageSize);
  }, [locationAudit, auditPage, auditPageSize]);

  useEffect(() => {
    setAuditPage(0);
  }, [locationAudit.length]);

  // Fast Manual Check-in list of members
  const fastMembersList = useMemo(() => {
    const targetSessionId = fastSessionId || (sessions[0]?.id ? String(sessions[0].id) : "");
    const sessionAttendedIds = new Set(
      records
        .filter((r) => String(r.sessionId) === String(targetSessionId))
        .map((r) => String(r.memberId)),
    );

    return members
      .filter((m) => {
        const matchesSearch =
          m.name.toLowerCase().includes(fastMemberSearch.toLowerCase()) ||
          (m.studentId && m.studentId.toLowerCase().includes(fastMemberSearch.toLowerCase()));

        let matchesBatch = true;
        if (fastBatchFilter === "unassigned") {
          matchesBatch = !m.batchId;
        } else if (fastBatchFilter !== "all") {
          matchesBatch = String(m.batchId) === String(fastBatchFilter);
        }

        return matchesSearch && matchesBatch;
      })
      .map((m) => ({
        ...m,
        alreadyMarked: sessionAttendedIds.has(String(m.id)),
      }));
  }, [members, fastSessionId, sessions, records, fastMemberSearch, fastBatchFilter]);

  const handleQuickMarkPresent = async (member) => {
    if (!fastSessionId) {
      showToast("Please select a session.", "error");
      return;
    }

    try {
      setMarkingMemberId(member.id);
      await attendanceService.createManualAttendance({
        sessionId: Number(fastSessionId),
        memberId: member.id,
      });

      showToast(`Marked ${member.name} present (Manual Override).`, "success");
      await getAllAttendance(true);
    } catch (error) {
      showToast(error.message || `Unable to mark attendance for ${member.name}.`, "error");
    } finally {
      setMarkingMemberId(null);
    }
  };

  const handleApproveReview = async () => {
    const tracking = reviewConfirm.tracking;
    if (!tracking) return;

    try {
      await attendanceService.createManualAttendance({
        sessionId: tracking.sessionId,
        memberId: tracking.memberId,
      });
      showToast(
        `Approved manual override for ${tracking.memberName || "member"}. Attendance is finalized.`,
        "success",
      );
      setReviewConfirm({ open: false, tracking: null });
      getAllAttendance();
    } catch (error) {
      showToast(error.message || "Unable to approve manual override.", "error");
    }
  };

  const handleDeleteRecord = async () => {
    const record = deleteConfirm.record;
    if (!record) return;

    try {
      await attendanceService.deleteAttendance(record.id);
      showToast("Attendance record deleted.", "success");
      setDeleteConfirm({ open: false, record: null });
      getAllAttendance();
    } catch (error) {
      showToast(error.message || "Unable to delete attendance record.", "error");
    }
  };

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

  const requestGeolocation = () => {
    if (!navigator.geolocation) {
      setGeoMessage("Geolocation unavailable. Manual attendance can be used instead.");
      return;
    }

    setGeoMessage("Acquiring GPS fix...");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setForm((curr) => ({
          ...curr,
          latitude: String(pos.coords.latitude),
          longitude: String(pos.coords.longitude),
        }));
        setGeoMessage(`Location captured (±${Math.round(pos.coords.accuracy)}m).`);
      },
      () => {
        setGeoMessage("Location permission denied. Enter coordinates manually or use manual method.");
      },
      { enableHighAccuracy: true, timeout: 10000 },
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
        showToast("Attendance record updated successfully.", "success");
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
          setGeoMessage("Latitude and longitude are required for geofence verification.");
          return;
        }

        await attendanceService.createAttendance(payload);
        showToast("Geofence attendance recorded successfully.", "success");
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
      showToast(error.message || "Unable to save attendance.", "error");
    }
  };

  const selectedFormSession = sessions.find((s) => String(s.id) === String(form.sessionId));
  const isSelectedSessionGeofence = Boolean(selectedFormSession?.locationEnabled ?? true);

  return (
    <div className="page-shell attendance-page">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <p className="eyebrow">Presence Verification</p>
          <h1>Attendance Management</h1>
        </div>

        <div className="page-header-actions">
          {canMarkAttendance && (
            <>
              <button
                type="button"
                className="primary-button fast-checkin-btn"
                onClick={() => setShowFastCheckIn(true)}
              >
                <UserCheck size={16} />
                <span>Fast Manual Check-in</span>
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  setEditingRecord(null);
                  setForm(initialForm);
                  setShowModal(true);
                }}
              >
                <Plus size={16} />
                <span>Custom Entry</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Tabs Bar: Records / Review Queue / Location Audit */}
      <div className="attendance-nav-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "records"}
          className={activeTab === "records" ? "active" : ""}
          onClick={() => setActiveTab("records")}
        >
          <FileSpreadsheet size={16} />
          <span>Attendance Records</span>
          <span className="tab-pill-badge neutral">{records.length}</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "review"}
          className={activeTab === "review" ? "active" : ""}
          onClick={() => setActiveTab("review")}
        >
          <ShieldAlert size={16} />
          <span>Needs Review</span>
          {reviewRecords.length > 0 ? (
            <span className="tab-pill-badge warning">{reviewRecords.length}</span>
          ) : (
            <span className="tab-pill-badge neutral">0</span>
          )}
        </button>

        {user?.role === "ADMIN" && (
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "audit"}
            className={activeTab === "audit" ? "active" : ""}
            onClick={() => setActiveTab("audit")}
          >
            <Compass size={16} />
            <span>GPS Audit Log</span>
            <span className="tab-pill-badge neutral">{locationAudit.length}</span>
          </button>
        )}
      </div>

      {/* TAB 1: ATTENDANCE RECORDS */}
      {activeTab === "records" && (
        <>
          {/* Cohort / Batch Distribution Chips */}
          <div className="batch-distribution-strip">
            <span className="strip-label">
              <Layers size={14} /> Cohort Attendance:
            </span>
            {batchStats.batchList.map((b) => (
              <span key={b.id} className="batch-stat-chip">
                <strong>{b.name}:</strong> {b.count} present
              </span>
            ))}
            {batchStats.unassigned > 0 && (
              <span className="batch-stat-chip unassigned">
                <strong>Unassigned:</strong> {batchStats.unassigned} present
              </span>
            )}
            <span className="batch-stat-chip total">
              <strong>Total:</strong> {batchStats.total}
            </span>
          </div>

          {/* Filter Bar with Search, Session, Method, Batch, Status, and Date Range selectors */}
          <div className="filter-bar attendance-filters">
            <div className="search-wrap">
              <Search size={16} />
              <input
                type="text"
                placeholder="Search by member name, email or session..."
                value={searchMemberQuery}
                onChange={(e) => setSearchMemberQuery(e.target.value)}
                aria-label="Search members"
              />
            </div>

            <div className="filter-controls">
              <select
                className="filter-select"
                value={filters.sessionId}
                onChange={(e) => setFilters((curr) => ({ ...curr, sessionId: e.target.value }))}
                aria-label="Filter by session"
              >
                <option value="all">All Sessions</option>
                {sessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.title}
                  </option>
                ))}
              </select>

              <select
                className="filter-select"
                value={filters.batchId}
                onChange={(e) => setFilters((curr) => ({ ...curr, batchId: e.target.value }))}
                aria-label="Filter by batch"
              >
                <option value="all">All Batches</option>
                <option value="unassigned">Unassigned</option>
                {batches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>

              <select
                className="filter-select"
                value={filters.method}
                onChange={(e) => setFilters((curr) => ({ ...curr, method: e.target.value }))}
                aria-label="Filter by method"
              >
                <option value="all">All Methods</option>
                <option value="GEOFENCE">Geofence (GPS)</option>
                <option value="MANUAL">Manual Override</option>
              </select>

              <select
                className="filter-select"
                value={filters.status}
                onChange={(e) => setFilters((curr) => ({ ...curr, status: e.target.value }))}
                aria-label="Filter by tracking status"
              >
                <option value="all">All Statuses</option>
                <option value="PRESENT">Present</option>
                <option value="TRACKING">Tracking</option>
                <option value="COMPLETED">Completed</option>
                <option value="ABSENT">Absent</option>
              </select>

              <div className="date-filter-inline">
                <label htmlFor="att-from-date">From:</label>
                <input
                  id="att-from-date"
                  type="date"
                  value={filters.fromDate}
                  onChange={(e) => setFilters((curr) => ({ ...curr, fromDate: e.target.value }))}
                  aria-label="From date"
                />
                <label htmlFor="att-to-date">To:</label>
                <input
                  id="att-to-date"
                  type="date"
                  value={filters.toDate}
                  onChange={(e) => setFilters((curr) => ({ ...curr, toDate: e.target.value }))}
                  aria-label="To date"
                />
              </div>

              <div className="sort-control" role="group" aria-label="Sort order">
                <button
                  type="button"
                  className={sortOrder === "newest" ? "selected" : ""}
                  onClick={() => setSortOrder("newest")}
                  title="Newest first"
                >
                  <ArrowDownWideNarrow size={14} /> Newest
                </button>
                <button
                  type="button"
                  className={sortOrder === "oldest" ? "selected" : ""}
                  onClick={() => setSortOrder("oldest")}
                  title="Oldest first"
                >
                  <ArrowUpWideNarrow size={14} /> Oldest
                </button>
              </div>
            </div>
          </div>

          <ActiveFilterBadges
            activeFilters={activeFilterList}
            onClearAll={clearAllFilters}
            totalCount={totalElements}
          />

          {/* Attendance Table */}
          {loading ? (
            <Loading message="Loading attendance records..." />
          ) : filteredRecords.length ? (
            <>
              <AttendanceTable
                records={filteredRecords}
                members={members}
                sessions={sessions}
                canManage={canManageAttendance}
                onEdit={openEditModal}
                onDelete={(record) => setDeleteConfirm({ open: true, record })}
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
              icon={<FileSpreadsheet size={36} />}
              title="No records found"
              description="No attendance records match your search and filter criteria."
              actionLabel={activeFilterList.length > 0 ? "Clear Filters" : undefined}
              onAction={clearAllFilters}
            />
          )}
        </>
      )}

      {/* TAB 2: REVIEW QUEUE */}
      {activeTab === "review" && (
        <section className="panel attendance-review-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Staff Intervention Required</p>
              <h3>Flagged Geofence Cases ({reviewRecords.length})</h3>
            </div>
            <span className="review-policy-note">
              Under 80% of expected checks inside venue perimeter
            </span>
          </div>

          {reviewRecords.length ? (
            <>
              <div className="table-card">
                <table>
                  <thead>
                    <tr>
                      <th>Member</th>
                      <th>Batch</th>
                      <th>Session</th>
                      <th>Intervals Inside</th>
                      <th>Last Location Check</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagedReviewRecords.map((tracking) => {
                      const mem = memberMap.get(tracking.memberId);
                      return (
                        <tr key={tracking.id}>
                          <td>
                            <strong>{tracking.memberName}</strong>
                          </td>
                          <td>
                            <span className={`batch-pill ${mem?.batchName ? "assigned" : "unassigned"}`}>
                              {mem?.batchName || "Unassigned"}
                            </span>
                          </td>
                          <td>{tracking.sessionTitle}</td>
                          <td>
                            <span className="interval-count warning">
                              {tracking.insideChecks} / {tracking.expectedChecks || 10} checks
                            </span>
                          </td>
                          <td>
                            <small>
                              {tracking.lastCheckAt
                                ? new Date(tracking.lastCheckAt).toLocaleTimeString([], {
                                    hour: "numeric",
                                    minute: "2-digit",
                                  })
                                : "N/A"}
                            </small>
                          </td>
                          <td>
                            <button
                              type="button"
                              className="primary-button compact approve-override-btn"
                              disabled={!canMarkAttendance}
                              onClick={() => setReviewConfirm({ open: true, tracking })}
                            >
                              <Check size={14} /> Approve Override
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={reviewPage}
                totalPages={reviewTotalPages}
                totalElements={reviewRecords.length}
                size={reviewPageSize}
                onPageChange={setReviewPage}
                onSizeChange={setReviewPageSize}
              />
            </>
          ) : (
            <EmptyState
              icon={<ShieldCheck size={36} className="text-success" />}
              title="All attendance verified"
              description="There are currently no sessions waiting for staff review or manual overrides."
            />
          )}
        </section>
      )}

      {/* TAB 3: ADMIN RAW GPS AUDIT */}
      {activeTab === "audit" && user?.role === "ADMIN" && (
        <section className="panel location-audit-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Authoritative Telemetry Records</p>
              <h3>Periodic GPS Location Audit ({locationAudit.length})</h3>
            </div>
          </div>

          {locationAudit.length ? (
            <>
              <div className="table-card">
                <table>
                  <thead>
                    <tr>
                      <th>Member</th>
                      <th>Session</th>
                      <th>Slot</th>
                      <th>Timestamp</th>
                      <th>GPS Coordinates</th>
                      <th>Reported Accuracy</th>
                      <th>Center Distance</th>
                      <th>Result</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagedAuditRecords.map((check) => (
                      <tr key={`${check.trackingId}-${check.checkNumber}`}>
                        <td>{check.memberName}</td>
                        <td>{check.sessionTitle}</td>
                        <td>#{check.checkNumber + 1}</td>
                        <td>
                          <small>{new Date(check.checkedAt).toLocaleTimeString()}</small>
                        </td>
                        <td>
                          <small>
                            {Number(check.latitude).toFixed(5)}, {Number(check.longitude).toFixed(5)}
                          </small>
                        </td>
                        <td>
                          <span className={`accuracy-pill ${check.accuracyMeters > 50 ? "poor" : "good"}`}>
                            {check.accuracyMeters != null ? `±${Math.round(check.accuracyMeters)}m` : "N/A"}
                          </span>
                        </td>
                        <td>{Number(check.distanceMeters).toFixed(1)} m</td>
                        <td>
                          <span className={`badge ${check.insideGeofence ? "badge-success" : "badge-danger"}`}>
                            {check.insideGeofence ? "INSIDE" : "OUTSIDE"}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={auditPage}
                totalPages={auditTotalPages}
                totalElements={locationAudit.length}
                size={auditPageSize}
                onPageChange={setAuditPage}
                onSizeChange={setAuditPageSize}
              />
            </>
          ) : (
            <EmptyState
              icon={<Compass size={36} />}
              title="No GPS checks recorded"
              description="Raw location checks will be logged here as members attend active sessions."
            />
          )}
        </section>
      )}

      {/* FAST MANUAL CHECK-IN MODAL FOR STAFF & ADMINS */}
      {showFastCheckIn && (
        <div
          className="modal-backdrop"
          onClick={() => setShowFastCheckIn(false)}
          role="presentation"
        >
          <div
            className="modal-card fast-checkin-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="fast-checkin-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="modal-title-wrap">
                <UserCheck size={20} className="modal-title-icon" />
                <h3 id="fast-checkin-title">Fast Manual Attendance</h3>
              </div>
              <button
                type="button"
                className="modal-close"
                onClick={() => setShowFastCheckIn(false)}
                aria-label="Close fast check-in"
              >
                <X size={18} />
              </button>
            </div>

            <div className="fast-checkin-controls">
              {/* Target Session Selection */}
              <label className="fast-session-label">
                <span>Select Session:</span>
                <select
                  value={fastSessionId}
                  onChange={(e) => setFastSessionId(e.target.value)}
                  className="fast-session-select"
                >
                  {sessions.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.title}
                    </option>
                  ))}
                </select>
              </label>

              {/* Instant Search Bar */}
              <div className="fast-search-row">
                <div className="search-wrap flex-1">
                  <Search size={16} />
                  <input
                    type="text"
                    placeholder="Search member by name or student ID..."
                    value={fastMemberSearch}
                    onChange={(e) => setFastMemberSearch(e.target.value)}
                    autoFocus
                  />
                </div>

                <select
                  className="filter-select compact"
                  value={fastBatchFilter}
                  onChange={(e) => setFastBatchFilter(e.target.value)}
                  aria-label="Filter members by batch"
                >
                  <option value="all">All Batches</option>
                  <option value="unassigned">Unassigned</option>
                  {batches.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Fast Member List */}
            <div className="fast-members-list">
              {fastMembersList.length ? (
                fastMembersList.map((member) => (
                  <div key={member.id} className="fast-member-item">
                    <div className="fast-member-info">
                      <span className="fast-member-name">{member.name}</span>
                      <div className="fast-member-tags">
                        {member.studentId && (
                          <span className="fast-student-id">{member.studentId}</span>
                        )}
                        <span className={`batch-pill compact ${member.batchName ? "assigned" : "unassigned"}`}>
                          {member.batchName || "Unassigned"}
                        </span>
                      </div>
                    </div>

                    <div className="fast-member-action">
                      {member.alreadyMarked ? (
                        <span className="badge badge-success">
                          <CheckCircle2 size={13} /> Present
                        </span>
                      ) : (
                        <button
                          type="button"
                          className="primary-button compact fast-mark-btn"
                          disabled={markingMemberId === member.id}
                          onClick={() => handleQuickMarkPresent(member)}
                        >
                          <Check size={14} />
                          <span>
                            {markingMemberId === member.id ? "Marking..." : "Mark Present"}
                          </span>
                        </button>
                      )}
                    </div>
                  </div>
                ))
              ) : (
                <div className="fast-empty">No members found matching &quot;{fastMemberSearch}&quot;</div>
              )}
            </div>

            <div className="modal-actions">
              <button
                type="button"
                className="secondary-button"
                onClick={() => setShowFastCheckIn(false)}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {/* STANDARD CRUD MODAL */}
      {showModal && (
        <div className="modal-backdrop" onClick={closeModal} role="presentation">
          <div
            className="modal-card large"
            role="dialog"
            aria-modal="true"
            aria-labelledby="attendance-modal-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h3 id="attendance-modal-title">
                {editingRecord ? "Edit Attendance Record" : "Manual Attendance Entry"}
              </h3>
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
              <label>
                <span>Select Session</span>
                <select
                  value={form.sessionId}
                  onChange={(event) => {
                    const selected = sessions.find((s) => String(s.id) === String(event.target.value));
                    setForm((curr) => ({
                      ...curr,
                      sessionId: event.target.value,
                      method: selected?.locationEnabled ? "GEOFENCE" : "MANUAL",
                    }));
                  }}
                  required
                >
                  <option value="">-- Choose session --</option>
                  {sessions.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.title}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                <span>Select Member</span>
                <select
                  value={form.memberId}
                  onChange={(event) => setForm((curr) => ({ ...curr, memberId: event.target.value }))}
                  required
                >
                  <option value="">-- Choose member --</option>
                  {members.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} {m.studentId ? `(${m.studentId})` : ""}
                    </option>
                  ))}
                </select>
              </label>

              {editingRecord && (
                <label>
                  <span>Attendance Timestamp</span>
                  <input
                    type="datetime-local"
                    value={form.markedAt}
                    onChange={(event) => setForm((curr) => ({ ...curr, markedAt: event.target.value }))}
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
                    onChange={(e) => setForm((curr) => ({ ...curr, method: e.target.value }))}
                    disabled={!isSelectedSessionGeofence}
                  />
                  <span>Geofence (GPS Verified)</span>
                </label>

                <label className="radio-option">
                  <input
                    type="radio"
                    name="method"
                    value="MANUAL"
                    checked={form.method === "MANUAL"}
                    onChange={(e) => setForm((curr) => ({ ...curr, method: e.target.value }))}
                  />
                  <span>Manual Staff Override</span>
                </label>
              </div>

              {form.method === "GEOFENCE" && isSelectedSessionGeofence && (
                <>
                  <label>
                    <span>Latitude</span>
                    <input
                      type="number"
                      step="any"
                      value={form.latitude}
                      onChange={(e) => setForm((curr) => ({ ...curr, latitude: e.target.value }))}
                      required
                    />
                  </label>

                  <label>
                    <span>Longitude</span>
                    <input
                      type="number"
                      step="any"
                      value={form.longitude}
                      onChange={(e) => setForm((curr) => ({ ...curr, longitude: e.target.value }))}
                      required
                    />
                  </label>

                  <div className="full-width">
                    <button
                      type="button"
                      className="secondary-button full-width"
                      onClick={requestGeolocation}
                    >
                      <LocateFixed size={16} /> Capture Device Location
                    </button>
                    {geoMessage && <p className="helper-text">{geoMessage}</p>}
                  </div>
                </>
              )}

              <div className="modal-actions full-width">
                <button type="button" className="secondary-button" onClick={closeModal}>
                  Cancel
                </button>
                <button type="submit" className="primary-button">
                  {editingRecord ? "Save Changes" : "Record Attendance"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CONFIRM DELETE MODAL */}
      <ConfirmModal
        isOpen={deleteConfirm.open}
        title="Delete Attendance Record"
        message="Are you sure you want to delete this attendance record? This action will remove the verified attendance from history."
        confirmText="Delete Record"
        tone="danger"
        onConfirm={handleDeleteRecord}
        onCancel={() => setDeleteConfirm({ open: false, record: null })}
      />

      {/* CONFIRM REVIEW OVERRIDE MODAL */}
      <ConfirmModal
        isOpen={reviewConfirm.open}
        title="Approve Manual Attendance Override"
        message={`Record a manual attendance override for ${reviewConfirm.tracking?.memberName || "this member"} in session "${reviewConfirm.tracking?.sessionTitle || ""}"? This will finalize their attendance as Present.`}
        confirmText="Approve Override"
        tone="primary"
        onConfirm={handleApproveReview}
        onCancel={() => setReviewConfirm({ open: false, tracking: null })}
      />
    </div>
  );
}
