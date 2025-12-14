import axios from "axios";

const client = axios.create({
  baseURL: "/",
  withCredentials: true,
  timeout: 10000
});

export default client;

