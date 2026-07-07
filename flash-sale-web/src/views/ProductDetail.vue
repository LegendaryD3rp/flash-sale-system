<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import { ElMessage } from 'element-plus'
import { Star, StarFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const product = ref<any>(null)
const seckill = ref<any>(null)
const hasAnySeckill = ref(false)
const loading = ref(true)
const isFavorited = ref(false)

// 评价
const reviews = ref<any[]>([])
const reviewPage = ref(1)
const reviewTotal = ref(0)
const reviewLoading = ref(false)

// 选中的SKU
const selectedSku = ref<any>(null)

const displayPrice = computed(() => {
  if (selectedSku.value) return selectedSku.value.price
  return product.value?.price || 0
})

const displayStock = computed(() => {
  if (selectedSku.value) return selectedSku.value.stock
  return product.value?.stock || 0
})

const displayImage = computed(() => {
  if (selectedSku.value && selectedSku.value.imageUrl) return selectedSku.value.imageUrl
  if (product.value?.images && product.value.images.length > 0) return product.value.images[0].imageUrl
  return product.value?.imageUrl || ''
})

function selectSku(sku: any) {
  selectedSku.value = sku
}

async function checkFavorite(productId: number) {
  if (!userStore.isLoggedIn()) return
  try {
    const res: any = await request.get('/api/product/favorite/check', {
      params: { productIds: String(productId) }
    })
    isFavorited.value = res[String(productId)] === true
  } catch {
    // ignore
  }
}

async function toggleFavorite() {
  if (!userStore.isLoggedIn()) {
    router.push('/login')
    return
  }
  try {
    if (isFavorited.value) {
      await request.delete(`/api/product/favorite/${product.value.id}`)
      isFavorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await request.post('/api/product/favorite', { productId: product.value.id })
      isFavorited.value = true
      ElMessage.success('已收藏')
    }
  } catch {
    // handled by interceptor
  }
}

async function fetchReviews() {
  if (!product.value?.id) return
  reviewLoading.value = true
  try {
    const data: any = await request.get(`/api/product/${product.value.id}/reviews`, {
      params: { page: reviewPage.value, size: 10 }
    })
    reviews.value = data.records || []
    reviewTotal.value = data.total || 0
  } catch {
    reviews.value = []
  } finally {
    reviewLoading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const prodId = Number(route.params.id)
    const res: any = await request.get(`/api/product/${prodId}`)
    product.value = res
    if (res.skus && res.skus.length === 1) {
      selectedSku.value = res.skus[0]
    }
    checkFavorite(prodId)
    // 加载评价
    await fetchReviews()
    // Try to find an active seckill for this product
    try {
      const seckillRes: any = await request.get('/api/seckill/activity/list', { params: { page: 1, size: 999 } })
      const list = seckillRes.records || []
      const found = list.find((a: any) => a.productId === prodId && a.status === 'ACTIVE')
      if (found) seckill.value = found
      hasAnySeckill.value = list.some((a: any) => a.productId === prodId)
    } catch { /* no seckill for this product */ }
  } catch {
    product.value = null
  } finally {
    loading.value = false
  }
})

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

function goToCart() {
  if (!userStore.isLoggedIn()) {
    router.push('/login')
    return
  }
  request.post('/api/cart/add', { productId: product.value?.id, quantity: 1 }).then(() => {
    ElMessage.success('已加入购物车')
  }).catch(() => {
    ElMessage.warning('添加失败，请重试')
  })
}
</script>

<template>
  <div style="max-width: 1000px; margin: 0 auto;">
    <el-button link @click="router.back()" style="margin-bottom: 16px;">← 返回</el-button>

    <!-- Loading -->
    <div v-if="loading" style="text-align: center; padding: 80px 0; color: #999;">加载中...</div>

    <!-- Product Detail -->
    <div v-else-if="product" style="display: flex; gap: 40px; flex-wrap: wrap;">
      <!-- Product Images -->
      <div style="flex: 1; min-width: 280px;">
        <!-- 多图轮播 -->
        <el-carousel v-if="product.images && product.images.length > 0" height="400px" indicator-position="outside" arrow="always">
          <el-carousel-item v-for="(img, idx) in product.images" :key="idx">
            <div style="width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #f5f5f5; border-radius: 8px;">
              <img :src="img.imageUrl" alt="商品图片" style="max-width: 100%; max-height: 100%; object-fit: contain;" />
            </div>
          </el-carousel-item>
        </el-carousel>
        <!-- 单图 -->
        <div v-else style="width: 100%; aspect-ratio: 1; border-radius: 8px; background: #f5f5f5; display: flex; align-items: center; justify-content: center; font-size: 64px;">
          📦
        </div>
      </div>

      <!-- Product Info -->
      <div style="flex: 1; min-width: 280px;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <h2 style="margin: 0;">{{ product.name }}</h2>
          <!-- 收藏按钮 -->
          <el-button v-if="userStore.isLoggedIn()" :icon="isFavorited ? StarFilled : Star" :type="isFavorited ? 'warning' : 'default'" text @click="toggleFavorite" />
        </div>
        <p style="color: #666; line-height: 1.6;">{{ product.description || '暂无描述' }}</p>

        <div style="margin: 20px 0; padding: 20px; background: #fff5f5; border-radius: 8px;">
          <div style="display: flex; align-items: baseline; gap: 12px;">
            <span style="font-size: 28px; color: #b2070a; font-weight: bold;">{{ formatPrice(displayPrice) }}</span>
            <span v-if="selectedSku" style="color: #999; font-size: 12px;">原价 {{ formatPrice(product.price) }}</span>
          </div>
          <div style="margin-top: 8px; color: #666;">
            库存：{{ displayStock }} 件 | 分类：{{ product.category || '通用' }}
          </div>
        </div>

        <!-- SKU 选择 -->
        <div v-if="product.skus && product.skus.length > 0" style="margin-bottom: 20px;">
          <div style="font-weight: bold; margin-bottom: 8px; color: #606266;">规格选择：</div>
          <div style="display: flex; gap: 8px; flex-wrap: wrap;">
            <el-tag
              v-for="sku in product.skus"
              :key="sku.id"
              :type="selectedSku === sku ? 'danger' : 'info'"
              :effect="selectedSku === sku ? 'dark' : 'plain'"
              style="cursor: pointer; padding: 6px 12px; font-size: 14px;"
              @click="selectSku(sku)"
            >
              {{ sku.name }}
            </el-tag>
          </div>
        </div>

        <!-- Seckill Entry -->
        <el-card v-if="seckill" shadow="never" style="margin-bottom: 16px; border: 2px solid #b2070a;">
          <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px;">
            <div>
              <el-tag type="danger" size="large">⚡ 秒杀进行中</el-tag>
              <div style="margin-top: 8px;">
                <span style="font-size: 24px; color: #b2070a; font-weight: bold;">{{ formatPrice(seckill.seckillPrice) }}</span>
                <span style="color: #999; margin-left: 8px; text-decoration: line-through;">{{ formatPrice(seckill.originalPrice || product.price) }}</span>
              </div>
              <div style="margin-top: 4px; color: #666;">剩余 {{ seckill.availableStock }}/{{ seckill.totalStock }} 件</div>
            </div>
            <el-button type="danger" size="large" @click="router.push(`/seckill/${seckill.id}`)">
              立即抢购
            </el-button>
          </div>
        </el-card>

        <el-button v-if="seckill" type="danger" size="large" style="width: 100%;" @click="router.push(`/seckill/${seckill.id}`)">
          ⚡ 去秒杀
        </el-button>
        <el-button v-else-if="hasAnySeckill" type="primary" size="large" style="width: 100%;" disabled>
          暂无可购买活动
        </el-button>
        <el-button v-else type="primary" size="large" style="width: 100%;" @click="goToCart">
          🛒 加入购物车
        </el-button>
      </div>
    </div>

    <!-- 商品评价 -->
    <div v-if="!loading && product" style="margin-top: 40px;">
      <h3>商品评价 ({{ reviewTotal }})</h3>
      <div v-if="reviewLoading" style="text-align: center; padding: 40px 0; color: #999;">加载中...</div>
      <div v-else-if="reviews.length === 0" style="text-align: center; padding: 40px 0; color: #999;">
        暂无评价，快来评价吧
      </div>
      <div v-else>
        <div v-for="review in reviews" :key="review.id" style="padding: 16px 0; border-bottom: 1px solid #f0f0f0;">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-rate v-model="review.rating" disabled :max="5" show-score :score-template="''" style="pointer-events: none;" />
              <span style="font-size: 13px; color: #999;">{{ review.rating }} 分</span>
            </div>
            <span style="font-size: 12px; color: #aaa;">{{ review.createdAt?.substring(0, 10) }}</span>
          </div>
          <div style="margin-top: 8px; line-height: 1.6;">{{ review.content }}</div>
          <div style="margin-top: 4px; font-size: 12px; color: #999;">— {{ review.nickname || '匿名用户' }}</div>
        </div>
      </div>
    </div>

    <el-empty v-else description="商品不存在" />
  </div>
</template>
