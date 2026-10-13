import { useCallback, useEffect, useMemo, useState } from "react";
import { Check, Plus, Search, UserCheck, Users, X } from "lucide-react";
import ConfirmModal from "../components/ConfirmModal";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import MemberTable from "../components/MemberTable";
import ActiveFilterBadges from "../components/ActiveFilterBadges";
import Pagination from "../components/Pagination";
import { useDebounce } from "../hooks/useDebounce";
import batchService from "../services/batchService";
import memberService from "../services/memberService";

const initialMemberForm = { name: "", email: "", password: "", studentId: "", batchId: "" };

export default function Members({ user, showToast }) {
  const [activeTab, setActiveTab] = useState("members"); // "members" or "queue"
  const [members, setMembers] = useState([]);
  const [signups, setSignups] = useState([]);
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(true);

  // Search & Filter state for Members
  const [memberSearch, setMemberSearch] = useState("");
  const debouncedMemberSearch = useDebounce(memberSearch, 350);
  const [memberBatchFilter, setMemberBatchFilter] = useState("all");
  const [memberRoleFilter, setMemberRoleFilter] = useState("all");
  const [memberPage, setMemberPage] = useState(0);
  const [memberPageSize, setMemberPageSize] = useState(10);
  const [memberTotalPages, setMemberTotalPages] = useState(0);
  const [memberTotalElements, setMemberTotalElements] = useState(0);

  // Search & Filter state for Signup Queue
  const [queueSearch, setQueueSearch] = useState("");
  const debouncedQueueSearch = useDebounce(queueSearch, 350);
  const [queueStatusFilter, setQueueStatusFilter] = useState("PENDING");
  const [queueBatchFilter, setQueueBatchFilter] = useState("all");
  const [queueFromDate, setQueueFromDate] = useState("");
  const [queueToDate, setQueueToDate] = useState("");
  const [queuePage, setQueuePage] = useState(0);
  const [queuePageSize, setQueuePageSize] = useState(10);
  const [queueTotalPages, setQueueTotalPages] = useState(0);
  const [queueTotalElements, setQueueTotalElements] = useState(0);

  // Approval state: store selected batch per signup id in queue
  const [selectedBatches, setSelectedBatches] = useState({});
  const [reviewingSignupId, setReviewingSignupId] = useState(null);

  // Rejection modal state
  const [rejectionModal, setRejectionModal] = useState({ open: false, signup: null, reason: "" });

  // Delete member confirm modal state
  const [deleteConfirm, setDeleteConfirm] = useState({ open: false, member: null });

  // Member CRUD modal state
  const [showMemberModal, setShowMemberModal] = useState(false);
  const [editingMember, setEditingMember] = useState(null);
  const [memberForm, setMemberForm] = useState(initialMemberForm);

  const canManageMembers = user?.role === "ADMIN";
  const canReviewSignups = user?.role === "ADMIN" || user?.role === "CLUB_USER";

  const fetchBatches = useCallback(async () => {
    try {
      const batchList = await batchService.listBatches();
      setBatches(batchList || []);
    } catch {
      // Non-critical if batches fail
    }
  }, []);

  const fetchMembersList = useCallback(
    async (silent = false) => {
      try {
        if (!silent) setLoading(true);
        const res = await memberService.listMembers({
          search: debouncedMemberSearch.trim() || undefined,
          batchId: memberBatchFilter !== "all" && memberBatchFilter !== "unassigned" ? memberBatchFilter : undefined,
          role: memberRoleFilter !== "all" ? memberRoleFilter : undefined,
          page: memberPage,
          size: memberPageSize,
        });

        if (res && res.content !== undefined) {
          setMembers(res.content);
          setMemberTotalPages(res.totalPages);
          setMemberTotalElements(res.totalElements);
        } else if (Array.isArray(res)) {
          setMembers(res);
          setMemberTotalPages(Math.ceil(res.length / memberPageSize));
          setMemberTotalElements(res.length);
        }
      } catch (error) {
        if (!silent) showToast(error.message || "Unable to load members.", "error");
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [debouncedMemberSearch, memberBatchFilter, memberRoleFilter, memberPage, memberPageSize, showToast],
  );

  const fetchSignupsList = useCallback(
    async (silent = false) => {
      if (!canReviewSignups) return;
      try {
        if (!silent) setLoading(true);
        const res = await memberService.listSignups({
          status: queueStatusFilter !== "ALL" ? queueStatusFilter : undefined,
          batchId: queueBatchFilter !== "all" && queueBatchFilter !== "unassigned" ? queueBatchFilter : undefined,
          search: debouncedQueueSearch.trim() || undefined,
          fromDate: queueFromDate || undefined,
          toDate: queueToDate || undefined,
          page: queuePage,
          size: queuePageSize,
        });

        if (res && res.content !== undefined) {
          setSignups(res.content);
          setQueueTotalPages(res.totalPages);
          setQueueTotalElements(res.totalElements);
        } else if (Array.isArray(res)) {
          setSignups(res);
          setQueueTotalPages(Math.ceil(res.length / queuePageSize));
          setQueueTotalElements(res.length);
        }
      } catch (error) {
        if (!silent) showToast(error.message || "Unable to load signups.", "error");
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [
      canReviewSignups,
      queueStatusFilter,
      queueBatchFilter,
      debouncedQueueSearch,
      queueFromDate,
      queueToDate,
      queuePage,
      queuePageSize,
      showToast,
    ],
  );

  const fetchMembers = useCallback(() => {
    fetchMembersList();
    if (canReviewSignups) {
      fetchSignupsList();
    }
  }, [fetchMembersList, fetchSignupsList, canReviewSignups]);

  useEffect(() => {
    fetchBatches();
  }, [fetchBatches]);

  useEffect(() => {
    setMemberPage(0);
  }, [debouncedMemberSearch, memberBatchFilter, memberRoleFilter]);

  useEffect(() => {
    setQueuePage(0);
  }, [debouncedQueueSearch, queueStatusFilter, queueBatchFilter, queueFromDate, queueToDate]);

  useEffect(() => {
    if (activeTab === "members") {
      fetchMembersList();
    } else if (activeTab === "queue") {
      fetchSignupsList();
    }
  }, [activeTab, fetchMembersList, fetchSignupsList]);

  const activeBatches = useMemo(() => {
    return batches.filter((b) => b.status === "ACTIVE");
  }, [batches]);

  const filteredMembers = members;
  const filteredSignups = signups;

  const activeMemberFilters = useMemo(() => {
    const list = [];
    if (debouncedMemberSearch.trim()) {
      list.push({
        key: "search",
        label: "Search",
        value: debouncedMemberSearch.trim(),
        onRemove: () => setMemberSearch(""),
      });
    }
    if (memberBatchFilter !== "all") {
      const b = batches.find((item) => String(item.id) === String(memberBatchFilter));
      list.push({
        key: "batch",
        label: "Batch",
        value: memberBatchFilter === "unassigned" ? "Unassigned" : b?.name || `Batch #${memberBatchFilter}`,
        onRemove: () => setMemberBatchFilter("all"),
      });
    }
    if (memberRoleFilter !== "all") {
      list.push({
        key: "role",
        label: "Role",
        value: memberRoleFilter,
        onRemove: () => setMemberRoleFilter("all"),
      });
    }
    return list;
  }, [debouncedMemberSearch, memberBatchFilter, memberRoleFilter, batches]);

  const clearAllMemberFilters = useCallback(() => {
    setMemberSearch("");
    setMemberBatchFilter("all");
    setMemberRoleFilter("all");
    setMemberPage(0);
  }, []);

  const activeQueueFilters = useMemo(() => {
    const list = [];
    if (debouncedQueueSearch.trim()) {
      list.push({
        key: "search",
        label: "Search",
        value: debouncedQueueSearch.trim(),
        onRemove: () => setQueueSearch(""),
      });
    }
    if (queueStatusFilter !== "ALL") {
      list.push({
        key: "status",
        label: "Status",
        value: queueStatusFilter,
        onRemove: () => setQueueStatusFilter("ALL"),
      });
    }
    if (queueBatchFilter !== "all") {
      const b = batches.find((item) => String(item.id) === String(queueBatchFilter));
      list.push({
        key: "batch",
        label: "Preference",
        value: queueBatchFilter === "unassigned" ? "No Preference" : b?.name || `Batch #${queueBatchFilter}`,
        onRemove: () => setQueueBatchFilter("all"),
      });
    }
    if (queueFromDate) {
      list.push({
        key: "fromDate",
        label: "Submitted From",
        value: queueFromDate,
        onRemove: () => setQueueFromDate(""),
      });
    }
    if (queueToDate) {
      list.push({
        key: "toDate",
        label: "Submitted To",
        value: queueToDate,
        onRemove: () => setQueueToDate(""),
      });
    }
    return list;
  }, [debouncedQueueSearch, queueStatusFilter, queueBatchFilter, queueFromDate, queueToDate, batches]);

  const clearAllQueueFilters = useCallback(() => {
    setQueueSearch("");
    setQueueStatusFilter("ALL");
    setQueueBatchFilter("all");
    setQueueFromDate("");
    setQueueToDate("");
    setQueuePage(0);
  }, []);

  const pendingCount = useMemo(() => {
    return signups.filter((s) => s.status === "PENDING").length;
  }, [signups]);

  // Member CRUD handlers
  const openCreateModal = () => {
    setEditingMember(null);
    setMemberForm(initialMemberForm);
    setShowMemberModal(true);
  };

  const openEditModal = (member) => {
    setEditingMember(member);
    setMemberForm({
      name: member.name,
      email: member.email,
      password: "",
      studentId: member.studentId || "",
      batchId: member.batchId ? String(member.batchId) : "",
    });
    setShowMemberModal(true);
  };

  const closeMemberModal = () => {
    setShowMemberModal(false);
    setEditingMember(null);
    setMemberForm(initialMemberForm);
  };

  const handleMemberSubmit = async (event) => {
    event.preventDefault();
    try {
      const payload = {
        name: memberForm.name,
        email: memberForm.email,
        studentId: memberForm.studentId.trim() || null,
        batchId: memberForm.batchId ? Number(memberForm.batchId) : null,
      };
      if (memberForm.password) {
        payload.password = memberForm.password;
      }

      if (editingMember) {
        await memberService.updateMember(editingMember.id, payload);
        showToast("Member updated successfully.", "success");
      } else {
        await memberService.createMember(payload);
        showToast("Member created successfully.", "success");
      }

      closeMemberModal();
      fetchMembers();
    } catch (error) {
      showToast(error.message || "Unable to save member.", "error");
    }
  };

  const handleConfirmDeleteMember = async () => {
    const member = deleteConfirm.member;
    if (!member) return;

    try {
      await memberService.deleteMember(member.id);
      showToast(`Member "${member.name}" deleted. Historical attendance records remain preserved.`, "success");
      setDeleteConfirm({ open: false, member: null });
      fetchMembers();
    } catch (error) {
      showToast(error.message || "Unable to delete member.", "error");
    }
  };

  // Review & Approval handlers
  const handleApprove = async (signup) => {
    const assignedBatchId =
      selectedBatches[signup.id] !== undefined
        ? selectedBatches[signup.id]
        : signup.requestedBatchId || (activeBatches[0]?.id ?? null);

    setReviewingSignupId(signup.id);
    try {
      await memberService.approveSignup(signup.id, assignedBatchId ? Number(assignedBatchId) : null);
      showToast(`${signup.name} was approved as an active member.`, "success");
      fetchMembers();
    } catch (error) {
      showToast(error.message || "Unable to approve registration.", "error");
    } finally {
      setReviewingSignupId(null);
    }
  };

  const openRejectModal = (signup) => {
    setRejectionModal({ open: true, signup, reason: "" });
  };

  const closeRejectModal = () => {
    setRejectionModal({ open: false, signup: null, reason: "" });
  };

  const handleConfirmReject = async () => {
    const { signup, reason } = rejectionModal;
    if (!signup) return;

    setReviewingSignupId(signup.id);
    try {
      await memberService.rejectSignup(signup.id, reason.trim() || null);
      showToast(`Registration request for ${signup.name} was rejected.`, "success");
      closeRejectModal();
      fetchMembers();
    } catch (error) {
      showToast(error.message || "Unable to reject registration.", "error");
    } finally {
      setReviewingSignupId(null);
    }
  };

  return (
    <div className="page-shell members-page">
      <div className="page-header">
        <div>
          <p className="eyebrow">Roster & Onboarding</p>
          <h1>Club Membership</h1>
        </div>

        {canManageMembers && activeTab === "members" && (
          <button type="button" className="primary-button" onClick={openCreateModal}>
            <Plus size={17} /> Add Member
          </button>
        )}
      </div>

      {/* Tabs navigation */}
      <div className="member-view-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "members"}
          className={activeTab === "members" ? "active" : ""}
          onClick={() => setActiveTab("members")}
        >
          <Users size={16} />
          <span>Club Members</span>
          <span className="tab-pill-badge neutral">{memberTotalElements}</span>
        </button>

        {canReviewSignups && (
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "queue"}
            className={activeTab === "queue" ? "active" : ""}
            onClick={() => setActiveTab("queue")}
          >
            <UserCheck size={16} />
            <span>Registration Queue</span>
            {pendingCount > 0 ? (
              <span className="tab-pill-badge warning">{pendingCount}</span>
            ) : (
              <span className="tab-pill-badge neutral">{queueTotalElements}</span>
            )}
          </button>
        )}
      </div>

      {loading ? (
        <Loading message="Loading member roster..." />
      ) : activeTab === "members" ? (
        <>
          {/* Members filters */}
          <div className="filter-bar">
            <div className="search-wrap">
              <Search size={16} />
              <input
                type="text"
                placeholder="Search by name, email, or student ID..."
                value={memberSearch}
                onChange={(e) => setMemberSearch(e.target.value)}
              />
            </div>

            <div className="filter-controls">
              <select
                className="filter-select"
                value={memberBatchFilter}
                onChange={(e) => setMemberBatchFilter(e.target.value)}
                aria-label="Filter members by batch"
              >
                <option value="all">All Batches</option>
                <option value="unassigned">Unassigned</option>
                {batches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name} {b.status === "ARCHIVED" ? "(Archived)" : ""}
                  </option>
                ))}
              </select>

              <select
                className="filter-select"
                value={memberRoleFilter}
                onChange={(e) => setMemberRoleFilter(e.target.value)}
                aria-label="Filter members by role"
              >
                <option value="all">All Roles</option>
                <option value="MEMBER">Member</option>
                <option value="CLUB_USER">Club Staff</option>
                <option value="ADMIN">Admin</option>
              </select>
            </div>
          </div>

          <ActiveFilterBadges
            activeFilters={activeMemberFilters}
            onClearAll={clearAllMemberFilters}
            totalCount={memberTotalElements}
          />

          {filteredMembers.length ? (
            <>
              <MemberTable
                members={filteredMembers}
                canManage={canManageMembers}
                onEdit={openEditModal}
                onDelete={(m) => setDeleteConfirm({ open: true, member: m })}
              />

              <Pagination
                page={memberPage}
                totalPages={memberTotalPages}
                totalElements={memberTotalElements}
                size={memberPageSize}
                onPageChange={setMemberPage}
                onSizeChange={setMemberPageSize}
              />
            </>
          ) : (
            <EmptyState
              title="No members found"
              description={
                activeMemberFilters.length > 0
                  ? "No members match the selected filters."
                  : "No club members are available yet."
              }
              actionLabel={canManageMembers ? "Add First Member" : undefined}
              onAction={canManageMembers ? openCreateModal : undefined}
            />
          )}
        </>
      ) : (
        /* Registration Queue Tab */
        <section className="registration-queue-section">
          <div className="filter-bar">
            <div className="search-wrap">
              <Search size={16} />
              <input
                type="text"
                placeholder="Search signups by name, email, or student ID..."
                value={queueSearch}
                onChange={(e) => setQueueSearch(e.target.value)}
              />
            </div>

            <div className="filter-controls">
              <select
                className="filter-select"
                value={queueStatusFilter}
                onChange={(e) => setQueueStatusFilter(e.target.value)}
                aria-label="Filter by registration status"
              >
                <option value="PENDING">Pending Review</option>
                <option value="APPROVED">Approved</option>
                <option value="REJECTED">Rejected</option>
                <option value="ALL">All Statuses</option>
              </select>

              <select
                className="filter-select"
                value={queueBatchFilter}
                onChange={(e) => setQueueBatchFilter(e.target.value)}
                aria-label="Filter by requested batch"
              >
                <option value="all">All Requested Batches</option>
                <option value="unassigned">No Preference</option>
                {batches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>

              <div className="date-filter-inline">
                <label className="date-label">Submitted</label>
                <input
                  type="date"
                  className="filter-date-input"
                  value={queueFromDate}
                  onChange={(e) => setQueueFromDate(e.target.value)}
                  aria-label="Submitted from date"
                  title="Submitted on or after"
                />
                <span className="date-sep">to</span>
                <input
                  type="date"
                  className="filter-date-input"
                  value={queueToDate}
                  onChange={(e) => setQueueToDate(e.target.value)}
                  aria-label="Submitted to date"
                  title="Submitted on or before"
                />
              </div>
            </div>
          </div>

          <ActiveFilterBadges
            activeFilters={activeQueueFilters}
            onClearAll={clearAllQueueFilters}
            totalCount={queueTotalElements}
          />

          {filteredSignups.length ? (
            <>
              <div className="table-card">
                <table>
                  <thead>
                    <tr>
                      <th>Applicant</th>
                      <th>Email</th>
                      <th>Student ID</th>
                      <th>Requested Preference</th>
                      <th>Submitted</th>
                      <th>Status</th>
                      <th>Batch Assignment & Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSignups.map((signup) => {
                      const currentAssignedBatch =
                        selectedBatches[signup.id] !== undefined
                          ? selectedBatches[signup.id]
                          : signup.requestedBatchId || (activeBatches[0]?.id ? String(activeBatches[0].id) : "");

                      return (
                        <tr key={signup.id}>
                          <td>
                            <strong>{signup.name}</strong>
                          </td>
                          <td>{signup.email}</td>
                          <td>{signup.studentId || "—"}</td>
                          <td>
                            <span className={`batch-pill ${signup.requestedBatchName ? "assigned" : "unassigned"}`}>
                              {signup.requestedBatchName || "None specified"}
                            </span>
                          </td>
                          <td>{signup.createdAt ? new Date(signup.createdAt).toLocaleDateString() : "—"}</td>
                          <td>
                            <span
                              className={`status-badge ${
                                signup.status === "APPROVED"
                                  ? "status-active"
                                  : signup.status === "REJECTED"
                                  ? "status-archived"
                                  : "status-pending"
                              }`}
                            >
                              {signup.status}
                            </span>
                          </td>
                          <td>
                            {signup.status === "PENDING" ? (
                              <div className="queue-action-group">
                                <select
                                  className="queue-batch-select"
                                  value={currentAssignedBatch}
                                  onChange={(e) =>
                                    setSelectedBatches((prev) => ({
                                      ...prev,
                                      [signup.id]: e.target.value,
                                    }))
                                  }
                                  title="Select batch to assign upon approval"
                                >
                                  {activeBatches.map((b) => (
                                    <option key={b.id} value={b.id}>
                                      Assign: {b.name}
                                    </option>
                                  ))}
                                  <option value="">No Batch</option>
                                </select>

                                <button
                                  type="button"
                                  className="primary-button compact approve"
                                  onClick={() => handleApprove(signup)}
                                  disabled={reviewingSignupId === signup.id}
                                >
                                  <Check size={14} /> Approve
                                </button>

                                <button
                                  type="button"
                                  className="danger-button compact"
                                  onClick={() => openRejectModal(signup)}
                                  disabled={reviewingSignupId === signup.id}
                                >
                                  Reject
                                </button>
                              </div>
                            ) : signup.status === "REJECTED" ? (
                              <div className="rejection-info-text">
                                <em>Reason:</em> {signup.rejectionReason || "No specific reason provided."}
                              </div>
                            ) : (
                              <div className="approved-info-text">
                                <Check size={14} /> Enrolled in club
                              </div>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <Pagination
                page={queuePage}
                totalPages={queueTotalPages}
                totalElements={queueTotalElements}
                size={queuePageSize}
                onPageChange={setQueuePage}
                onSizeChange={setQueuePageSize}
              />
            </>
          ) : (
            <EmptyState
              icon={<UserCheck size={36} />}
              title="No registration requests"
              description={
                activeQueueFilters.length > 0
                  ? "No registration requests match the current filter selection."
                  : queueStatusFilter === "PENDING"
                  ? "There are no pending membership requests waiting for staff review."
                  : "No registration requests found."
              }
            />
          )}
        </section>
      )}

      {/* Add / Edit Member Modal */}
      {showMemberModal && (
        <div className="modal-backdrop" onClick={closeMemberModal} role="presentation">
          <div
            className="modal-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="member-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h3 id="member-modal-title">{editingMember ? "Edit Member" : "Add Member"}</h3>
              <button
                type="button"
                className="modal-close"
                onClick={closeMemberModal}
                aria-label="Close dialog"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleMemberSubmit} className="form-grid">
              <label>
                <span>Full Name</span>
                <input
                  type="text"
                  value={memberForm.name}
                  onChange={(e) => setMemberForm((c) => ({ ...c, name: e.target.value }))}
                  required
                />
              </label>

              <label>
                <span>Email Address</span>
                <input
                  type="email"
                  value={memberForm.email}
                  onChange={(e) => setMemberForm((c) => ({ ...c, email: e.target.value }))}
                  required
                />
              </label>

              <label>
                <span>Student / Club ID (Optional)</span>
                <input
                  type="text"
                  placeholder="e.g. OK-2026-042"
                  value={memberForm.studentId}
                  onChange={(e) => setMemberForm((c) => ({ ...c, studentId: e.target.value }))}
                />
              </label>

              <label>
                <span>Batch / Generation</span>
                <select
                  value={memberForm.batchId}
                  onChange={(e) => setMemberForm((c) => ({ ...c, batchId: e.target.value }))}
                >
                  <option value="">Unassigned</option>
                  {activeBatches.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.name}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                <span>Password</span>
                <input
                  type="password"
                  value={memberForm.password}
                  onChange={(e) => setMemberForm((c) => ({ ...c, password: e.target.value }))}
                  placeholder={editingMember ? "Leave blank to keep existing password" : "Enter member password"}
                  required={!editingMember}
                />
              </label>

              <div className="modal-actions">
                <button type="button" className="secondary-button" onClick={closeMemberModal}>
                  Cancel
                </button>
                <button type="submit" className="primary-button">
                  {editingMember ? "Update Member" : "Create Member"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Rejection Reason Modal */}
      {rejectionModal.open && (
        <div className="modal-backdrop" onClick={closeRejectModal} role="presentation">
          <div
            className="modal-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="reject-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h3 id="reject-modal-title">Decline Registration</h3>
              <button
                type="button"
                className="modal-close"
                onClick={closeRejectModal}
                aria-label="Close dialog"
              >
                <X size={18} />
              </button>
            </div>

            <div className="modal-body-copy">
              <p>
                Decline registration request for <strong>{rejectionModal.signup?.name}</strong> (
                {rejectionModal.signup?.email})?
              </p>
              <label className="rejection-reason-label">
                <span>Reason for rejection (Optional)</span>
                <textarea
                  rows={3}
                  placeholder="e.g. Incomplete verification, invalid credentials, not currently enrolled..."
                  value={rejectionModal.reason}
                  onChange={(e) => setRejectionModal((c) => ({ ...c, reason: e.target.value }))}
                  maxLength={500}
                />
              </label>
            </div>

            <div className="modal-actions">
              <button type="button" className="secondary-button" onClick={closeRejectModal}>
                Cancel
              </button>
              <button type="button" className="danger-button" onClick={handleConfirmReject}>
                Confirm Decline
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm Delete Member Modal */}
      <ConfirmModal
        isOpen={deleteConfirm.open}
        title="Delete Member"
        message={`Delete member "${deleteConfirm.member?.name}"? Historical attendance records remain preserved.`}
        confirmText="Delete Member"
        tone="danger"
        onConfirm={handleConfirmDeleteMember}
        onCancel={() => setDeleteConfirm({ open: false, member: null })}
      />
    </div>
  );
}
