import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

axios.defaults.baseURL = '/api/v1'

let isRefreshing = false
let pendingRequests = []

function onRefreshed(token) {
  pendingRequests.forEach(cb => cb(token))
  pendingRequests = []
}

function addPendingRequest(cb) {
  pendingRequests.push(cb)
}

axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

axios.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data.data
  },
  async error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        const refreshToken = localStorage.getItem('refreshToken')
        if (refreshToken && !isRefreshing) {
          isRefreshing = true
          try {
            const res = await axios.post('/auth/refresh', { refreshToken })
            if (res.data.code === 0) {
              const { token, refreshToken: newRefresh } = res.data.data
              localStorage.setItem('token', token)
              localStorage.setItem('refreshToken', newRefresh)
              isRefreshing = false
              onRefreshed(token)
              error.config.headers.Authorization = `Bearer ${token}`
              return axios(error.config)
            }
          } catch {
            // refresh failed
          } finally {
            isRefreshing = false
          }
        } else if (refreshToken && isRefreshing) {
          return new Promise(resolve => {
            addPendingRequest(token => {
              error.config.headers.Authorization = `Bearer ${token}`
              resolve(axios(error.config))
            })
          })
        }
        localStorage.removeItem('token')
        localStorage.removeItem('refreshToken')
        router.push('/login')
      }
      ElMessage.error(data?.message || '服务器错误')
    } else {
      ElMessage.error('网络错误')
    }
    return Promise.reject(error)
  }
)

export default axios
