import { apiRequest } from "./api";
import { buildQueryString } from "../utils/queryUtils";

const memberService = {
  listMembers: async (paramsOrBatchId = {}) => {
    let qs = "";
    if (typeof paramsOrBatchId === "object" && paramsOrBatchId !== null) {
      qs = buildQueryString(paramsOrBatchId);
    } else if (paramsOrBatchId && paramsOrBatchId !== "all") {
      qs = `?batchId=${paramsOrBatchId}`;
    }
    return apiRequest(`/api/members${qs}`);
  },

  assignBatch: async (memberId, batchId) =>
    apiRequest(`/api/members/${memberId}/batch`, {
      method: "PUT",
      body: JSON.stringify({ batchId }),
    }),

  listSignups: async (paramsOrStatus = {}) => {
    let qs = "";
    if (typeof paramsOrStatus === "object" && paramsOrStatus !== null) {
      qs = buildQueryString(paramsOrStatus);
    } else if (paramsOrStatus && paramsOrStatus !== "ALL") {
      qs = `?status=${paramsOrStatus}`;
    }
    return apiRequest(`/api/members/signups${qs}`);
  },

  approveSignup: async (id, batchId = null) =>
    apiRequest(`/api/members/signups/${id}/approve`, {
      method: "POST",
      body: JSON.stringify({ batchId }),
    }),

  rejectSignup: async (id, reason = null) =>
    apiRequest(`/api/members/signups/${id}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    }),


  createMember: async (payload) =>
    apiRequest("/api/members", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updateMember: async (id, payload) =>
    apiRequest(`/api/members/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  deleteMember: async (id) =>
    apiRequest(`/api/members/${id}`, {
      method: "DELETE",
    }),
};

export default memberService;
