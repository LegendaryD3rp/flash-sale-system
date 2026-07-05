<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { User, Message, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()

const step = ref(1) // 1: 验证身份, 2: 重置密码
const loading = ref(false)

// 第一步
const verifyForm = ref({ username: '', email: '' })
const resetToken = ref('')

// 第二步
const passwordForm = ref({ newPassword: '', confirmPassword: '' })

async function handleVerify() {
  loading.value = true
  try {
    const token: any = await request.post('/api/user/forgot-password', {
      username: verifyForm.value.username,
      email: verifyForm.value.email
    })
    resetToken.value = token
    ElMessage.success('验证通过，请设置新密码')
    step.value = 2
  } catch {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function handleReset() {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.error('密码至少6位')
    return
  }
  loading.value = true
  try {
    await request.post('/api/user/reset-password', {
      token: resetToken.value,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码重置成功，请登录')
    router.push('/login')
  } catch {
    // Error handled by interceptor
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
          <p style="color: #999;">{{ step === 1 ? '忘记密码' : '重置密码' }}</p>
        </div>
      </template>

      <!-- 第一步：验证身份 -->
      <template v-if="step === 1">
        <el-steps :active="1" align-center style="margin-bottom: 24px;">
          <el-step title="验证身份" />
          <el-step title="重置密码" />
          <el-step title="完成" />
        </el-steps>

        <el-form :model="verifyForm" label-position="top" @keyup.enter="handleVerify">
          <el-form-item label="用户名">
            <el-input v-model="verifyForm.username" placeholder="请输入用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="verifyForm.email" placeholder="请输入注册邮箱" :prefix-icon="Message" />
          </el-form-item>
          <el-form-item>
            <el-button type="danger" style="width: 100%;" :loading="loading" @click="handleVerify">
              验证
            </el-button>
          </el-form-item>
        </el-form>

        <div style="text-align: center;">
          <span style="color: #999;">想起密码了？</span>
          <el-button link type="primary" @click="router.push('/login')">返回登录</el-button>
        </div>
      </template>

      <!-- 第二步：重置密码 -->
      <template v-if="step === 2">
        <el-steps :active="2" align-center style="margin-bottom: 24px;">
          <el-step title="验证身份" />
          <el-step title="重置密码" />
          <el-step title="完成" />
        </el-steps>

        <el-form :model="passwordForm" label-position="top" @keyup.enter="handleReset">
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位" :prefix-icon="Lock" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" :prefix-icon="Lock" />
          </el-form-item>
          <el-form-item>
            <el-button type="danger" style="width: 100%;" :loading="loading" @click="handleReset">
              重置密码
            </el-button>
          </el-form-item>
        </el-form>
      </template>
    </el-card>
  </div>
</template>
