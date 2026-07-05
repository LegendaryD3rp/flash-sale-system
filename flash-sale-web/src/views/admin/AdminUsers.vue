<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, Plus } from '@element-plus/icons-vue'

interface AdminUser {
  id: number
  username: string
  email: string
  phone: string
  role: string
  status: string
  createdAt: string
  updatedAt: string
}

const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

// Edit dialog
const editDialogVisible = ref(false)
const editUser = ref<AdminUser | null>(null)
const editForm = ref({
  username: '',
  status: ''
})

function statusTag(s: string) {
  const map: Record<string, { type: string; text: string }> = {
    ACTIVE: { type: 'success', text: '正常' },
    INACTIVE: { type: 'warning', text: '未激活' },
    DISABLED: { type: 'danger', text: '已禁用' }
  }
  return map[s] || { type: 'info', text: s }
}

function roleTag(r: string) {
  return r === 'ADMIN' ? { type: 'danger', text: '管理员' } : { type: 'primary', text: '用户' }
}

async function loadUsers() {
  loading.value = true
  try {
    const res: any = await request.get('/api/admin/user/list', {
      params: { page: page.value, size: size.value }
    })
    users.value = res?.records ?? []
    total.value = res?.total ?? 0
  } catch {
    users.value = []
  } finally {
    loading.value = false
  }
}

function openEdit(user: AdminUser) {
  editUser.value = user
  editForm.value = {
    username: user.username,
    status: user.status
  }
  editDialogVisible.value = true
}

async function saveEdit() {
  if (!editUser.value) return
  try {
    await request.put(`/api/admin/user/${editUser.value.id}`, editForm.value)
    ElMessage.success('修改成功')
    editDialogVisible.value = false
    loadUsers()
  } catch {
    ElMessage.error('修改失败')
  }
}

async function disableUser(user: AdminUser) {
  try {
    await ElMessageBox.confirm(
      `确定要禁用用户「${user.username}」吗？禁用后该用户将无法登录。`,
      '确认禁用',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await request.delete(`/api/admin/user/${user.id}`)
    ElMessage.success('已禁用')
    loadUsers()
  } catch {
    // cancelled or error
  }
}

function formatRole(r: string) {
  return r === 'ADMIN' ? '管理员' : '用户'
}

onMounted(() => loadUsers())
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h3 style="margin: 0;">👥 用户管理</h3>
    </div>

    <!-- Table -->
    <el-table :data="users" v-loading="loading" stripe border style="width: 100%;">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="role" label="角色" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="roleTag(row.role).type" size="small">{{ roleTag(row.role).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" :icon="Edit" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button
            type="danger"
            size="small"
            :icon="Delete"
            :disabled="row.status === 'DISABLED'"
            @click="disableUser(row)"
          >
            {{ row.status === 'DISABLED' ? '已禁用' : '禁用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
    <div style="display: flex; justify-content: center; margin-top: 20px;">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadUsers"
        @size-change="loadUsers"
      />
    </div>

    <!-- Edit Dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑用户" width="400px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" placeholder="输入新用户名" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" placeholder="选择状态" style="width: 100%;">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="未激活" value="INACTIVE" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
:deep(.el-table th) {
  background-color: #f5f7fa;
}
</style>
