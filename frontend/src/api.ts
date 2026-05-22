import axios from 'axios'

export const TOKEN_KEY = 'treedms_token'
export const USER_KEY = 'treedms_user'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export function apiMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || error.message || '请求失败'
  }
  if (error instanceof Error) {
    return error.message
  }
  return '请求失败'
}
