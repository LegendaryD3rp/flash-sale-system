<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { StarFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const order = ref<any>(null)
const loading = ref(true)
const actionLoading = ref(false)
const reviewed = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const orderId = route.params.id
    const res: any = await request.get(`/api/order/${orderId}`)
    order.value = res
    // 检查是否已评价
    try {
      const check: any = await request.get('/api/product/review/check', { params: { orderId: Number(orderId) } })
      reviewed.value = check.reviewed === true
    } catch {
      reviewed.value = false
    }
  } catch {
    order.value = null
  } finally {
    loading.value = false
  }
})

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

function statusText(status: string) {
  const map: Record<string, string> = {
    PENDING: '处理中',
    PENDING_PAY: '待付款',
    PAID: '已付款',
    SHIPPED: '已发货',
    RECEIVED: '已收货',
    COMPLETED: '已完成',
    SUCCESS: '交易成功',
    FAILED: '交易失败',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

function statusType(status: string) {
  if (['SUCCESS', 'COMPLETED', 'RECEIVED', 'PAID'].includes(status)) return 'success'
  if (['FAILED', 'CANCELLED'].includes(status)) return 'danger'
  if (['PENDING_PAY'].includes(status)) return 'warning'
  if (['SHIPPED'].includes(status)) return 'primary'
  return 'info'
}

function showPay() { return order.value?.status === 'PENDING_PAY' }
function showCancel() { return order.value?.status === 'PENDING_PAY' }
function showReceive() { return order.value?.status === 'SHIPPED' }
function showReview() {
  return (order.value?.status === 'RECEIVED' || order.value?.status === 'COMPLETED' || order.value?.status === 'SUCCESS')
    && !reviewed.value
}

async function payOrder() {
  actionLoading.value = true
  try {
    await request.post(`/api/order/${order.value.id}/pay`)
    ElMessage.success('支付成功')
    const res: any = await request.get(`/api/order/${order.value.id}`)
    order.value = res
  } catch {
    ElMessage.error('支付失败')
  } finally {
    actionLoading.value = false
  }
}

async function cancelOrder() {
  actionLoading.value = true
  try {
    await request.post(`/api/order/${order.value.id}/cancel`)
    ElMessage.success('订单已取消')
    const res: any = await request.get(`/api/order/${order.value.id}`)
    order.value = res
  } catch {
    ElMessage.error('取消失败')
  } finally {
    actionLoading.value = false
  }
}

async function receiveOrder() {
  actionLoading.value = true
  try {
    await request.post(`/api/order/${order.value.id}/receive`)
    ElMessage.success('已确认收货')
    const res: any = await request.get(`/api/order/${order.value.id}`)
    order.value = res
  } catch {
    ElMessage.error('操作失败')
  } finally {
    actionLoading.value = false
  }
}

// 评价弹窗
const reviewDialogVisible = ref(false)
const reviewForm = ref({ rating: 5, content: '' })
const reviewLoading = ref(false)

function openReviewDialog() {
  reviewForm.value = { rating: 5, content: '' }
  reviewDialogVisible.value = true
}

async function submitReview() {
  if (!reviewForm.value.content.trim()) {
    ElMessage.warning('请输入评价内容')
    return
  }
  reviewLoading.value = true
  try {
    await request.post('/api/product/review', {
      productId: order.value.productId,
      orderId: order.value.id,
      rating: reviewForm.value.rating,
      content: reviewForm.value.content
    })
    ElMessage.success('评价成功')
    reviewDialogVisible.value = false
    reviewed.value = true
  } catch {
    // handled by interceptor
  } finally {
    reviewLoading.value = false
  }
}

function formatTime(time: string) {
  if (!time) return ''
  return time.substring(0, 19).replace('T', ' ')
}

function fullAddress() {
  if (!order.value) return ''
  const parts = [order.value.deliveryProvince, order.value.deliveryCity, order.value.deliveryDistrict, order.value.deliveryAddress]
  return parts.filter(Boolean).join(' ')
}
</script>

<template>
  <div style="max-width: 800px; margin: 0 auto;">
    <el-button link @click="router.push('/order')" style="margin-bottom: 16px;">← 返回订单列表</el-button>

    <div v-if="loading" style="text-align: center; padding: 80px 0; color: #999;">加载中...</div>

    <div v-else-if="order">
      <el-card>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <h3 style="margin: 0;">订单详情</h3>
            <div style="display: flex; gap: 8px; align-items: center;">
              <el-tag :type="statusType(order.status) as any">
                {{ statusText(order.status) }}
              </el-tag>
              <el-tag v-if="reviewed" type="success" size="small">已评价</el-tag>
            </div>
          </div>
        </template>

        <div style="display: flex; gap: 24px; flex-wrap: wrap;">
          <div style="width: 120px; height: 120px; border-radius: 8px; background: #f5f5f5; display: flex; align-items: center; justify-content: center; font-size: 48px; flex-shrink: 0;">
            📦
          </div>
          <div style="flex: 1; min-width: 250px;">
            <h4>{{ order.productName || `商品 #${order.productId}` }}</h4>
            <el-descriptions :column="1" border style="margin-top: 16px;">
              <el-descriptions-item label="订单号">{{ order.id }}</el-descriptions-item>
              <el-descriptions-item label="数量">{{ order.quantity || 1 }}</el-descriptions-item>
              <el-descriptions-item label="总金额">
                <span style="color: #b2070a; font-weight: bold; font-size: 18px;">{{ formatPrice(order.totalAmount) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="收货人" v-if="order.receiverName">
                {{ order.receiverName }} {{ order.receiverPhone }}
              </el-descriptions-item>
              <el-descriptions-item label="收货地址" v-if="order.deliveryAddress">
                {{ fullAddress() }}
              </el-descriptions-item>
              <el-descriptions-item label="下单时间">{{ formatTime(order.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatTime(order.updatedAt) }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div v-if="showPay() || showCancel() || showReceive() || showReview()" style="margin-top: 16px; display: flex; gap: 12px; justify-content: flex-end; border-top: 1px solid #eee; padding-top: 16px;">
          <el-button v-if="showCancel()" :loading="actionLoading" @click="cancelOrder">取消订单</el-button>
          <el-button v-if="showPay()" type="primary" :loading="actionLoading" @click="payOrder">去支付</el-button>
          <el-button v-if="showReceive()" type="success" :loading="actionLoading" @click="receiveOrder">确认收货</el-button>
          <el-button v-if="showReview()" type="warning" :icon="StarFilled" @click="openReviewDialog">去评价</el-button>
        </div>
      </el-card>
    </div>

    <el-empty v-else description="订单不存在" />

    <!-- 评价弹窗 -->
    <el-dialog v-model="reviewDialogVisible" title="商品评价" width="450px">
      <el-form :model="reviewForm" label-position="top">
        <el-form-item label="评分">
          <el-rate v-model="reviewForm.rating" :max="5" />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="reviewForm.content" type="textarea" :rows="4" placeholder="分享您的使用体验..." maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewLoading" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>
