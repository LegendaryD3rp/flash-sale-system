<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { Timer, Lightning } from '@element-plus/icons-vue'

const router = useRouter()
const activities = ref<any[]>([])
const loading = ref(true)
const activeTab = ref('all')

onMounted(async () => {
  loading.value = true
  try {
    const res: any = await request.get('/api/seckill/activity/list', { params: { page: 1, size: 20 } })
    activities.value = res.records || []
  } catch {
    activities.value = []
  } finally {
    loading.value = false
  }
})

const filteredActivities = ref<any[]>([])


watch([activities, activeTab], () => {
  if (activeTab.value === 'all') {
    filteredActivities.value = activities.value
  } else {
    filteredActivities.value = activities.value.filter((a: any) => a.status === activeTab.value)
  }
}, { immediate: true })

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

function statusTag(status: string) {
  const map: Record<string, { type: string; text: string }> = {
    ACTIVE: { type: 'danger', text: '进行中' },
    ENDED: { type: 'info', text: '已结束' },
    PAUSED: { type: 'warning', text: '已暂停' },
    DRAFT: { type: 'info', text: '草稿' },
    PENDING: { type: 'warning', text: '预告' }
  }
  return map[status] || { type: 'info', text: status }
}

function formatTime(time: string) {
  if (!time) return ''
  return time.substring(0, 16).replace('T', ' ')
}
</script>

<template>
  <div style="max-width: 1000px; margin: 0 auto;">
    <h2 style="margin-bottom: 16px;">
      <el-icon style="vertical-align: middle; margin-right: 6px;" color="#b2070a"><Lightning /></el-icon>
      秒杀活动
    </h2>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" style="margin-bottom: 16px;">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="进行中" name="ACTIVE" />
      <el-tab-pane label="预告" name="PENDING" />
    </el-tabs>

    <!-- Loading -->
    <div v-if="loading" style="text-align: center; padding: 60px 0; color: #999;">加载中...</div>

    <!-- Activity Cards -->
    <el-row :gutter="16">
      <el-col v-for="a in filteredActivities" :key="a.id" :xs="24" :sm="12" :md="8" style="margin-bottom: 16px;">
        <el-card shadow="hover" :style="{ cursor: 'pointer', border: a.status === 'ACTIVE' ? '2px solid #b2070a' : '' }"
                 @click="router.push(`/seckill/${a.id}`)">
          <div style="display: flex; flex-direction: column; gap: 8px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <el-tag :type="(statusTag(a.status).type || 'info') as any" size="small">
                {{ statusTag(a.status).text }}
              </el-tag>
              <el-tag v-if="a.status === 'ACTIVE'" type="danger" size="small" effect="dark">⚡</el-tag>
            </div>

            <div style="font-size: 16px; font-weight: bold;">{{ a.productName || `商品 #${a.productId}` }}</div>

            <div style="display: flex; align-items: baseline; gap: 8px;">
              <span style="font-size: 24px; color: #b2070a; font-weight: bold;">
                {{ formatPrice(a.seckillPrice) }}
              </span>
              <span v-if="a.originalPrice" style="color: #999; text-decoration: line-through; font-size: 13px;">
                {{ formatPrice(a.originalPrice) }}
              </span>
            </div>

            <div style="display: flex; justify-content: space-between; color: #666; font-size: 13px;">
              <span v-if="a.status === 'ACTIVE'">
                剩余 {{ a.availableStock }}/{{ a.totalStock }} 件
              </span>
              <span v-else-if="a.startTime">
                {{ formatTime(a.startTime) }} 开始
              </span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && filteredActivities.length === 0" description="暂无秒杀活动" />
  </div>
</template>
