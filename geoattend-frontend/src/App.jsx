import { useEffect, useRef, useState } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes, useNavigate } from "react-router-dom";
import { Eye, EyeOff, History, MapPin, ShieldCheck } from "lucide-react";
import Header from "./components/Header";
import GeoAttendanceTracker from "./components/GeoAttendanceTracker";
import Sidebar from "./components/Sidebar";
import Toast from "./components/Toast";
import Attendance from "./pages/Attendance";
import Dashboard from "./pages/Dashboard";
import MemberAttendance from "./pages/MemberAttendance";
import MemberHome from "./pages/MemberHome";
import Members from "./pages/Members";
import Sessions from "./pages/Sessions";
import authService from "./services/authService";
import { clearAuth, getStoredUser } from "./services/api";
import "./App.css";

function AppShell({ user, title, children, onLogout, sidebarOpen, setSidebarOpen }) {
  return (
    <div className="app-shell">
      <Sidebar
        user={user}
        onLogout={onLogout}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <div className="main-panel">
        <Header
          title={title}
          user={user}
          onToggleSidebar={() => setSidebarOpen((current) => !current)}
        />

        <main className="page-content">{children}</main>
      </div>
    </div>
  );
}

function LoginPage({ onLoginSuccess, showToast }) {
  const [form, setForm] = useState({ email: "", password: "" });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      const data = await authService.login(form);
      const loggedInUser = {
        id: data.id,
        name: data.name,
        email: data.email,
        role: data.role,
      };

      localStorage.setItem("token", data.token);
      localStorage.setItem("user", JSON.stringify(loggedInUser));

      onLoginSuccess(loggedInUser);
      showToast("Login successful.", "success");
    } catch (loginError) {
      const message = loginError.message || "Invalid credentials.";
      setError(message);
      showToast(message, "error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <section className="login-brand-panel">
        <div className="brand-block login-brand">
          <img className="club-brand-logo" src="/oyster-kode-logo.png" alt="Oyster Kode Club" />
          <div className="login-brand-name"><span>GeoAttend</span><small>Oyster Kode Club</small></div>
        </div>
        <div className="login-brand-copy">
          <p className="eyebrow">Account access</p>
          <h1>Presence,<br />accounted for.</h1>
        </div>
        <div className="login-geofence" aria-hidden="true">
          <div className="geo-ring geo-ring-outer" />
          <div className="geo-ring geo-ring-middle" />
          <div className="geo-ring geo-ring-inner" />
          <div className="geo-center"><MapPin size={27} strokeWidth={2.2} /></div>
          <span className="geo-point geo-point-one" />
          <span className="geo-point geo-point-two" />
          <span className="geo-point geo-point-three" />
        </div>
        <div className="login-brand-footer"><ShieldCheck size={17} /> Secure attendance workspace</div>
      </section>

      <section className="login-form-side">
        <div className="login-card">
          <div className="login-form-heading">
            <p className="eyebrow">Welcome back</p>
            <h2>Sign in</h2>
            <p className="subtitle">Continue to your workspace.</p>
          </div>

          <form className="login-form" onSubmit={handleSubmit}>
            <label>
              <span>Email address</span>
              <input
                type="email"
                value={form.email}
                onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
                placeholder="you@example.com"
                autoComplete="username"
                required
              />
            </label>

            <label>
              <span>Password</span>
              <div className="password-input-wrap">
                <input
                  type={showPassword ? "text" : "password"}
                  value={form.password}
                  onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  required
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  title={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </label>

            {error && <div className="form-error" role="alert">{error}</div>}

            <button type="submit" className="primary-button full-width login-submit" disabled={loading}>
              {loading ? "Signing in..." : "Sign in"}
            </button>
          </form>
        </div>
        <div className="login-form-foot"><ShieldCheck size={15} /> Protected account access</div>
      </section>
    </div>
  );
}

function ProtectedRoute({ user, children }) {
  return user ? children : <Navigate to="/login" replace />;
}

function PublicRoute({ user, children }) {
  return !user ? children : <Navigate to={user.role === "MEMBER" ? "/member" : "/dashboard"} replace />;
}

function MemberShell({ user, onLogout, showToast, children }) {
  return (
    <div className="member-shell">
      <GeoAttendanceTracker showToast={showToast} />
      <header className="member-header">
        <div className="member-brand">
          <img className="club-brand-logo" src="/oyster-kode-logo.png" alt="Oyster Kode Club" />
          <div>
            <p className="eyebrow">GeoAttend</p>
            <h1>Attendance</h1>
          </div>
        </div>

        <nav className="member-nav" aria-label="Member navigation">
          <NavLink to="/member" end>Check in</NavLink>
          <NavLink to="/member/attendance"><History size={16} /> My history</NavLink>
        </nav>

        <div className="member-header-actions">
          <div className="user-pill">
            <span className="user-avatar">{user?.name?.charAt(0)?.toUpperCase() || "U"}</span>
            <div>
              <strong>{user?.name || "Member"}</strong>
              <small>{user?.role || "MEMBER"}</small>
            </div>
          </div>
          <button type="button" className="logout-button member-logout" onClick={onLogout}>Sign out</button>
        </div>
      </header>

      <main className="member-page">{children}</main>
    </div>
  );
}

function AppContent() {
  const [user, setUser] = useState(() => getStoredUser());
  const [toast, setToast] = useState(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const toastTimeout = useRef(null);
  const navigate = useNavigate();

  const showToast = (message, type = "success") => {
    setToast({ message, type });

    if (toastTimeout.current) {
      window.clearTimeout(toastTimeout.current);
    }

    toastTimeout.current = window.setTimeout(() => {
      setToast(null);
    }, 3200);
  };

  useEffect(() => {
    return () => {
      if (toastTimeout.current) {
        window.clearTimeout(toastTimeout.current);
      }
    };
  }, []);

  const handleLogout = () => {
    clearAuth();
    setUser(null);
    setSidebarOpen(false);
    navigate("/login");
    showToast("You have been logged out.", "info");
  };

  return (
    <>
      <Toast toast={toast} />

      <Routes>
        <Route path="/" element={<Navigate to={user ? (user.role === "MEMBER" ? "/member" : "/dashboard") : "/login"} replace />} />

        <Route
          path="/login"
          element={
            <PublicRoute user={user}>
              <LoginPage onLoginSuccess={setUser} showToast={showToast} />
            </PublicRoute>
          }
        />

        <Route
          path="/member"
          element={
            <ProtectedRoute user={user}>
              {user?.role === "MEMBER" ? (
                <MemberShell user={user} onLogout={handleLogout} showToast={showToast}>
                  <MemberHome user={user} showToast={showToast} />
                </MemberShell>
              ) : (
                <Navigate to="/dashboard" replace />
              )}
            </ProtectedRoute>
          }
        />

        <Route
          path="/member/attendance"
          element={
            <ProtectedRoute user={user}>
              {user?.role === "MEMBER" ? (
                <MemberShell user={user} onLogout={handleLogout} showToast={showToast}>
                  <MemberAttendance user={user} showToast={showToast} />
                </MemberShell>
              ) : (
                <Navigate to="/dashboard" replace />
              )}
            </ProtectedRoute>
          }
        />

        <Route
          path="/dashboard"
          element={
            <ProtectedRoute user={user}>
              <AppShell
                user={user}
                title="Dashboard"
                onLogout={handleLogout}
                sidebarOpen={sidebarOpen}
                setSidebarOpen={setSidebarOpen}
              >
                <Dashboard user={user} showToast={showToast} />
              </AppShell>
            </ProtectedRoute>
          }
        />

        <Route
          path="/members"
          element={
            <ProtectedRoute user={user}>
              <AppShell
                user={user}
                title="Members"
                onLogout={handleLogout}
                sidebarOpen={sidebarOpen}
                setSidebarOpen={setSidebarOpen}
              >
                <Members user={user} showToast={showToast} />
              </AppShell>
            </ProtectedRoute>
          }
        />

        <Route
          path="/sessions"
          element={
            <ProtectedRoute user={user}>
              <AppShell
                user={user}
                title="Sessions"
                onLogout={handleLogout}
                sidebarOpen={sidebarOpen}
                setSidebarOpen={setSidebarOpen}
              >
                <Sessions user={user} showToast={showToast} />
              </AppShell>
            </ProtectedRoute>
          }
        />

        <Route
          path="/attendance"
          element={
            <ProtectedRoute user={user}>
              <AppShell
                user={user}
                title="Attendance"
                onLogout={handleLogout}
                sidebarOpen={sidebarOpen}
                setSidebarOpen={setSidebarOpen}
              >
                <Attendance user={user} showToast={showToast} />
              </AppShell>
            </ProtectedRoute>
          }
        />
      </Routes>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  );
}
