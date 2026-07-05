<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'

interface MyCoupon {
  id: number
  couponId: number
  couponName: string
  type: string
  discount: number
  minAmount: number
  status: string
  claimedAt: string
  usedAt: string
  startTime: string
  endTime: string
}

const coupons = ref<MyCoupon[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const activeTab = ref('UNUSED')

onMounted(() => fetchCoupons())

async function fetchCoupons() {
  loading.value = true
  try {
    const data: any = await request.get('/api/coupon/mine', { params: { page: page.value, size: size.value } })
    // 前端过滤
    let list = data.records || []
    // 检查过期
    list = list.map((c: MyCoupon) => {
      if (c.status === 'UNUSED' && c.endTime && new Date(c.endTime) < new Date()) {
        c.status = 'EXPIRED'
      }
      return c
    })
    if (activeTab.value === 'UNUSED') {
      coupons.value = list.filter((c: MyCoupon) => c.status === 'UNUSED')
    } else if (activeTab.value === 'USED') {
      coupons.value = list.filter((c: MyCoupon) => c.status === 'USED')
    } else {
      coupons.value = list.filter((c: MyCoupon) => c.status === 'EXPIRED')
    }
    total.value = data.total || 0
  } catch {
    coupons.value = []
  } finally {
    loading.value = false
  }
}

function handleTabChange(tab: string) {
  activeTab.value = tab
  page.value = 1
  fetchCoupons()
}

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

function formatTime(time: string) {
  if (!time) return ''
  return time.substring(0, 16).replace('T', ' ')
}

function discountLabel(c: MyCoupon) {
  if (c.type === 'PERCENT') return c.discount + '%'
  return formatPrice(c.discount)
}

function thresholdLabel(c: MyCoupon) {
  return c.minAmount > 0 ? '满' + formatPrice(c.minAmount) : '无门槛'
}
</script>

<template>
  <div style="max-width: 800px; margin: 0 auto;">
    <h3 style="margin-bottom: 16px;">🎫 我的优惠券</h3>

    <el-tabs :model-value="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="未使用" name="UNUSED" />
      <el-tab-pane label="已使用" name="USED" />
      <el-tab-pane label="已过期" name="EXPIRED" />
    </el-tabs>

    <div v-if="loading" style="text-align: center; padding: 60px 0; color: #999;">加载中...</div>

    <div v-else-if="coupons.length === 0" style="text-align: center; padding: 60px 0; color: #999;">
      <div style="font-size: 48px; margin-bottom: 16px;">📭</div>
      <p>暂无{{ activeTab === 'UNUSED' ? '可用' : activeTab === 'USED' ? '已使用' : '已过期' }}优惠券</p>
      <el-button type="primary" @click="$router.push('/')">去领券</el-button>
    </div>

    <div v-else style="display: flex; flex-direction: column; gap: 12px;">
      <div v-for="c in coupons" :key="c.id" class="coupon-card">
        <div class="coupon-left">
          <div class="coupon-amount">{{ discountLabel(c) }}</div>
          <div class="coupon-type">{{ c.type === 'PERCENT' ? '折扣' : '立减' }}</div>
        </div>
        <div class="coupon-right">
          <div class="coupon-name">{{ c.couponName }}</div>
          <div class="coupon-threshold">{{ thresholdLabel(c) }}</div>
          <div class="coupon-time" v-if="c.status === 'UNUSED'">
            有效期：{{ formatTime(c.startTime) }} ~ {{ formatTime(c.endTime) }}
          </div>
          <div class="coupon-time" v-else-if="c.status === 'USED'">
            已使用 {{ formatTime(c.usedAt) }}
          </div>
          <div class="coupon-time" v-else>
            已过期
          </div>
        </div>
      </div>

      <div style="margin-top: 16px; display: flex; justify-content: center;">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="(p: number) => { page = p; fetchCoupons() }" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.coupon-card {
  display: flex;
  background: linear-gradient(135deg, #fff5f5 0%, #fff 100%);
  border: 1px solid #ffe0e0;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}
.coupon-card::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 140px;
  width: 16px;
  background: radial-gradient(circle at 0 50%, transparent 6px, #fff5f5 6px);
  background-size: 16px 16px;
  background-repeat: repeat-y;
}
.coupon-left {
  width: 130px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px;
  border-right: 1px dashed #ffd5d5;
  position: relative;
  z-index: 1;
}
.coupon-amount {
  font-size: 28px;
  font-weight: bold;
  color: #b2070a;
}
.coupon-type {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.coupon-right {
  flex: 1;
  padding: 16px 20px;
  position: relative;
  z-index: 1;
}
.coupon-name {
  font-weight: bold;
  font-size: 15px;
}
.coupon-threshold {
  font-size: 13px;
  color: #666;
  margin-top: 4px;
}
.coupon-time {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
}
</style>
