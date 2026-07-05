<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { Search } from '@element-plus/icons-vue'

interface AuditLogRecord {
  id: number
  userId: number
  username: string
  role: string
  module: string
  action: string
  description: string
  requestMethod: string
  requestUrl: string
  ip: string
  success: boolean
  createdAt: string
}

const logs = ref<AuditLogRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const filterModule = ref('')
const filterAction = ref('')
const filterUsername = ref('')

const moduleOptions = ['用户管理', '商品管理', '审计日志']
const actionOptions = ['新增', '修改', '删除', '查询']

onMounted(() => {
  fetchLogs()
})

async function fetchLogs() {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    if (filterModule.value) params.module = filterModule.value
    if (filterAction.value) params.action = filterAction.value
    if (filterUsername.value) params.username = filterUsername.value
    const data: any = await request.get('/api/admin/audit-log', { params })
    logs.value = data.records || []
    total.value = data.total || 0
  } catch {
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchLogs()
}

function handleReset() {
  filterModule.value = ''
  filterAction.value = ''
  filterUsername.value = ''
  page.value = 1
  fetchLogs()
}

function onPageChange(newPage: number) {
  page.value = newPage
  fetchLogs()
}

function formatTime(time: string) {
  if (!time) return ''
  return time.substring(0, 19).replace('T', ' ')
}
</script>

<template>
  <div>
    <h3 style="margin-bottom: 16px;">操作日志审计</h3>

    <!-- 筛选 -->
    <el-card style="margin-bottom: 16px;">
      <el-form :inline="true" :model="{ module: filterModule, action: filterAction, username: filterUsername }">
        <el-form-item label="模块">
          <el-select v-model="filterModule" placeholder="全部模块" clearable style="width: 150px;">
            <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filterAction" placeholder="全部操作" clearable style="width: 150px;">
            <el-option v-for="a in actionOptions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="filterUsername" placeholder="模糊搜索" clearable style="width: 150px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card>
      <el-table :data="logs" v-loading="loading" stripe style="width: 100%;" max-height="600">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="action" label="操作" width="80" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="username" label="操作人" width="100" />
        <el-table-column prop="requestMethod" label="方法" width="80" />
        <el-table-column prop="requestUrl" label="请求路径" min-width="200" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: center;">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>
