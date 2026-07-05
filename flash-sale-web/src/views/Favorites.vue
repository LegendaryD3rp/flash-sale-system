<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { StarFilled, Star, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const products = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)

interface FavoriteItem {
  id: number
  productId: number
  name: string
  price: number
  imageUrl: string
  category: string
  status: string
  favoriteTime: string
}

async function fetchFavorites() {
  loading.value = true
  try {
    const data: any = await request.get('/api/product/favorite/favorites', {
      params: { page: page.value, size: 20 }
    })
    products.value = (data.records || []).map((p: FavoriteItem) => ({
      ...p,
      _imageError: false
    }))
    total.value = data.total || 0
  } catch {
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

async function removeFavorite(productId: number) {
  try {
    await request.delete(`/api/product/favorite/${productId}`)
    ElMessage.success('已取消收藏')
    fetchFavorites()
  } catch {
    // handled by interceptor
  }
}

onMounted(fetchFavorites)
</script>

<template>
  <div style="max-width: 1200px; margin: 0 auto;">
    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 20px;">
      <el-icon :size="24" color="#e6a23c"><StarFilled /></el-icon>
      <h3 style="margin: 0;">我的收藏</h3>
      <el-tag type="warning" v-if="total > 0">共 {{ total }} 件</el-tag>
    </div>

    <div v-loading="loading">
      <!-- Product Grid -->
      <div v-if="products.length > 0">
        <el-row :gutter="20">
          <el-col v-for="p in products" :key="p.id" :xs="12" :sm="8" :md="6" :lg="6" style="margin-bottom: 20px;">
            <el-card shadow="hover" style="cursor: pointer; position: relative;" @click="router.push(`/product/${p.productId}`)">
              <div style="position: absolute; top: 8px; right: 8px; z-index: 10;" @click.stop="removeFavorite(p.productId)">
                <el-icon :size="20" color="#e6a23c"><StarFilled /></el-icon>
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
            @current-change="(p: number) => { page = p; fetchFavorites() }"
          />
        </div>
      </div>

      <el-card v-else shadow="never">
        <el-empty description="还没有收藏任何商品">
          <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
        </el-empty>
      </el-card>
    </div>
  </div>
</template>
