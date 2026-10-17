import { useEffect, useRef, useState } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes, useNavigate } from "react-router-dom";
import { Eye, EyeOff, History, LogOut, MapPin, ShieldCheck } from "lucide-react";
import Header from "./components/Header";
import GeoAttendanceTracker from "./components/GeoAttendanceTracker";
import Sidebar from "./components/Sidebar";
import Toast from "./components/Toast";
import Attendance from "./pages/Attendance";
import Batches from "./pages/Batches";
import Dashboard from "./pages/Dashboard";
import MemberAttendance from "./pages/MemberAttendance";
import MemberHome from "./pages/MemberHome";
import Members from "./pages/Members";
import Sessions from "./pages/Sessions";
import authService from "./services/authService";
import batchService from "./services/batchService";
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
  const [authMode, setAuthMode] = useState("signin");
  const [loginForm, setLoginForm] = useState({ email: "", password: "" });
  const [signupForm, setSignupForm] = useState({
    name: "",
    email: "",
    password: "",
    confirmPassword: "",
    requestedBatchId: "",
    studentId: "",
  });
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showSignupPassword, setShowSignupPassword] = useState(false);

  useEffect(() => {
    let active = true;
    batchService.listActiveBatches(true)
      .then((res) => {
        if (active && Array.isArray(res)) {
          setBatches(res);
        }
      })
      .catch(() => {});
    return () => { active = false; };
  }, []);

  const handleLoginSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError("");
    setNotice("");

    try {
      const data = await authService.login(loginForm);
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

  const handleSignupSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setNotice("");

    if (!signupForm.name.trim() || !signupForm.email.trim() || !signupForm.password) {
      setError("Please complete all required fields.");
      return;
    }

    if (signupForm.password.length < 8) {
      setError("Password must be at least 8 characters long.");
      return;
    }

    if (signupForm.password !== signupForm.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const payload = {
        name: signupForm.name.trim(),
        email: signupForm.email.trim(),
        password: signupForm.password,
        requestedBatchId: signupForm.requestedBatchId ? Number(signupForm.requestedBatchId) : null,
        studentId: signupForm.studentId.trim() || null,
      };

      const response = await authService.register(payload);
      const successMsg = response?.message || "Registration submitted! Your account is pending review by club staff.";
      setNotice(successMsg);
      showToast(successMsg, "success");
      setSignupForm({
        name: "",
        email: "",
        password: "",
        confirmPassword: "",
        requestedBatchId: "",
        studentId: "",
      });
      setAuthMode("signin");
    } catch (signupError) {
      const message = signupError.message || "Registration request failed.";
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
            <p className="eyebrow">{authMode === "signin" ? "Welcome back" : "Join GeoAttend"}</p>
            <h2>{authMode === "signin" ? "Sign in" : "Request access"}</h2>
            <p className="subtitle">
              {authMode === "signin"
                ? "Continue to your workspace."
                : "Submit your details to request club membership."}
            </p>
          </div>

          <div className="auth-mode-switch" role="tablist" aria-label="Account access">
            <button
              type="button"
              role="tab"
              aria-selected={authMode === "signin"}
              className={authMode === "signin" ? "active" : ""}
              onClick={() => {
                setAuthMode("signin");
                setError("");
                setNotice("");
              }}
            >
              Sign in
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={authMode === "signup"}
              className={authMode === "signup" ? "active" : ""}
              onClick={() => {
                setAuthMode("signup");
                setError("");
                setNotice("");
              }}
            >
              Register
            </button>
          </div>

          {notice && <div className="form-notice" role="status">{notice}</div>}
          {error && <div className="form-error" role="alert">{error}</div>}

          {authMode === "signin" ? (
            <form className="login-form" onSubmit={handleLoginSubmit}>
              <label>
                <span>Email address</span>
                <input
                  type="email"
                  value={loginForm.email}
                  onChange={(event) => setLoginForm((current) => ({ ...current, email: event.target.value }))}
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
                    value={loginForm.password}
                    onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))}
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

              <button type="submit" className="primary-button full-width login-submit" disabled={loading}>
                {loading ? "Signing in..." : "Sign in"}
              </button>
            </form>
          ) : (
            <form className="login-form signup-form" onSubmit={handleSignupSubmit}>
              <div className="signup-information">
                <ShieldCheck size={19} />
                <p>
                  New member registrations are reviewed and approved by club staff before access is granted.
                </p>
              </div>

              <label>
                <span>Full Name</span>
                <input
                  type="text"
                  value={signupForm.name}
                  onChange={(event) => setSignupForm((current) => ({ ...current, name: event.target.value }))}
                  placeholder="e.g. Alex Johnson"
                  autoComplete="name"
                  required
                />
              </label>

              <label>
                <span>Email address</span>
                <input
                  type="email"
                  value={signupForm.email}
                  onChange={(event) => setSignupForm((current) => ({ ...current, email: event.target.value }))}
                  placeholder="you@example.com"
                  autoComplete="email"
                  required
                />
              </label>

              <label>
                <span>Password (minimum 8 characters)</span>
                <div className="password-input-wrap">
                  <input
                    type={showSignupPassword ? "text" : "password"}
                    value={signupForm.password}
                    onChange={(event) => setSignupForm((current) => ({ ...current, password: event.target.value }))}
                    placeholder="Create a strong password"
                    autoComplete="new-password"
                    minLength={8}
                    required
                  />
                  <button
                    type="button"
                    className="password-toggle"
                    onClick={() => setShowSignupPassword((current) => !current)}
                    aria-label={showSignupPassword ? "Hide password" : "Show password"}
                    title={showSignupPassword ? "Hide password" : "Show password"}
                  >
                    {showSignupPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </label>

              <label>
                <span>Confirm Password</span>
                <input
                  type={showSignupPassword ? "text" : "password"}
                  value={signupForm.confirmPassword}
                  onChange={(event) => setSignupForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                  placeholder="Re-enter your password"
                  autoComplete="new-password"
                  minLength={8}
                  required
                />
              </label>

              <div className="signup-preferences-form">
                <label>
                  <span>Generation / Batch Preference</span>
                  <select
                    value={signupForm.requestedBatchId}
                    onChange={(event) => setSignupForm((current) => ({ ...current, requestedBatchId: event.target.value }))}
                    className="signup-batch-select"
                  >
                    <option value="">-- Select Generation / Batch (Optional) --</option>
                    {batches.map((batch) => (
                      <option key={batch.id} value={batch.id}>
                        {batch.name}
                      </option>
                    ))}
                  </select>
                </label>

                <label>
                  <span>Student ID (Optional)</span>
                  <input
                    type="text"
                    value={signupForm.studentId}
                    onChange={(event) => setSignupForm((current) => ({ ...current, studentId: event.target.value }))}
                    placeholder="e.g. 2024-CS-042"
                    maxLength={50}
                  />
                </label>
              </div>

              <button type="submit" className="primary-button full-width login-submit" disabled={loading}>
                {loading ? "Submitting registration..." : "Submit Registration Request"}
              </button>
            </form>
          )}
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
          <div className="member-brand-copy">
            <span className="eyebrow">GeoAttend</span>
            <h1>Member Attendance</h1>
          </div>
        </div>

        <nav className="member-nav desktop-only" aria-label="Member navigation">
          <NavLink to="/member" end className={({ isActive }) => (isActive ? "active" : "")}>
            <MapPin size={16} /> Live Check-in
          </NavLink>
          <NavLink to="/member/attendance" className={({ isActive }) => (isActive ? "active" : "")}>
            <History size={16} /> My History
          </NavLink>
        </nav>

        <div className="member-header-actions">
          <div className="user-pill" title={`${user?.name || "Member"} (Member)`}>
            <span className="user-avatar">{user?.name?.charAt(0)?.toUpperCase() || "M"}</span>
            <div className="user-pill-text">
              <strong>{user?.name || "Member"}</strong>
              <small className="role-badge role-member">Member</small>
            </div>
          </div>
          <button
            type="button"
            className="logout-button member-logout"
            onClick={onLogout}
            title="Sign out of GeoAttend"
          >
            <LogOut size={16} />
            <span className="logout-text">Sign out</span>
          </button>
        </div>
      </header>

      <main className="member-page">{children}</main>

      {/* Mobile Bottom Navigation Bar for thumb navigation on phones */}
      <nav className="member-mobile-bottom-nav" aria-label="Mobile Navigation">
        <NavLink
          to="/member"
          end
          className={({ isActive }) => `mobile-bottom-nav-link ${isActive ? "active" : ""}`}
        >
          <MapPin size={20} />
          <span>Live Check-in</span>
        </NavLink>
        <NavLink
          to="/member/attendance"
          className={({ isActive }) => `mobile-bottom-nav-link ${isActive ? "active" : ""}`}
        >
          <History size={20} />
          <span>My History</span>
        </NavLink>
      </nav>
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

  useEffect(() => {
    const handleAuthExpired = () => {
      clearAuth();
      setUser(null);
      navigate("/login");
      showToast("Your session has expired. Please log in again.", "error");
    };

    const handleAuthRefreshed = (event) => {
      if (event.detail) {
        setUser((current) => ({
          ...current,
          ...event.detail,
        }));
      }
    };

    window.addEventListener("geoattend:auth-expired", handleAuthExpired);
    window.addEventListener("geoattend:auth-refreshed", handleAuthRefreshed);

    return () => {
      window.removeEventListener("geoattend:auth-expired", handleAuthExpired);
      window.removeEventListener("geoattend:auth-refreshed", handleAuthRefreshed);
    };
  }, [navigate]);

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
              {user?.role === "MEMBER" ? (
                <Navigate to="/member" replace />
              ) : (
                <AppShell
                  user={user}
                  title="Members"
                  onLogout={handleLogout}
                  sidebarOpen={sidebarOpen}
                  setSidebarOpen={setSidebarOpen}
                >
                  <Members user={user} showToast={showToast} />
                </AppShell>
              )}
            </ProtectedRoute>
          }
        />

        <Route
          path="/batches"
          element={
            <ProtectedRoute user={user}>
              {user?.role === "MEMBER" ? (
                <Navigate to="/member" replace />
              ) : (
                <AppShell
                  user={user}
                  title="Batches & Generations"
                  onLogout={handleLogout}
                  sidebarOpen={sidebarOpen}
                  setSidebarOpen={setSidebarOpen}
                >
                  <Batches user={user} showToast={showToast} />
                </AppShell>
              )}
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
