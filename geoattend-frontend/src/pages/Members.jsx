import { useEffect, useState } from "react";
import { Plus, X } from "lucide-react";
import EmptyState from "../components/EmptyState";
import Loading from "../components/Loading";
import MemberTable from "../components/MemberTable";
import memberService from "../services/memberService";

const initialForm = { name: "", email: "", password: "" };

export default function Members({ user, showToast }) {
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingMember, setEditingMember] = useState(null);
  const [form, setForm] = useState(initialForm);

  const canManageMembers = user?.role === "ADMIN";

  const fetchMembers = async () => {
    try {
      setLoading(true);
      const data = await memberService.listMembers();
      setMembers(data);
    } catch (error) {
      showToast(error.message || "Unable to load members.", "error");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMembers();
  }, [showToast]);

  const openCreateModal = () => {
    setEditingMember(null);
    setForm(initialForm);
    setShowModal(true);
  };

  const openEditModal = (member) => {
    setEditingMember(member);
    setForm({ name: member.name, email: member.email, password: "" });
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingMember(null);
    setForm(initialForm);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      if (editingMember) {
        await memberService.updateMember(editingMember.id, form);
        showToast("Member updated successfully.", "success");
      } else {
        await memberService.createMember(form);
        showToast("Member created successfully.", "success");
      }

      closeModal();
      fetchMembers();
    } catch (error) {
      showToast(error.message || "Unable to save member.", "error");
    }
  };

  const handleDelete = async (member) => {
    const confirmed = window.confirm(`Delete ${member.name}? This action cannot be undone.`);

    if (!confirmed) {
      return;
    }

    try {
      await memberService.deleteMember(member.id);
      showToast("Member deleted successfully.", "success");
      fetchMembers();
    } catch (error) {
      showToast(error.message || "Unable to delete member.", "error");
    }
  };

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">Club roster</p>
          <h1>Members</h1>
        </div>

        {canManageMembers && (
          <button type="button" className="primary-button" onClick={openCreateModal}>
            <Plus size={17} /> Add Member
          </button>
        )}
      </div>

      {loading ? (
        <Loading message="Loading members..." />
      ) : members.length ? (
        <MemberTable members={members} canManage={canManageMembers} onEdit={openEditModal} onDelete={handleDelete} />
      ) : (
        <EmptyState title="No members found" description="No club members are available yet." />
      )}

      {showModal && (
        <div className="modal-backdrop" onClick={closeModal}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingMember ? "Edit Member" : "Add Member"}</h3>
              <button type="button" className="close-button" onClick={closeModal} aria-label="Close dialog" title="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="form-grid">
              <label>
                <span>Name</span>
                <input
                  type="text"
                  value={form.name}
                  onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                  required
                />
              </label>

              <label>
                <span>Email</span>
                <input
                  type="email"
                  value={form.email}
                  onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
                  required
                />
              </label>

              <label>
                <span>Password</span>
                <input
                  type="password"
                  value={form.password}
                  onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                  placeholder={editingMember ? "Leave blank to keep current password" : "Enter member password"}
                  required={!editingMember}
                />
              </label>

              <div className="modal-actions">
                <button type="button" className="secondary-button" onClick={closeModal}>
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
    </div>
  );
}
