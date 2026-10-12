import { useCallback, useEffect, useMemo, useState } from "react";
import { Archive, CheckCircle, Edit2, Layers, Plus, RotateCcw, Search, Users, X } from "lucide-react";
import ConfirmModal from "../components/ConfirmModal";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import StatCard from "../components/StatCard";
import ActiveFilterBadges from "../components/ActiveFilterBadges";
import Pagination from "../components/Pagination";
import { useDebounce } from "../hooks/useDebounce";
import batchService from "../services/batchService";

const initialForm = { name: "", status: "ACTIVE" };

export default function Batches({ user, showToast }) {
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [overviewBatches, setOverviewBatches] = useState([]);

  // Search & Filter state
  const [searchTerm, setSearchTerm] = useState("");
  const debouncedSearchTerm = useDebounce(searchTerm, 350);
  const [statusFilter, setStatusFilter] = useState("all");

  // Pagination state
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [showModal, setShowModal] = useState(false);
  const [editingBatch, setEditingBatch] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [submitting, setSubmitting] = useState(false);

  // Archive / activate confirm modal state
  const [archiveConfirm, setArchiveConfirm] = useState({ open: false, batch: null });

  const canManage = user?.role === "ADMIN";

  const fetchOverviewBatches = useCallback(async () => {
    try {
      const data = await batchService.listBatches();
      setOverviewBatches(Array.isArray(data) ? data : data?.content || []);
    } catch {
      // non-blocking
    }
  }, []);

  const fetchBatches = useCallback(async () => {
    try {
      setLoading(true);
      const params = {
        search: debouncedSearchTerm.trim() || undefined,
        status: statusFilter !== "all" ? statusFilter : undefined,
        page,
        size: pageSize,
      };

      const result = await batchService.listBatches(params);
      if (result && Array.isArray(result.content)) {
        setBatches(result.content);
        setTotalPages(result.totalPages ?? 0);
        setTotalElements(result.totalElements ?? 0);
      } else if (Array.isArray(result)) {
        setBatches(result);
        setTotalPages(1);
        setTotalElements(result.length);
      } else {
        setBatches([]);
        setTotalPages(0);
        setTotalElements(0);
      }
    } catch (error) {
      showToast(error.message || "Failed to load batches.", "error");
    } finally {
      setLoading(false);
    }
  }, [debouncedSearchTerm, statusFilter, page, pageSize, showToast]);

  useEffect(() => {
    fetchOverviewBatches();
  }, [fetchOverviewBatches]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearchTerm, statusFilter]);

  useEffect(() => {
    fetchBatches();
  }, [fetchBatches]);

  const activeFilters = useMemo(() => {
    const list = [];
    if (debouncedSearchTerm.trim()) {
      list.push({
        key: "search",
        label: "Generation",
        value: debouncedSearchTerm.trim(),
        onRemove: () => setSearchTerm(""),
      });
    }
    if (statusFilter !== "all") {
      list.push({
        key: "status",
        label: "Status",
        value: statusFilter === "ACTIVE" ? "Active" : "Archived",
        onRemove: () => setStatusFilter("all"),
      });
    }
    return list;
  }, [debouncedSearchTerm, statusFilter]);

  const clearAllFilters = useCallback(() => {
    setSearchTerm("");
    setStatusFilter("all");
    setPage(0);
  }, []);

  const stats = useMemo(() => {
    const list = overviewBatches.length > 0 ? overviewBatches : batches;
    const total = overviewBatches.length > 0 ? overviewBatches.length : totalElements;
    const active = list.filter((b) => b.status === "ACTIVE").length;
    const archived = list.filter((b) => b.status === "ARCHIVED").length;
    const totalMembers = list.reduce((sum, b) => sum + (Number(b.memberCount) || 0), 0);
    return { total, active, archived, totalMembers };
  }, [overviewBatches, batches, totalElements]);

  const openCreateModal = () => {
    setEditingBatch(null);
    setForm(initialForm);
    setShowModal(true);
  };

  const openEditModal = (batch) => {
    setEditingBatch(batch);
    setForm({ name: batch.name, status: batch.status });
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingBatch(null);
    setForm(initialForm);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.name.trim()) {
      showToast("Batch name cannot be empty.", "error");
      return;
    }

    try {
      setSubmitting(true);
      if (editingBatch) {
        await batchService.updateBatch(editingBatch.id, form);
        showToast(`Batch "${form.name}" updated successfully.`, "success");
      } else {
        await batchService.createBatch(form);
        showToast(`Batch "${form.name}" created successfully.`, "success");
      }
      closeModal();
      fetchBatches();
      fetchOverviewBatches();
    } catch (error) {
      showToast(error.message || "Unable to save batch.", "error");
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmToggleArchive = async () => {
    const batch = archiveConfirm.batch;
    if (!batch) return;

    const isArchived = batch.status === "ARCHIVED";
    const actionLabel = isArchived ? "activate" : "archive";

    try {
      if (isArchived) {
        await batchService.activateBatch(batch.id);
        showToast(`Batch "${batch.name}" activated.`, "success");
      } else {
        await batchService.archiveBatch(batch.id);
        showToast(`Batch "${batch.name}" archived.`, "success");
      }
      setArchiveConfirm({ open: false, batch: null });
      fetchBatches();
      fetchOverviewBatches();
    } catch (error) {
      showToast(error.message || `Unable to ${actionLabel} batch.`, "error");
    }
  };

  return (
    <div className="page-shell batches-page">
      <div className="page-header">
        <div>
          <p className="eyebrow">Cohort management</p>
          <h1>Batches & Generations</h1>
        </div>

        {canManage && (
          <button type="button" className="primary-button" onClick={openCreateModal}>
            <Plus size={17} /> Add Batch
          </button>
        )}
      </div>

      {/* KPI Stats Row */}
      <div className="stats-grid">
        <StatCard
          icon={<Layers size={22} />}
          label="Total Cohorts"
          value={stats.total}
          subtext="Configured batches"
          tone="green"
        />
        <StatCard
          icon={<CheckCircle size={22} />}
          label="Active Generations"
          value={stats.active}
          subtext="Accepting check-ins"
          tone="lime"
        />
        <StatCard
          icon={<Archive size={22} />}
          label="Archived"
          value={stats.archived}
          subtext="Historical records intact"
          tone="default"
        />
        <StatCard
          icon={<Users size={22} />}
          label="Enrolled Members"
          value={stats.totalMembers}
          subtext="Assigned across batches"
          tone="blue"
        />
      </div>

      <div className="filter-bar">
        <div className="search-wrap">
          <Search size={16} />
          <input
            type="text"
            placeholder="Search batches by name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div className="filter-controls">
          <select
            className="filter-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            aria-label="Filter by status"
          >
            <option value="all">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </div>
      </div>

      <ActiveFilterBadges
        activeFilters={activeFilters}
        onClearAll={clearAllFilters}
        totalCount={totalElements}
      />

      {loading ? (
        <Loading message="Loading cohorts..." />
      ) : batches.length ? (
        <>
          <div className="table-card">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Batch / Generation Name</th>
                  <th>Status</th>
                  <th>Enrolled Members</th>
                  <th>Created</th>
                  {canManage && <th>Actions</th>}
                </tr>
              </thead>
              <tbody>
                {batches.map((batch) => (
                  <tr key={batch.id}>
                    <td>#{batch.id}</td>
                    <td className="font-semibold">{batch.name}</td>
                    <td>
                      <span className={`status-badge ${batch.status === "ACTIVE" ? "status-active" : "status-archived"}`}>
                        {batch.status}
                      </span>
                    </td>
                    <td>
                      <span className="member-count-pill">
                        <Users size={13} /> {batch.memberCount} members
                      </span>
                    </td>
                    <td>{batch.createdAt ? new Date(batch.createdAt).toLocaleDateString() : "—"}</td>
                    {canManage && (
                      <td>
                        <div className="table-actions">
                          <button
                            type="button"
                            className="action-button secondary"
                            onClick={() => openEditModal(batch)}
                            title="Edit Batch"
                          >
                            <Edit2 size={13} /> Edit
                          </button>
                          {batch.status === "ACTIVE" ? (
                            <button
                              type="button"
                              className="action-button warning"
                              onClick={() => setArchiveConfirm({ open: true, batch })}
                              title="Archive Batch"
                            >
                              <Archive size={13} /> Archive
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="action-button approve"
                              onClick={() => setArchiveConfirm({ open: true, batch })}
                              title="Activate Batch"
                            >
                              <RotateCcw size={13} /> Activate
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

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
          icon={<Layers size={36} />}
          title="No batches found"
          description={
            activeFilters.length > 0
              ? "No cohorts match your filter criteria."
              : "No batches have been created yet."
          }
          actionLabel={canManage && activeFilters.length === 0 ? "Create First Batch" : undefined}
          onAction={canManage && activeFilters.length === 0 ? openCreateModal : undefined}
        />
      )}

      {/* Create / Edit Modal */}
      {showModal && (
        <div className="modal-backdrop" onClick={closeModal} role="presentation">
          <div
            className="modal-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="batch-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h3 id="batch-modal-title">{editingBatch ? "Edit Batch" : "Create New Batch"}</h3>
              <button
                type="button"
                className="modal-close"
                onClick={closeModal}
                aria-label="Close dialog"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="form-grid">
              <label>
                <span>Batch / Generation Name</span>
                <input
                  type="text"
                  placeholder="e.g. 5th Generation, 6th Generation"
                  value={form.name}
                  onChange={(event) => setForm((curr) => ({ ...curr, name: event.target.value }))}
                  required
                />
              </label>

              <label>
                <span>Status</span>
                <select
                  value={form.status}
                  onChange={(event) => setForm((curr) => ({ ...curr, status: event.target.value }))}
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="ARCHIVED">ARCHIVED</option>
                </select>
              </label>

              <div className="modal-actions">
                <button type="button" className="secondary-button" onClick={closeModal} disabled={submitting}>
                  Cancel
                </button>
                <button type="submit" className="primary-button" disabled={submitting}>
                  {submitting ? "Saving..." : editingBatch ? "Update Batch" : "Create Batch"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirm Archive / Activate Modal */}
      <ConfirmModal
        isOpen={archiveConfirm.open}
        title={archiveConfirm.batch?.status === "ARCHIVED" ? "Activate Batch" : "Archive Batch"}
        message={
          archiveConfirm.batch?.status === "ARCHIVED"
            ? `Activate batch "${archiveConfirm.batch?.name}"? New members will be able to enroll in this generation.`
            : `Archive batch "${archiveConfirm.batch?.name}"? Existing members and historical attendance records remain preserved, but new enrollments will be prevented.`
        }
        confirmText={archiveConfirm.batch?.status === "ARCHIVED" ? "Activate Batch" : "Archive Batch"}
        tone={archiveConfirm.batch?.status === "ARCHIVED" ? "primary" : "danger"}
        onConfirm={handleConfirmToggleArchive}
        onCancel={() => setArchiveConfirm({ open: false, batch: null })}
      />
    </div>
  );
}
