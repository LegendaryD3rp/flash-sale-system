<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import { createSeckillWs } from '../utils/websocket'
import { ElMessage } from 'element-plus'
import { Timer, Lightning } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activity = ref<any>(null)
const product = ref<any>(null)
const loading = ref(true)
const hasBought = ref(false)
const isSubmitting = ref(false)
const flashSuccess = ref(false)
const orderSn = ref('')
let ws: WebSocket | null = null

// ========== Timer ==========
const now = ref(Date.now())
let timer: number | null = null

onMounted(async () => {
  timer = window.setInterval(() => { now.value = Date.now() }, 1000)
  await fetchActivity()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (ws) ws.close()
})

async function fetchActivity() {
  loading.value = true
  try {
    const activityId = Number(route.params.id)
    const res: any = await request.get(`/api/seckill/activity/${activityId}`)
    activity.value = res
    // Fetch product info
    if (res.productId) {
      try {
        const prod: any = await request.get(`/api/product/${res.productId}`)
        product.value = prod
      } catch { /* product detail optional */ }
    }
  } catch {
    activity.value = null
  } finally {
    loading.value = false
  }
}

const startTime = computed(() => activity.value ? new Date(activity.value.startTime).getTime() : 0)
const endTime = computed(() => activity.value ? new Date(activity.value.endTime).getTime() : 0)

const timeStatus = computed(() => {
  if (!activity.value) return 'AFTER'
  if (now.value < startTime.value) return 'BEFORE'
  if (now.value >= startTime.value && now.value <= endTime.value) return 'DURING'
  return 'AFTER'
})

const countdownText = computed(() => {
  const diff = timeStatus.value === 'BEFORE'
    ? startTime.value - now.value
    : endTime.value - now.value
  if (diff <= 0) return '00:00:00'
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const remainingStock = computed(() => {
  if (!activity.value) return 0
  return activity.value.remainingStock ?? activity.value.availableStock ?? 0
})

// ========== Button State Machine ==========
type ButtonState = 'NOT_LOGGED_IN' | 'ENDED' | 'NOT_STARTED' | 'CAN_BUY' | 'SOLD_OUT' | 'ALREADY_BOUGHT' | 'SUBMITTING' | 'SUCCESS'

const buttonState = computed<ButtonState>(() => {
  if (!userStore.isLoggedIn()) return 'NOT_LOGGED_IN'
  if (flashSuccess.value) return 'SUCCESS'
  if (isSubmitting.value) return 'SUBMITTING'
  if (timeStatus.value === 'AFTER') return 'ENDED'
  if (hasBought.value) return 'ALREADY_BOUGHT'
  if (remainingStock.value <= 0) return 'SOLD_OUT'
  if (timeStatus.value === 'BEFORE') return 'NOT_STARTED'
  return 'CAN_BUY'
})

const buttonConfig = computed(() => {
  const map: Record<ButtonState, { text: string; color: string; disabled: boolean }> = {
    NOT_LOGGED_IN: { text: '请先登录', color: '#909399', disabled: true },
    ENDED: { text: '活动已结束', color: '#909399', disabled: true },
    NOT_STARTED: { text: '即将开始', color: '#e6a23c', disabled: true },
    CAN_BUY: { text: '⚡ 立即秒杀', color: '#b2070a', disabled: false },
    SOLD_OUT: { text: '已售罄', color: '#909399', disabled: true },
    ALREADY_BOUGHT: { text: '您已抢购', color: '#909399', disabled: true },
    SUBMITTING: { text: '排队中...', color: '#909399', disabled: true },
    SUCCESS: { text: '✅ 抢购成功！查看订单', color: '#67c23a', disabled: false },
  }
  return map[buttonState.value] || map.ENDED
})

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

async function handleFlash() {
  if (buttonState.value === 'SUCCESS') {
    router.push('/order')
    return
  }
  if (buttonState.value === 'NOT_LOGGED_IN') {
    router.push('/login?redirect=' + encodeURIComponent(route.fullPath))
    return
  }
  if (buttonState.value !== 'CAN_BUY') return

  isSubmitting.value = true
  try {
    const res: any = await request.post('/api/seckill/flash', {
      activityId: activity.value?.id
    })
    flashSuccess.value = true
    ElMessage.success('秒杀请求已受理，等待结果...')
    // Connect WebSocket for real-time result
    ws = createSeckillWs(activity.value?.id, {
      onMessage(data) {
        if (data.orderId || data.orderSn) {
          orderSn.value = data.orderId || data.orderSn
        }
      }
    })
  } catch (e: any) {
    const msg = e.message || '抢购失败'
    ElMessage.error(msg)
    if (msg.includes('重复')) hasBought.value = true
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div style="max-width: 600px; margin: 0 auto;">
    <el-button link @click="router.push('/')" style="margin-bottom: 16px;">← 返回首页</el-button>

    <!-- Loading -->
    <div v-if="loading" style="text-align: center; padding: 80px 0;">
      <el-icon class="is-loading" :size="32" color="#b2070a"><Lightning /></el-icon>
      <p style="color: #999;">加载中...</p>
    </div>

    <!-- Not Found -->
    <el-empty v-else-if="!activity" description="活动不存在或已结束" />

    <!-- Activity Detail -->
    <div v-else>
      <el-card shadow="never" style="border: 2px solid #b2070a;">
        <!-- Header -->
        <div style="text-align: center; padding: 10px 0;">
          <el-tag type="danger" size="large" effect="dark">⚡ 限时秒杀</el-tag>
        </div>

        <!-- Product Info -->
        <div style="text-align: center; padding: 20px 0;">
          <div style="font-size: 18px; font-weight: bold; margin-bottom: 8px;">
            {{ product?.name || `商品 #${activity.productId}` }}
          </div>
          <div style="display: flex; justify-content: center; align-items: baseline; gap: 12px;">
            <span style="font-size: 36px; color: #b2070a; font-weight: bold;">
              {{ formatPrice(activity.seckillPrice) }}
            </span>
            <span style="color: #999; text-decoration: line-through; font-size: 18px;">
              {{ activity.originalPrice ? formatPrice(activity.originalPrice) : '' }}
            </span>
          </div>
        </div>

        <!-- Stock Bar -->
        <div style="margin: 0 20px 16px;">
          <el-progress
            :percentage="Math.round((activity.availableStock / activity.totalStock) * 100)"
            :stroke-width="12"
            :text-inside="true"
            :status="remainingStock < 10 ? 'exception' : 'success'"
          >
            {{ activity.availableStock }}/{{ activity.totalStock }} 件
          </el-progress>
        </div>

        <!-- Countdown -->
        <div style="text-align: center; padding: 10px 0; background: #fff5f5; border-radius: 4px; margin: 0 20px 16px;">
          <el-icon style="vertical-align: middle;"><Timer /></el-icon>
          <span v-if="timeStatus === 'BEFORE'">距开始 </span>
          <span v-else-if="timeStatus === 'DURING'">距结束 </span>
          <span v-else>已结束</span>
          <span v-if="timeStatus !== 'AFTER'" style="font-size: 24px; font-weight: bold; color: #b2070a; margin-left: 8px;">
            {{ countdownText }}
          </span>
        </div>

        <!-- Flash Button -->
        <div style="padding: 0 20px 20px;">
          <el-button
            :type="buttonState === 'CAN_BUY' || buttonState === 'NOT_STARTED' ? 'danger' : 'default'"
            size="large"
            style="width: 100%; height: 50px; font-size: 18px;"
            :disabled="buttonConfig.disabled"
            @click="handleFlash"
          >
            {{ buttonConfig.text }}
          </el-button>
        </div>

        <!-- Success Info -->
        <div v-if="flashSuccess" style="text-align: center; padding-bottom: 16px; color: #67c23a;">
          <el-tag type="success" size="large">🎉 已为您排队，订单处理中...</el-tag>
        </div>
      </el-card>

      <!-- Product Detail Link -->
      <div style="margin-top: 16px; text-align: center;">
        <el-button link type="primary" @click="router.push(`/product/${activity.productId}`)">
          查看商品详情 →
        </el-button>
      </div>
    </div>
  </div>
</template>
