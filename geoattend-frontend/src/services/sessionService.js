import { apiRequest } from "./api";

const sessionService = {
  listSessions: async () => apiRequest("/api/sessions"),

  createSession: async (payload) =>
    apiRequest("/api/sessions", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updateSession: async (id, payload) =>
    apiRequest(`/api/sessions/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  deleteSession: async (id) =>
    apiRequest(`/api/sessions/${id}`, { method: "DELETE" }),
};

export default sessionService;
