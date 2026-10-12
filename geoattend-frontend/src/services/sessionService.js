import { apiRequest } from "./api";
import { buildQueryString } from "../utils/queryUtils";

const sessionService = {
  listSessions: async (params = {}) =>
    apiRequest(`/api/sessions${buildQueryString(params)}`),

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
