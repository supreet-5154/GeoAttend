import { NavLink } from "react-router-dom";
import { CalendarDays, ClipboardCheck, LayoutDashboard, LogOut, UsersRound } from "lucide-react";

const items = [
  { label: "Overview", to: "/dashboard", icon: LayoutDashboard },
  { label: "Members", to: "/members", icon: UsersRound },
  { label: "Sessions", to: "/sessions", icon: CalendarDays },
  { label: "Attendance", to: "/attendance", icon: ClipboardCheck },
];

export default function Sidebar({ user, onLogout, isOpen, onClose }) {
  return (
    <>
      <aside className={`sidebar ${isOpen ? "sidebar-open" : ""}`}>
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
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="user-meta">
            <span className="sidebar-user-initial">{user?.name?.charAt(0)?.toUpperCase() || "U"}</span>
            <div>
              <strong>{user?.name || "User"}</strong>
              <span>{user?.role || "Role"}</span>
            </div>
          </div>

          <button type="button" className="logout-button" onClick={onLogout}>
            <LogOut size={16} />
            Logout
          </button>
        </div>
      </aside>

      {isOpen && <button type="button" className="sidebar-overlay" onClick={onClose} aria-label="Close menu" />}
    </>
  );
}
