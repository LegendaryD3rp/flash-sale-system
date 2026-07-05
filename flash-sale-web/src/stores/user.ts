import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '../utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId')) || 0)
  const role = ref(localStorage.getItem('role') || '')

  async function login(username: string, password: string) {
    const data: any = await request.post('/api/user/login', { username, password })
    token.value = data.token
    userId.value = data.userId
    role.value = data.role
    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', String(data.userId))
    localStorage.setItem('role', data.role)
    return data
  }

  async function register(username: string, password: string, email: string) {
    return await request.post('/api/user/register', { username, password, email })
  }

  function logout() {
    token.value = ''
    userId.value = 0
    role.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('role')
  }

  function isLoggedIn(): boolean {
    return !!token.value
  }

  function isAdmin(): boolean {
    return role.value === 'ADMIN'
  }

  return { token, userId, role, login, register, logout, isLoggedIn, isAdmin }
})
