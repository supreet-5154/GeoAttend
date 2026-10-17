export const API_BASE_URL = (import.meta.env.VITE_API_URL || "http://localhost:8081").replace(/\/$/, "");

const safeJsonParse = async (response) => {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch (error) {
    return text;
  }
};

export const getStoredUser = () => {
  try {
    const rawUser = localStorage.getItem("user");
    return rawUser ? JSON.parse(rawUser) : null;
  } catch (error) {
    return null;
  }
};

export const clearAuth = () => {
  localStorage.removeItem("token");
  localStorage.removeItem("user");
};

/**
 * Decodes the expiration timestamp (in ms) from a standard JWT without external libraries.
 */
export const getTokenExpirationMs = (token) => {
  if (!token || typeof token !== "string") return null;
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join(""),
    );
    const parsed = JSON.parse(jsonPayload);
    return parsed.exp ? parsed.exp * 1000 : null;
  } catch (e) {
    return null;
  }
};

// In-flight refresh promise singleton to prevent duplicate concurrent refresh requests
let activeRefreshPromise = null;

/**
 * Attempts to silently refresh the JWT using the backend /api/auth/refresh endpoint.
 */
export const refreshSessionToken = async () => {
  if (activeRefreshPromise) {
    return activeRefreshPromise;
  }

  const currentToken = localStorage.getItem("token");
  if (!currentToken) {
    return null;
  }

  activeRefreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${currentToken}`,
        },
        body: JSON.stringify({ token: currentToken }),
      });

      if (!response.ok) {
        return null;
      }

      const data = await safeJsonParse(response);
      if (data && data.token) {
        localStorage.setItem("token", data.token);

        if (data.name && data.role) {
          const existingUser = getStoredUser() || {};
          const updatedUser = {
            ...existingUser,
            id: data.id ?? existingUser.id,
            name: data.name,
            email: data.email ?? existingUser.email,
            role: data.role,
          };
          localStorage.setItem("user", JSON.stringify(updatedUser));
        }

        window.dispatchEvent(new CustomEvent("geoattend:auth-refreshed", { detail: data }));
        return data.token;
      }
      return null;
    } catch (err) {
      return null;
    } finally {
      activeRefreshPromise = null;
    }
  })();

  return activeRefreshPromise;
};

export const apiRequest = async (endpoint, options = {}) => {
  const { skipAuth = false, headers = {}, _isRetry = false, ...rest } = options;
  let token = localStorage.getItem("token");

  // Proactive Silent Refresh: If token is valid but expires in less than 30 minutes, refresh ahead of time
  if (!skipAuth && token && !endpoint.includes("/api/auth/")) {
    const expMs = getTokenExpirationMs(token);
    const now = Date.now();
    // If expiring within 30 minutes (or already expired within grace), refresh proactively
    if (expMs && expMs - now < 30 * 60 * 1000) {
      const refreshedToken = await refreshSessionToken();
      if (refreshedToken) {
        token = refreshedToken;
      }
    }
  }

  const requestHeaders = {
    ...(rest.body ? { "Content-Type": "application/json" } : {}),
    ...headers,
  };

  if (!skipAuth && token) {
    requestHeaders.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...rest,
    headers: requestHeaders,
  });

  // Reactive Interceptor: If a 401 is received, attempt one automatic silent refresh and replay the request
  if (response.status === 401 && !skipAuth && !_isRetry && !endpoint.includes("/api/auth/")) {
    const refreshedToken = await refreshSessionToken();
    if (refreshedToken) {
      return apiRequest(endpoint, {
        ...options,
        _isRetry: true,
        headers: {
          ...headers,
          Authorization: `Bearer ${refreshedToken}`,
        },
      });
    }

    // Refresh failed or session expired beyond grace window
    clearAuth();
    window.dispatchEvent(new CustomEvent("geoattend:auth-expired"));
    throw new Error("Your session has expired. Please log in again.");
  }

  const data = await safeJsonParse(response);

  if (!response.ok) {
    let message = "Something went wrong. Please try again.";

    if (data?.message || data?.detail) {
      message = data.message || data.detail;
    } else if (response.status === 401) {
      message = "Your session has expired. Please log in again.";
    } else if (response.status === 403) {
      message = "You do not have permission to perform this action.";
    } else if (typeof data === "string" && data) {
      message = data;
    } else if (response.status === 400) {
      message = "Invalid request. Please check your input.";
    }

    throw new Error(message);
  }

  return data;
};

export const formatDateTime = (value) => {
  if (!value) {
    return "N/A";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString([], {
    dateStyle: "medium",
    timeStyle: "short",
  });
};

export const formatShortDate = (value) => {
  if (!value) {
    return "N/A";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleDateString([], {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
};

export const getLocationText = (latitude, longitude) => {
  if (latitude === null || latitude === undefined || longitude === null || longitude === undefined) {
    return "N/A";
  }

  return `${Number(latitude).toFixed(4)}, ${Number(longitude).toFixed(4)}`;
};
