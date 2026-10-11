import { apiRequest } from "./api";

const authService = {
  login: async (credentials) =>
    apiRequest("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(credentials),
      skipAuth: true,
    }),
};

export default authService;
