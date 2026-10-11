import { useState } from "react";
import { CalendarDays, Menu } from "lucide-react";

export default function Header({ title, user, onToggleSidebar }) {
  const [today] = useState(() => new Intl.DateTimeFormat([], {
    weekday: "long",
    month: "short",
    day: "numeric",
  }).format(new Date()));

  return (
    <header className="top-header">
      <div className="header-left">
        <button type="button" className="mobile-menu-button" onClick={onToggleSidebar} aria-label="Toggle navigation">
          <Menu size={19} />
        </button>
        <div>
          <p className="header-context">GEOATTEND <span>/</span> {title}</p>
        </div>
      </div>

      <div className="header-user">
        <div className="header-date"><CalendarDays size={16} /><span>{today}</span></div>
        <div className="user-pill">
          <span className="user-avatar">{user?.name?.charAt(0)?.toUpperCase() || "U"}</span>
          <div>
            <strong>{user?.name || "User"}</strong>
            <small>{user?.role || "Role"}</small>
          </div>
        </div>
      </div>
    </header>
  );
}
