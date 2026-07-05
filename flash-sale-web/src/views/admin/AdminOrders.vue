<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

interface AdminOrder {
  id: number
  userId: number
  username: string
  productId: number
  productName: string
  productImageUrl: string
  seckillActivityId: number
  seckillPrice: number
  quantity: number
  totalAmount: number
  status: string
  createdAt: string
  updatedAt: string
}

const router = useRouter()
const orders = ref<AdminOrder[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const shipLoading = ref(new Set<number>())
const statusFilter = ref('')

function formatPrice(p: number) { return '¥' + (p / 100).toFixed(2) }

function statusTag(s: string) {
  const map: Record<string, { type: string; text: string }> = {
    PENDING: { type: 'warning', text: '处理中' },
    PENDING_PAY: { type: 'warning', text: '待付款' },
    PAID: { type: '', text: '已付款' },
    SHIPPED: { type: 'primary', text: '已发货' },
    RECEIVED: { type: 'success', text: '已收货' },
    COMPLETED: { type: 'success', text: '已完成' },
    SUCCESS: { type: 'success', text: '交易成功' },
    FAILED: { type: 'danger', text: '交易失败' },
    CANCELLED: { type: 'info', text: '已取消' }
  }
  return map[s] || { type: 'info', text: s }
}

async function loadOrders() {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    if (statusFilter.value) params.status = statusFilter.value
    const res: any = await request.get('/api/admin/order/list', { params })
    orders.value = res?.records ?? []
    total.value = res?.total ?? 0
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

async function shipOrder(orderId: number) {
  shipLoading.value.add(orderId)
  try {
    await request.post(`/api/admin/order/${orderId}/ship`)
    ElMessage.success('发货成功')
    loadOrders()
  } catch {
    ElMessage.error('发货失败')
  } finally {
    shipLoading.value.delete(orderId)
  }
}

function viewDetail(orderId: number) {
  router.push(`/order/${orderId}`)
}

onMounted(() => loadOrders())
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h3 style="margin: 0;">📋 订单管理</h3>
      <div style="display: flex; gap: 8px;">
        <el-select v-model="statusFilter" placeholder="按状态筛选" clearable style="width: 140px;" @change="loadOrders">
          <el-option label="全部" value="" />
          <el-option label="待付款" value="PENDING_PAY" />
          <el-option label="已付款" value="PAID" />
          <el-option label="已发货" value="SHIPPED" />
          <el-option label="已收货" value="RECEIVED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="处理中" value="PENDING" />
          <el-option label="交易成功" value="SUCCESS" />
          <el-option label="交易失败" value="FAILED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadOrders">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="orders" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="订单号" width="150" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="productName" label="商品" min-width="160" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">{{ formatPrice(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="60" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type as any" size="small">{{ statusTag(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 'PAID'" link type="success"
                       :loading="shipLoading.has(row.id)"
                       @click="shipOrder(row.id)">发货</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: center;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="loadOrders"
        />
      </div>
    </el-card>
  </div>
</template>
