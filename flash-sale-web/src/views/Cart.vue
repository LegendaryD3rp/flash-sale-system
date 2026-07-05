<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import { ElMessage } from 'element-plus'
import { Delete, Minus, Plus, ShoppingCart } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const cartItems = ref<any[]>([])
const loading = ref(false)
const checkedIds = ref<Set<number>>(new Set())

interface CartItem {
  id: number
  productId: number
  productName: string
  productImageUrl: string
  productPrice: number
  quantity: number
  subtotal: number
  stock: number
  status: string
}

async function fetchCart() {
  loading.value = true
  try {
    const data: any = await request.get('/api/cart/list')
    cartItems.value = (data || []).map((item: CartItem) => ({
      ...item
    }))
    // 新加载时默认全选
    checkedIds.value = new Set(cartItems.value.map((i: CartItem) => i.id))
  } catch {
    cartItems.value = []
  } finally {
    loading.value = false
  }
}

async function updateQuantity(item: any, delta: number) {
  const newQty = item.quantity + delta
  if (newQty < 1) return
  if (newQty > item.stock) {
    ElMessage.warning('库存不足')
    return
  }
  try {
    const data: any = await request.put(`/api/cart/${item.id}`, { quantity: newQty })
    if (data) {
      item.quantity = data.quantity
      item.subtotal = data.subtotal
    } else {
      // 数量为0时被删除
      cartItems.value = cartItems.value.filter((i: CartItem) => i.id !== item.id)
      checkedIds.value.delete(item.id)
    }
  } catch {
    // 失败时重新加载
    fetchCart()
  }
}

async function deleteItem(item: any) {
  try {
    await request.delete(`/api/cart/${item.id}`)
    cartItems.value = cartItems.value.filter((i: CartItem) => i.id !== item.id)
    checkedIds.value.delete(item.id)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

async function clearCart() {
  if (cartItems.value.length === 0) return
  try {
    await request.post('/api/cart/clear')
    cartItems.value = []
    checkedIds.value = new Set()
    ElMessage.success('购物车已清空')
  } catch {
    ElMessage.error('清空失败')
  }
}

function toggleCheck(itemId: number) {
  if (checkedIds.value.has(itemId)) {
    checkedIds.value.delete(itemId)
  } else {
    checkedIds.value.add(itemId)
  }
  // 触发响应式
  checkedIds.value = new Set(checkedIds.value)
}

function toggleCheckAll() {
  if (checkedIds.value.size === cartItems.value.length) {
    checkedIds.value = new Set()
  } else {
    checkedIds.value = new Set(cartItems.value.map((i: CartItem) => i.id))
  }
}

const isAllChecked = computed(() => {
  return cartItems.value.length > 0 && checkedIds.value.size === cartItems.value.length
})

const totalPrice = computed(() => {
  return cartItems.value
    .filter((i: CartItem) => checkedIds.value.has(i.id))
    .reduce((sum: number, i: CartItem) => sum + (i.subtotal || 0), 0)
})

const selectedCount = computed(() => {
  return cartItems.value.filter((i: CartItem) => checkedIds.value.has(i.id)).length
})

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

function goCheckout() {
  if (selectedCount.value === 0) {
    ElMessage.warning('请选择要结算的商品')
    return
  }
  // 跳转到结算页面，携带选中的购物车项ID
  const ids = Array.from(checkedIds.value).join(',')
  router.push(`/checkout?cartIds=${ids}`)
}

function goToProduct(item: any) {
  router.push(`/product/${item.productId}`)
}

onMounted(() => {
  if (!userStore.isLoggedIn()) {
    router.push('/login')
    return
  }
  fetchCart()
})
</script>

<template>
  <div style="max-width: 1000px; margin: 0 auto;">
    <el-card shadow="never" style="margin-bottom: 16px;">
      <div style="display: flex; align-items: center; justify-content: space-between;">
        <h3 style="margin: 0;">
          <el-icon><ShoppingCart /></el-icon>
          购物车
          <span v-if="cartItems.length > 0" style="font-size: 14px; color: #999; font-weight: normal; margin-left: 8px;">共 {{ cartItems.length }} 件商品</span>
        </h3>
        <el-button text type="danger" :disabled="cartItems.length === 0" @click="clearCart">
          清空购物车
        </el-button>
      </div>
    </el-card>

    <!-- Loading -->
    <div v-if="loading" style="text-align: center; padding: 60px 0;">
      <el-icon class="is-loading" :size="32">
        <svg viewBox="0 0 1024 1024" width="32" height="32"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z" fill="currentColor"/></svg>
        </el-icon>
        <p style="color: #999;">加载中...</p>
      </div>

    <!-- Empty Cart -->
    <el-empty v-else-if="cartItems.length === 0" description="购物车是空的">
      <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
    </el-empty>

    <!-- Cart List -->
    <template v-else>
      <div style="margin-bottom: 16px;">
        <el-checkbox :model-value="isAllChecked" @change="toggleCheckAll" style="margin-bottom: 12px;">
          全选
        </el-checkbox>
      </div>

      <div v-for="item in cartItems" :key="item.id" style="margin-bottom: 12px;">
        <el-card shadow="hover">
          <div style="display: flex; align-items: center; gap: 16px;">
            <!-- Checkbox -->
            <el-checkbox :model-value="checkedIds.has(item.id)" @change="toggleCheck(item.id)" />

            <!-- Product Image -->
            <div style="width: 80px; height: 80px; background: #f5f7fa; border-radius: 4px; display: flex; align-items: center; justify-content: center; overflow: hidden; cursor: pointer; flex-shrink: 0;" @click="goToProduct(item)">
              <img v-if="item.productImageUrl"
                   :src="item.productImageUrl"
                   :alt="item.productName"
                   style="width: 100%; height: 100%; object-fit: cover;" />
              <span v-else style="font-size: 28px; color: #ccc;">📦</span>
            </div>

            <!-- Product Info -->
            <div style="flex: 1; min-width: 0;">
              <div style="font-weight: bold; cursor: pointer;" @click="goToProduct(item)">{{ item.productName }}</div>
              <div style="color: #999; font-size: 12px; margin-top: 4px;">
                <el-tag size="small" type="info">{{ item.productCategory }}</el-tag>
              </div>
              <div style="color: #b2070a; font-size: 18px; font-weight: bold; margin-top: 4px;">
                {{ formatPrice(item.productPrice) }}
              </div>
            </div>

            <!-- Quantity Controls -->
            <div style="display: flex; align-items: center; gap: 4px;">
              <el-button :icon="Minus" size="small" circle :disabled="item.quantity <= 1" @click="updateQuantity(item, -1)" />
              <el-input-number
                v-model="item.quantity"
                :min="1"
                :max="item.stock"
                size="small"
                style="width: 80px;"
                :controls="false"
                @change="(val: number) => updateQuantity(item, val - item.quantity)"
              />
              <el-button :icon="Plus" size="small" circle :disabled="item.quantity >= item.stock" @click="updateQuantity(item, 1)" />
            </div>

            <!-- Subtotal -->
            <div style="text-align: right; min-width: 100px;">
              <div style="font-size: 14px; color: #909399;">小计</div>
              <div style="color: #b2070a; font-size: 18px; font-weight: bold;">{{ formatPrice(item.subtotal) }}</div>
            </div>

            <!-- Delete -->
            <el-button :icon="Delete" text type="danger" @click="deleteItem(item)" />
          </div>
        </el-card>
      </div>

      <!-- Bottom Bar -->
      <el-card shadow="never" style="position: sticky; bottom: 0; z-index: 10;">
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <div>
            <el-checkbox :model-value="isAllChecked" @change="toggleCheckAll" style="margin-right: 16px;">
              全选
            </el-checkbox>
            <span style="color: #909399; font-size: 14px;">
              已选 <span style="color: #b2070a; font-weight: bold;">{{ selectedCount }}</span> 件商品
            </span>
          </div>
          <div style="display: flex; align-items: center; gap: 20px;">
            <div>
              <span style="color: #909399;">合计：</span>
              <span style="color: #b2070a; font-size: 24px; font-weight: bold;">{{ formatPrice(totalPrice) }}</span>
            </div>
            <el-button type="danger" size="large" :disabled="selectedCount === 0" @click="goCheckout">
              去结算 ({{ selectedCount }})
            </el-button>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>
