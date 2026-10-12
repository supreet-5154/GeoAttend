export const API_BASE_URL = "http://localhost:8081";

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

export const apiRequest = async (endpoint, options = {}) => {
  const { skipAuth = false, headers = {}, ...rest } = options;
  const token = localStorage.getItem("token");

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
