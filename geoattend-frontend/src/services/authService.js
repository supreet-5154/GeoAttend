import { apiRequest } from "./api";

const authService = {
  login: async (credentials) =>
    apiRequest("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(credentials),
      skipAuth: true,
    }),

  register: async (payload) =>
    apiRequest("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload),
      skipAuth: true,
    }),
};

export default authService;
