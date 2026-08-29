import axios from './interceptor'

export function getSystemStats() {
  return axios.get('/admin/stats')
}

export function getAdminUsers() {
  return axios.get('/admin/users')
}

export function updateUserRole(userId, role) {
  return axios.put(`/admin/users/${userId}/role`, { role })
}

export function resetUserQuota(userId, quota) {
  return axios.put(`/admin/users/${userId}/quota`, { quota })
}

export function deleteUser(userId) {
  return axios.delete(`/admin/users/${userId}`)
}
