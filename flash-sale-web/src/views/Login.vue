<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    await userStore.login(form.value.username, form.value.password)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e) {
    // Error already handled by request interceptor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div style="max-width: 400px; margin: 60px auto;">
    <el-card>
      <template #header>
        <div style="text-align: center;">
          <h2 style="color: #b2070a;">⚡ FlashSale</h2>
          <p style="color: #999;">用户登录</p>
        </div>
      </template>

      <el-form :model="form" label-position="top" @keyup.enter="handleLogin">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <div style="text-align: right; margin-bottom: 16px;">
          <el-button link type="primary" @click="router.push('/forgot-password')">忘记密码？</el-button>
        </div>
        <el-form-item>
          <el-button type="danger" style="width: 100%;" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div style="text-align: center;">
        <span style="color: #999;">还没有账号？</span>
        <el-button link type="primary" @click="router.push('/register')">立即注册</el-button>
      </div>
    </el-card>
  </div>
</template>
