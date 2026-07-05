<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const form = ref({ username: '', password: '', email: '' })
const loading = ref(false)

async function handleRegister() {
  loading.value = true
  try {
    await userStore.register(form.value.username, form.value.password, form.value.email)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // Handled by interceptor
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
          <p style="color: #999;">用户注册</p>
        </div>
      </template>

      <el-form :model="form" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="2-50个字符" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="至少6位" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item>
          <el-button type="danger" style="width: 100%;" :loading="loading" @click="handleRegister">
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div style="text-align: center;">
        <span style="color: #999;">已有账号？</span>
        <el-button link type="primary" @click="router.push('/login')">去登录</el-button>
      </div>
    </el-card>
  </div>
</template>
