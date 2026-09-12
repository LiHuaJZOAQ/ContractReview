import axios from './interceptor'

export function getSystemStats() {
  return axios.get('/admin/stats')
}

export function getSystemMonitor() {
  return axios.get('/admin/monitor')
}

export function getOperationLogs(page = 1, size = 20, action = '') {
  const params = { page, size }
  if (action) params.action = action
  return axios.get('/admin/operations', { params })
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

export function getDefaultQuota() {
  return axios.get('/admin/quota-default')
}

export function setDefaultQuota(quota) {
  return axios.put('/admin/quota-default', { quota })
}

export function deleteUser(userId) {
  return axios.delete(`/admin/users/${userId}`)
}
