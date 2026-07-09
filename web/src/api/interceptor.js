import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

axios.defaults.baseURL = '/api/v1'

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
  error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
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
