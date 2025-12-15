import axios from "axios";

const client = axios.create({
  baseURL: "/api",
  withCredentials: true,
  timeout: 10000,
});

export default client;
