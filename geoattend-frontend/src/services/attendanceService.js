import { apiRequest } from "./api";
import { buildQueryString } from "../utils/queryUtils";

const attendanceService = {
  getAttendance: async (params = {}) =>
    apiRequest(`/api/attendance${buildQueryString(params)}`),

  getAttendanceForSession: async (sessionId, batchId = null) => {
    const url = batchId && batchId !== "all"
      ? `/api/attendance/session/${sessionId}?batchId=${batchId}`
      : `/api/attendance/session/${sessionId}`;
    return apiRequest(url);
  },

  getAttendanceByBatch: async (batchId) =>
    apiRequest(`/api/attendance/batch/${batchId}`),

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
