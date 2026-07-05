<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import { User, Edit, Lock, StarFilled, Coin } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const userInfo = ref<any>({})
const loading = ref(false)

// 编辑资料表单
const editForm = ref({ nickname: '', email: '' })
const editDialogVisible = ref(false)
const editLoading = ref(false)

// 修改密码表单
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordDialogVisible = ref(false)
const passwordLoading = ref(false)

async function fetchUserInfo() {
  loading.value = true
  try {
    const data: any = await request.get('/api/user/me')
    userInfo.value = data
    editForm.value.nickname = data.nickname || ''
    editForm.value.email = data.email || ''
  } catch {
    userInfo.value = {}
  } finally {
    loading.value = false
  }
}

async function handleEditProfile() {
  editLoading.value = true
  try {
    await request.put('/api/user/profile', {
      nickname: editForm.value.nickname,
      email: editForm.value.email
    })
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    fetchUserInfo()
  } catch {
    // Error handled by interceptor
  } finally {
    editLoading.value = false
  }
}

async function handleChangePassword() {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.error('新密码至少6位')
    return
  }
  passwordLoading.value = true
  try {
    await request.put('/api/user/password', {
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordDialogVisible.value = false
    userStore.logout()
    router.push('/login')
  } catch {
    // Error handled by interceptor
  } finally {
    passwordLoading.value = false
  }
}

onMounted(fetchUserInfo)
</script>

<template>
  <div style="max-width: 600px; margin: 40px auto;">
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px;">
          <el-icon :size="24" color="#409eff"><User /></el-icon>
          <span style="font-size: 18px; font-weight: bold;">个人中心</span>
        </div>
      </template>

      <div v-loading="loading">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">
            {{ userInfo.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="昵称">
            {{ userInfo.nickname || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ userInfo.email || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag :type="userInfo.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
              {{ userInfo.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">
            {{ userInfo.createdAt || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 24px; display: flex; gap: 12px;">
          <el-button type="primary" :icon="Edit" @click="editDialogVisible = true">
            编辑资料
          </el-button>
          <el-button :icon="Lock" @click="passwordDialogVisible = true">
            修改密码
          </el-button>
          <el-button @click="router.push('/favorites')">
            <el-icon style="vertical-align: middle; margin-right: 4px;"><StarFilled /></el-icon>我的收藏
          </el-button>
          <el-button @click="router.push('/coupon')" style="margin-left: 12px;">
            <el-icon style="vertical-align: middle; margin-right: 4px;"><Coin /></el-icon>我的优惠券
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 编辑资料对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑资料" width="400px">
      <el-form :model="editForm" label-position="top">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" placeholder="设置昵称" maxlength="100" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="设置邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleEditProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="400px">
      <el-form :model="passwordForm" label-position="top">
        <el-form-item label="旧密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handleChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>
