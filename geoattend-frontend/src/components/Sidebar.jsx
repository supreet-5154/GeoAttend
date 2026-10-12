import { useEffect } from "react";
import { NavLink } from "react-router-dom";
import { CalendarDays, ClipboardCheck, Layers, LayoutDashboard, LogOut, UsersRound } from "lucide-react";

const items = [
  { label: "Overview", to: "/dashboard", icon: LayoutDashboard },
  { label: "Members", to: "/members", icon: UsersRound },
  { label: "Batches", to: "/batches", icon: Layers },
  { label: "Sessions", to: "/sessions", icon: CalendarDays },
  { label: "Attendance", to: "/attendance", icon: ClipboardCheck },
];

export default function Sidebar({ user, onLogout, isOpen, onClose }) {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "Escape" && isOpen) {
        onClose();
      }
    };
    if (isOpen) {
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  const roleLabel = user?.role === "ADMIN" ? "Administrator" : user?.role === "CLUB_USER" ? "Club Staff" : "Member";

  return (
    <>
      <aside className={`sidebar ${isOpen ? "sidebar-open" : ""}`} aria-label="Sidebar navigation">
        <div className="sidebar-header">
          <img className="club-brand-logo" src="/oyster-kode-logo.png" alt="Oyster Kode Club" />
          <div className="sidebar-brand-copy">
            <h1>GeoAttend</h1>
            <span>OYSTER KODE CLUB</span>
          </div>
        </div>

        <nav className="sidebar-nav" aria-label="Main navigation">
          <p className="sidebar-section-label">Workspace</p>
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}
              onClick={onClose}
            >
              <item.icon size={18} strokeWidth={1.9} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="user-meta">
            <span className="sidebar-user-initial">{user?.name?.charAt(0)?.toUpperCase() || "U"}</span>
            <div className="sidebar-user-info">
              <strong title={user?.name}>{user?.name || "User"}</strong>
              <span className="sidebar-role-pill">{roleLabel}</span>
            </div>
          </div>

          <button
            type="button"
            className="logout-button"
            onClick={onLogout}
            title="Log out of GeoAttend"
          >
            <LogOut size={16} />
            <span>Logout</span>
          </button>
        </div>
      </aside>

      {isOpen && (
        <button
          type="button"
          className="sidebar-overlay"
          onClick={onClose}
          aria-label="Close navigation menu"
        />
      )}
    </>
  );
}
