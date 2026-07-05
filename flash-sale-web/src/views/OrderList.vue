<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const orders = ref<any[]>([])
const loading = ref(false)

onMounted(async () => {
  if (!userStore.isLoggedIn()) {
    router.push('/login?redirect=/order')
    return
  }
  loading.value = true
  try {
    const res: any = await request.get('/api/order/list', { params: { page: 1, size: 20 } })
    orders.value = res.records || []
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
})

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

function statusTag(status: string) {
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
  return map[status] || { type: 'info', text: status }
}

function formatTime(time: string) {
  if (!time) return ''
  return time.substring(0, 19).replace('T', ' ')
}
</script>

<template>
  <div style="max-width: 1000px; margin: 0 auto;">
    <h2>📋 我的订单</h2>

    <div v-if="loading" style="text-align: center; padding: 60px 0; color: #999;">加载中...</div>

    <el-card v-for="o in orders" :key="o.id" shadow="hover"
             style="margin-bottom: 12px; cursor: pointer;"
             @click="router.push(`/order/${o.id}`)">
      <div style="display: flex; gap: 16px; align-items: center;">
        <div style="width: 80px; height: 80px; border-radius: 4px; background: #f5f5f5; display: flex; align-items: center; justify-content: center; font-size: 24px; flex-shrink: 0;">
          📦
        </div>
        <div style="flex: 1; min-width: 0;">
          <h4 style="margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            {{ o.productName || `商品 #${o.productId}` }}
          </h4>
          <span style="color: #999; font-size: 12px;">订单号：{{ o.id }}</span>
          <span v-if="o.quantity && o.quantity > 1" style="color: #999; font-size: 12px; margin-left: 8px;">×{{ o.quantity }}</span>
        </div>
        <div style="text-align: right; flex-shrink: 0;">
          <div style="color: #b2070a; font-weight: bold;">{{ formatPrice(o.totalAmount) }}</div>
          <el-tag :type="(statusTag(o.status).type || 'info') as any" size="small">
            {{ statusTag(o.status).text }}
          </el-tag>
          <div style="color: #999; font-size: 12px; margin-top: 4px;">{{ formatTime(o.createdAt) }}</div>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!loading && orders.length === 0" description="还没有订单，去秒杀吧！" />
  </div>
</template>
