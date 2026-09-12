import axios from './interceptor'

export function getProfile() {
  return axios.get('/user/profile')
}

export function updateProfile(data) {
  return axios.put('/user/profile', data)
}

export function changePassword(data) {
  return axios.post('/user/password', data)
}

export function updateApiConfig(data) {
  return axios.put('/user/api-config', data)
}
