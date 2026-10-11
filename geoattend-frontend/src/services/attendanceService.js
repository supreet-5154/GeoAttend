import { apiRequest } from "./api";

const attendanceService = {
  getAttendanceForSession: async (sessionId) =>
    apiRequest(`/api/attendance/session/${sessionId}`),

  getAttendanceForMember: async (memberId) =>
    apiRequest(`/api/attendance/member/${memberId}`),

  getMyAttendance: async () =>
    apiRequest("/api/attendance/my"),

  getMyTracking: async () =>
    apiRequest("/api/attendance/my/tracking"),

  getTrackingForSession: async (sessionId) =>
    apiRequest(`/api/attendance/session/${sessionId}/tracking`),

  getLocationChecksForSession: async (sessionId) =>
    apiRequest(`/api/attendance/session/${sessionId}/checks`),

  startMyTracking: async (payload) =>
    apiRequest("/api/attendance/tracking/start", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  recordLocationCheck: async (payload) =>
    apiRequest("/api/attendance/location-check", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  createAttendance: async (payload) =>
    apiRequest("/api/attendance", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  markSelfAttendance: async (payload) =>
    apiRequest("/api/attendance/self", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  createManualAttendance: async (payload) =>
    apiRequest("/api/attendance/manual", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updateAttendance: async (id, payload) =>
    apiRequest(`/api/attendance/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  deleteAttendance: async (id) =>
    apiRequest(`/api/attendance/${id}`, { method: "DELETE" }),
};

export default attendanceService;
