import { apiRequest } from "./api";
import { buildQueryString } from "../utils/queryUtils";

const batchService = {
  listBatches: async (params = {}) =>
    apiRequest(`/api/batches${buildQueryString(params)}`),

  listActiveBatches: async (skipAuth = false) =>
    apiRequest("/api/batches/active", { skipAuth }),

  getBatch: async (id) => apiRequest(`/api/batches/${id}`),

  createBatch: async (payload) =>
    apiRequest("/api/batches", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updateBatch: async (id, payload) =>
    apiRequest(`/api/batches/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  archiveBatch: async (id) =>
    apiRequest(`/api/batches/${id}/archive`, {
      method: "PUT",
    }),

  activateBatch: async (id) =>
    apiRequest(`/api/batches/${id}/activate`, {
      method: "PUT",
    }),
};

export default batchService;
