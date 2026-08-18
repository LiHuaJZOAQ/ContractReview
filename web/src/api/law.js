import axios from './interceptor'

export function getLaw(id) {
  return axios.get(`/law/${id}`)
}

export function listLaws(category, keyword) {
  const params = {}
  if (category) params.category = category
  if (keyword) params.keyword = keyword
  return axios.get('/law', { params })
}

export function createLaw(data) {
  return axios.post('/law', data)
}

export function updateLaw(id, data) {
  return axios.put(`/law/${id}`, data)
}

export function deleteLaw(id) {
  return axios.delete(`/law/${id}`)
}

export function toggleLaw(id) {
  return axios.put(`/law/${id}/toggle`)
}

export function reindexLaw(id) {
  return axios.post(`/law/${id}/reindex`)
}
