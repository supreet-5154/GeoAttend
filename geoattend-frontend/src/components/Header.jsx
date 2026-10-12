import { useState } from "react";
import { CalendarDays, Menu } from "lucide-react";

export default function Header({ title, user, onToggleSidebar }) {
  const [today] = useState(() => new Intl.DateTimeFormat([], {
    weekday: "short",
    month: "short",
    day: "numeric",
  }).format(new Date()));

  const roleLabel = user?.role === "ADMIN" ? "Admin" : user?.role === "CLUB_USER" ? "Staff" : "Member";

  return (
    <header className="top-header">
      <div className="header-left">
        <button
          type="button"
          className="mobile-menu-button"
          onClick={onToggleSidebar}
          aria-label="Toggle navigation menu"
        >
          <Menu size={20} />
        </button>
        <div className="header-breadcrumbs">
          <span className="header-app-name">GEOATTEND</span>
          <span className="header-slash">/</span>
          <h2 className="header-page-title">{title}</h2>
        </div>
      </div>

      <div className="header-user">
        <div className="header-date">
          <CalendarDays size={15} />
          <span>{today}</span>
        </div>
        <div className="user-pill" title={`${user?.name || "User"} (${user?.role || ""})`}>
          <span className="user-avatar">{user?.name?.charAt(0)?.toUpperCase() || "U"}</span>
          <div className="user-pill-text">
            <strong>{user?.name || "User"}</strong>
            <small className={`role-badge role-${(user?.role || "MEMBER").toLowerCase()}`}>{roleLabel}</small>
          </div>
        </div>
      </div>
    </header>
  );
}
