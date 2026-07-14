import axios from './interceptor'

export function login(data) {
  return axios.post('/auth/login', data)
}

export function register(data) {
  return axios.post('/auth/register', data)
}

export function refreshToken(refreshToken) {
  return axios.post('/auth/refresh', { refreshToken })
}
