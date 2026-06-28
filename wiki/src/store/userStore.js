import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get, takeAccessToken } from '@/net/index.js'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref(null)
  const isLoading = ref(false)

  const isLoggedIn = computed(() => {
    return !!takeAccessToken() && !!userInfo.value
  })

  const username = computed(() => {
    return userInfo.value?.nickname || userInfo.value?.userEmail || '用户'
  })

  const avatar = computed(() => {
    return userInfo.value?.avatar || ''
  })

  const role = computed(() => userInfo.value?.role || null)
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')
  const isAdmin = computed(() => role.value === 'ADMIN' || role.value === 'SUPER_ADMIN')

  async function fetchUserInfo() {
    if (!takeAccessToken()) {
      userInfo.value = null
      return
    }
    isLoading.value = true
    try {
      await new Promise((resolve, reject) => {
        get('/user/info',
          (data) => {
            userInfo.value = data
            resolve(data)
          },
          (message, code) => {
            if (code === 401) {
              userInfo.value = null
            }
            reject(new Error(message))
          }
        )
      })
    } catch (err) {
      console.warn('获取用户信息失败:', err)
    } finally {
      isLoading.value = false
    }
  }

  function setUserInfo(info) {
    userInfo.value = info
  }

  function clearUserInfo() {
    userInfo.value = null
  }

  return {
    userInfo,
    isLoading,
    isLoggedIn,
    username,
    avatar,
    role,
    isSuperAdmin,
    isAdmin,
    fetchUserInfo,
    setUserInfo,
    clearUserInfo
  }
})
