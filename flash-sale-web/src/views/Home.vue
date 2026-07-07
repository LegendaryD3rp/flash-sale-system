<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import { Search, Lightning, Star, StarFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')
const products = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const seckillActivities = ref<any[]>([])
const selectedCategory = ref('')
const favoritesMap = ref<Record<number, boolean>>({})

const categories = ['全部', '笔记本', '手机', '耳机', '键盘', '显示器', '路由器', '书籍', '游戏', '数码']

interface ProductItem {
  id: number
  name: string
  price: number
  imageUrl: string
  category: string
  stock: number
  status: string
}

function onCategoryClick(cat: string) {
  selectedCategory.value = cat
  keyword.value = ''   // 点击分类时清空搜索框
  page.value = 1
  fetchProducts()
}

async function fetchProducts() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, size: 20 }
    if (selectedCategory.value && selectedCategory.value !== '全部') {
      params.category = selectedCategory.value
    }
    const role = userStore.isLoggedIn() ? (userStore.isAdmin() ? 'ADMIN' : 'CUSTOMER') : 'CUSTOMER'
    const data: any = await request.get('/api/product', {
      params,
      headers: { 'X-User-Role': role }
    })
    products.value = (data.records || []).map((p: ProductItem) => ({
      ...p,
      _imageError: false
    }))
    total.value = data.total || 0
    checkFavorites()
  } catch (e) {
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function checkFavorites() {
  if (!userStore.isLoggedIn() || products.value.length === 0) return
  try {
    const ids = products.value.map((p: any) => p.id).join(',')
    const res: any = await request.get('/api/product/favorite/check', {
      params: { productIds: ids }
    })
    favoritesMap.value = {}
    for (const [id, fav] of Object.entries(res)) {
      favoritesMap.value[Number(id)] = fav as boolean
    }
  } catch {
    // ignore
  }
}

async function toggleFavorite(productId: number) {
  if (!userStore.isLoggedIn()) {
    router.push('/login')
    return
  }
  try {
    if (favoritesMap.value[productId]) {
      await request.delete(`/api/product/favorite/${productId}`)
      favoritesMap.value[productId] = false
    } else {
      await request.post('/api/product/favorite', { productId })
      favoritesMap.value[productId] = true
    }
  } catch {
    // handled by interceptor
  }
}

async function searchProducts() {
  if (!keyword.value.trim()) {
    fetchProducts()
    return
  }
  loading.value = true
  selectedCategory.value = ''   // 搜索时清除分类高亮
  try {
    const data: any = await request.get('/api/product/search', {
      params: { keyword: keyword.value.trim(), page: page.value, size: 20 }
    })
    products.value = (data.records || []).map((p: ProductItem) => ({
      ...p,
      _imageError: false
    }))
    total.value = data.total || 0
    checkFavorites()
  } catch (e) {
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

function onSearch() {
  page.value = 1
  searchProducts()
}

function goLogin() {
  router.push('/login')
}

async function fetchSeckillActivities() {
  try {
    const role = userStore.isLoggedIn() ? (userStore.isAdmin() ? 'ADMIN' : 'CUSTOMER') : 'CUSTOMER'
    const data: any = await request.get('/api/seckill/activity/list', {
      params: { page: 1, size: 10 },
      headers: { 'X-User-Role': role }
    })
    seckillActivities.value = (data.records || []).filter((a: any) => a.status === 'ACTIVE')
  } catch {
    seckillActivities.value = []
  }
}

onMounted(() => {
  fetchProducts()
  fetchSeckillActivities()
})
</script>

<template>
  <div style="max-width: 1200px; margin: 0 auto;">
    <!-- Search Bar -->
    <el-card shadow="never" style="margin-bottom: 20px;">
      <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;">
        <h3 style="margin: 0; white-space: nowrap;">⚡ FlashSale 商城</h3>
        <el-input
          v-model="keyword"
          placeholder="搜索商品名称或描述..."
          :prefix-icon="Search"
          clearable
          style="max-width: 350px;"
          @keyup.enter="onSearch"
        />
        <el-button type="primary" @click="onSearch">搜索</el-button>
        <el-tag type="danger" v-if="total > 0">共 {{ total }} 件商品</el-tag>
      </div>
    </el-card>

    <!-- Category Navigation -->
    <el-card shadow="never" style="margin-bottom: 20px;">
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
        <span style="font-weight: bold; color: #606266; margin-right: 8px; white-space: nowrap;">商品分类：</span>
        <el-tag
          v-for="cat in categories"
          :key="cat"
          :type="selectedCategory === cat || (cat === '全部' && selectedCategory === '') ? 'primary' : 'info'"
          :effect="selectedCategory === cat || (cat === '全部' && selectedCategory === '') ? 'dark' : 'plain'"
          style="cursor: pointer;"
          @click="onCategoryClick(cat)"
        >
          {{ cat }}
        </el-tag>
      </div>
    </el-card>

    <!-- Seckill Banner -->
    <el-card v-if="seckillActivities.length > 0" shadow="never" style="margin-bottom: 20px; border: 2px solid #b2070a; background: linear-gradient(135deg, #fff5f5 0%, #fff 100%);">
      <div style="display: flex; align-items: center; gap: 16px; flex-wrap: wrap;">
        <el-tag type="danger" size="large" effect="dark">
          <el-icon style="vertical-align: middle; margin-right: 4px;"><Lightning /></el-icon>
          限时秒杀
        </el-tag>
        <div v-for="a in seckillActivities" :key="a.id"
             style="flex: 1; min-width: 200px; cursor: pointer; padding: 8px 12px; border: 1px solid #fce4e4; border-radius: 8px;"
             @click="router.push(`/seckill/${a.id}`)">
          <div style="font-weight: bold; font-size: 14px;">{{ a.productName || `商品 #${a.productId}` }}</div>
          <div>
            <span style="color: #b2070a; font-size: 20px; font-weight: bold;">¥{{ (a.seckillPrice / 100).toFixed(2) }}</span>
            <span v-if="a.originalPrice" style="color: #999; text-decoration: line-through; font-size: 12px; margin-left: 6px;">¥{{ (a.originalPrice / 100).toFixed(2) }}</span>
          </div>
          <div style="font-size: 12px; color: #666;">剩余 {{ a.availableStock ?? a.totalStock }}/{{ a.totalStock }} 件</div>
        </div>
      </div>
    </el-card>

    <!-- Loading -->
    <div v-if="loading" style="text-align: center; padding: 60px 0;">
      <el-icon class="is-loading" :size="32">
        <svg viewBox="0 0 1024 1024" width="32" height="32"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z" fill="currentColor"/></svg>
        </el-icon>
        <p style="color: #999;">加载中...</p>
      </div>

    <!-- Prompt to login if not logged in and no products -->
    <el-card v-else-if="!userStore.isLoggedIn() && products.length === 0 && !loading" shadow="never">
      <el-empty description="请登录后浏览商品">
        <el-button type="danger" @click="goLogin">去登录</el-button>
      </el-empty>
    </el-card>

    <!-- Product Grid -->
    <div v-else-if="products.length > 0">
      <el-row :gutter="20">
        <el-col v-for="p in products" :key="p.id" :xs="12" :sm="8" :md="6" :lg="6" style="margin-bottom: 20px;">
          <el-card shadow="hover" style="cursor: pointer; position: relative;" @click="router.push(`/product/${p.id}`)">
            <!-- 收藏图标 -->
            <div v-if="userStore.isLoggedIn()" style="position: absolute; top: 8px; right: 8px; z-index: 10;" @click.stop="toggleFavorite(p.id)">
              <el-icon :size="20" :color="favoritesMap[p.id] ? '#e6a23c' : '#c0c4cc'">
                <StarFilled v-if="favoritesMap[p.id]" />
                <Star v-else />
              </el-icon>
            </div>
            <div style="height: 180px; display: flex; align-items: center; justify-content: center; background: #fafafa; border-radius: 4px; overflow: hidden;">
              <img v-if="p.imageUrl && !p._imageError" :src="p.imageUrl" alt="商品图片"
                   style="max-width: 100%; max-height: 100%; object-fit: cover;"
                   @error="p._imageError = true" />
              <el-icon v-else :size="48" color="#dcdfe6"><svg viewBox="0 0 1024 1024"><path d="M928 160H96c-17.7 0-32 14.3-32 32v640c0 17.7 14.3 32 32 32h832c17.7 0 32-14.3 32-32V192c0-17.7-14.3-32-32-32zM338 604c-14.2 0-25.7-11.5-25.7-25.7s11.5-25.7 25.7-25.7 25.7 11.5 25.7 25.7-11.5 25.7-25.7 25.7z m399.6 124H286.4c-15.8 0-25.3-18.1-16.2-30.9l111.3-158.5c4.4-6.3 11.5-9.9 19.1-9.9s14.7 3.6 19.1 9.9l70.4 100.2 155.8-222c4.4-6.3 11.5-9.9 19.1-9.9s14.7 3.6 19.1 9.9l117.7 167.6c9.1 12.8-.5 30.9-16.2 30.9z" fill="currentColor"/></svg></el-icon>
            </div>
            <div style="padding: 8px 0;">
              <div style="font-weight: bold; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ p.name }}</div>
              <div style="color: #b2070a; font-size: 18px; font-weight: bold; margin-top: 4px;">{{ formatPrice(p.price) }}</div>
              <div style="font-size: 12px; color: #999; margin-top: 2px;">
                <el-tag size="small" type="info" v-if="p.category">{{ p.category }}</el-tag>
                <span v-if="p.stock !== undefined" style="margin-left: 6px;">库存 {{ p.stock }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Pagination -->
      <div style="text-align: center; padding: 20px 0;">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="20"
          :current-page="page"
          @current-change="(p: number) => { page = p; keyword.value ? searchProducts() : fetchProducts() }"
        />
      </div>
    </div>

    <!-- Empty state -->
    <el-card v-else shadow="never">
      <el-empty description="暂无商品" />
    </el-card>
  </div>
</template>
