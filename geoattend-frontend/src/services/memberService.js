import { apiRequest } from "./api";

const memberService = {
  listMembers: async () => apiRequest("/api/members"),

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
