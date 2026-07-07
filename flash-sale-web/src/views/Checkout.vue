<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const cartItems = ref<any[]>([])
const addresses = ref<any[]>([])
const selectedAddressId = ref<number | null>(null)
const loading = ref(false)
const submitting = ref(false)

// 优惠券
const availableCoupons = ref<any[]>([])
const selectedUserCouponId = ref<number | null>(null)
const selectedDiscount = ref(0)

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

const totalPrice = computed(() => {
  return cartItems.value.reduce((sum: number, i: any) => sum + (i.subtotal || 0), 0)
})

const finalPrice = computed(() => {
  return Math.max(0, totalPrice.value - selectedDiscount.value)
})

function fullAddress(addr: any) {
  return [addr.province, addr.city, addr.district, addr.detailAddress].filter(Boolean).join(' ')
}

onMounted(async () => {
  loading.value = true
  try {
    const cartIdsStr = route.query.cartIds as string
    if (!cartIdsStr) {
      ElMessage.warning('请选择要结算的商品')
      router.push('/cart')
      return
    }

    const res: any = await request.get('/api/cart/listByIds', { params: { ids: cartIdsStr } })
    cartItems.value = res || []

    if (cartItems.value.length === 0) {
      ElMessage.warning('购物车项不存在')
      router.push('/cart')
      return
    }

    // 获取地址列表
    const addrs: any = await request.get('/api/address/list')
    addresses.value = addrs || []
    const defaultAddr = addresses.value.find((a: any) => a.isDefault === 1)
    if (defaultAddr) {
      selectedAddressId.value = defaultAddr.id
    } else if (addresses.value.length > 0) {
      selectedAddressId.value = addresses.value[0].id
    }

    // 加载可用优惠券
    await loadAvailableCoupons()
  } catch {
    ElMessage.error('加载失败')
    router.push('/cart')
  } finally {
    loading.value = false
  }
})

async function loadAvailableCoupons() {
  try {
    const total = cartItems.value.reduce((sum: number, i: any) => sum + (i.subtotal || 0), 0)
    const list: any = await request.get('/api/coupon/available', { params: { amount: total } })
    availableCoupons.value = list || []
  } catch {
    availableCoupons.value = []
  }
}

async function selectCoupon(userCouponId: number | null) {
  selectedUserCouponId.value = userCouponId
  if (userCouponId) {
    try {
      const total = totalPrice.value
      const data: any = await request.post('/api/coupon/calc-discount', { userCouponId, amount: total })
      selectedDiscount.value = data.discount || 0
    } catch {
      selectedDiscount.value = 0
      selectedUserCouponId.value = null
    }
  } else {
    selectedDiscount.value = 0
  }
}

function unselectCoupon() {
  selectCoupon(null)
}

async function submitOrder() {
  if (!selectedAddressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  submitting.value = true
  try {
    const cartIds = cartItems.value.map((i: any) => i.id)
    const body: any = {
      cartIds,
      addressId: selectedAddressId.value
    }
    if (selectedUserCouponId.value) {
      body.userCouponId = selectedUserCouponId.value
    }
    const res: any = await request.post('/api/order/create', body)
    ElMessage.success('下单成功！共 ' + (res || []).length + ' 笔订单')
    router.push('/order')
  } catch {
    ElMessage.error('下单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div style="max-width: 1000px; margin: 0 auto;">
    <h3 style="margin-bottom: 16px;">📝 确认订单</h3>

    <div v-if="loading" style="text-align: center; padding: 60px 0; color: #999;">加载中...</div>

    <template v-else>
      <el-row :gutter="24">
        <!-- 左侧：商品清单 + 优惠券 -->
        <el-col :span="16">
          <el-card shadow="never" style="margin-bottom: 16px;">
            <template #header>商品清单</template>
            <div v-for="item in cartItems" :key="item.id"
                 style="display: flex; align-items: center; gap: 16px; padding: 12px 0; border-bottom: 1px solid #f0f0f0;">
              <div style="width: 60px; height: 60px; background: #f5f7fa; border-radius: 4px; display: flex; align-items: center; justify-content: center; overflow: hidden; flex-shrink: 0;">
                <img v-if="item.productImageUrl" :src="item.productImageUrl" style="max-width: 100%; max-height: 100%;" />
                <span v-else>📦</span>
              </div>
              <div style="flex: 1; min-width: 0;">
                <div style="font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                  {{ item.productName }}
                </div>
                <div style="color: #999; font-size: 13px; margin-top: 4px;">
                  {{ formatPrice(item.productPrice) }} × {{ item.quantity }}
                </div>
              </div>
              <div style="color: #b2070a; font-weight: bold; flex-shrink: 0;">
                {{ formatPrice(item.subtotal) }}
              </div>
            </div>

            <!-- 价格合计 + 优惠 -->
            <div style="display: flex; flex-direction: column; align-items: flex-end; padding-top: 16px; gap: 4px;">
              <div style="color: #666;">
                合计：<span style="color: #b2070a; font-size: 20px; font-weight: bold;">{{ formatPrice(totalPrice) }}</span>
              </div>
              <div v-if="selectedDiscount > 0" style="color: #67c23a;">
                优惠券减免：-{{ formatPrice(selectedDiscount) }}
              </div>
              <div v-if="selectedDiscount > 0" style="color: #b2070a; font-size: 18px; font-weight: bold; border-top: 1px dashed #eee; padding-top: 8px;">
                实付：{{ formatPrice(finalPrice) }}
              </div>
            </div>
          </el-card>

          <!-- 优惠券区域 -->
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>🎫 优惠券</span>
                <router-link to="/coupon" style="font-size: 13px;">我的优惠券</router-link>
              </div>
            </template>
            <div v-if="availableCoupons.length === 0" style="padding: 16px 0; text-align: center; color: #999;">
              暂无可用优惠券
            </div>
            <div v-else>
              <div v-if="selectedUserCouponId" style="margin-bottom: 8px;">
                <el-tag closable type="success" @close="unselectCoupon">
                  已选：{{ availableCoupons.find((c: any) => c.id === selectedUserCouponId)?.couponName || '优惠券' }}
                </el-tag>
              </div>
              <div v-for="c in availableCoupons" :key="c.id"
                   :class="['coupon-option', { active: selectedUserCouponId === c.id, disabled: !c.available }]"
                   @click="c.available && selectCoupon(c.id)">
                <div class="coupon-opt-left">
                  <div class="coupon-opt-amount">
                    {{ c.type === 'PERCENT' ? c.discount + '%' : formatPrice(c.discount) }}
                  </div>
                  <div class="coupon-opt-label">{{ c.type === 'PERCENT' ? '折扣' : '立减' }}</div>
                </div>
                <div class="coupon-opt-right">
                  <div class="coupon-opt-name">{{ c.couponName }}</div>
                  <div class="coupon-opt-threshold">
                    {{ c.minAmount > 0 ? '满' + formatPrice(c.minAmount) : '无门槛' }}
                  </div>
                  <div class="coupon-opt-status" v-if="!c.available">未满足使用条件</div>
                  <div class="coupon-opt-status" v-else-if="c.estimatedDiscount > 0">预估可减 {{ formatPrice(c.estimatedDiscount) }}</div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>

        <!-- 右侧：收货地址 -->
        <el-col :span="8">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>收货地址</span>
                <router-link to="/address" style="font-size: 13px;">管理</router-link>
              </div>
            </template>
            <div v-if="addresses.length === 0" style="padding: 16px 0; text-align: center; color: #999;">
              暂无地址
              <router-link to="/address" style="display: block; margin-top: 8px;">去添加</router-link>
            </div>
            <el-radio-group v-else v-model="selectedAddressId" style="width: 100%;">
              <div v-for="addr in addresses" :key="addr.id"
                   style="padding: 8px 0; border-bottom: 1px solid #f5f5f5;">
                <el-radio :value="addr.id" style="display: flex; align-items: flex-start; width: 100%;">
                  <div>
                    <div>
                      <strong>{{ addr.receiverName }}</strong>
                      <span style="margin-left: 8px; color: #666;">{{ addr.receiverPhone }}</span>
                    </div>
                    <div style="font-size: 13px; color: #999; margin-top: 2px;">{{ fullAddress(addr) }}</div>
                  </div>
                </el-radio>
              </div>
            </el-radio-group>
          </el-card>
        </el-col>
      </el-row>

      <!-- 提交按钮 -->
      <div style="text-align: right; margin-top: 24px;">
        <el-button style="margin-right: 12px;" @click="router.push('/cart')">返回购物车</el-button>
        <el-button type="primary" size="large" :loading="submitting" @click="submitOrder">
          {{ selectedDiscount > 0 ? '提交订单（省' + formatPrice(selectedDiscount) + '）' : '提交订单' }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
  .coupon-option {
    display: flex;
    padding: 10px;
    margin-bottom: 8px;
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s;
  }
  .coupon-option:hover:not(.disabled) {
    border-color: #b2070a;
    background: #fff5f5;
  }
  .coupon-option.active {
    border-color: #b2070a;
    background: #fff0f0;
  }
  .coupon-option.disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  .coupon-opt-left {
    width: 80px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border-right: 1px dashed #e4e7ed;
    margin-right: 12px;
  }
  .coupon-opt-amount {
    font-size: 20px;
    font-weight: bold;
    color: #b2070a;
  }
  .coupon-opt-label {
    font-size: 11px;
    color: #999;
  }
  .coupon-opt-right {
    flex: 1;
  }
  .coupon-opt-name {
    font-weight: 500;
    font-size: 14px;
  }
  .coupon-opt-threshold {
    font-size: 12px;
    color: #666;
    margin-top: 2px;
  }
  .coupon-opt-status {
    font-size: 12px;
    color: #67c23a;
    margin-top: 4px;
  }
  .coupon-option.disabled .coupon-opt-status {
    color: #e6a23c;
  }
  </style>
