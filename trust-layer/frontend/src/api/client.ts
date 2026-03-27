import axios from 'axios';
import { userManager } from '../context/AuthContext';

const api = axios.create();

api.interceptors.request.use(async (config) => {
  const user = await userManager.getUser();
  if (user?.access_token) {
    config.headers.Authorization = `Bearer ${user.access_token}`;
  }
  return config;
});

export default api;
