<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Timer } from '@element-plus/icons-vue'

interface SeckillActivity {
  id: number
  productId: number
  productName: string
  seckillPrice: number
  originalPrice: number
  totalStock: number
  availableStock: number
  startTime: string
  endTime: string
  status: string
  createdAt: string
  updatedAt: string
}

const activities = ref<SeckillActivity[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const dialogVisible = ref(false)
const formRef = ref()
const form = ref({
  productId: 0,
  seckillPrice: 0,
  originalPrice: 0,
  totalStock: 0,
  startTime: '',
  endTime: ''
})

// Product picker
const productOptions = ref<{ label: string; value: number }[]>([])
const productDialogVisible = ref(false)
const productKeyword = ref('')
const productList = ref<any[]>([])
const productLoading = ref(false)

async function loadActivities() {
  loading.value = true
  try {
    const res: any = await request.get('/api/seckill/activity/list', {
      params: { page: page.value, size: size.value }
    })
    activities.value = res?.records ?? []
    total.value = res?.total ?? 0
  } catch {
    activities.value = []
  } finally {
    loading.value = false
  }
}

function formatPrice(p: number) { return '¥' + (p / 100).toFixed(2) }

function statusTag(s: string) {
  const map: Record<string, { type: string; text: string }> = {
    DRAFT: { type: 'info', text: '草稿' },
    ACTIVE: { type: 'success', text: '进行中' },
    PAUSED: { type: 'warning', text: '已暂停' },
    PENDING: { type: 'warning', text: '预热中' },
    ENDED: { type: 'danger', text: '已结束' }
  }
  return map[s] || { type: 'info', text: s }
}

function openCreate() {
  form.value = { productId: 0, seckillPrice: 0, originalPrice: 0, totalStock: 0, startTime: '', endTime: '' }
  dialogVisible.value = true
}

async function loadProductsForPicker() {
  productLoading.value = true
  try {
    const params: any = { page: 1, size: 100 }
    if (productKeyword.value.trim()) params.keyword = productKeyword.value.trim()
    const res: any = await request.get('/api/product', { params })
    productList.value = res?.records ?? []
  } catch {
    productList.value = []
  } finally {
    productLoading.value = false
  }
}

function openProductPicker() {
  productKeyword.value = ''
  loadProductsForPicker()
  productDialogVisible.value = true
}

function selectProduct(row: any) {
  form.value.productId = row.id
  form.value.originalPrice = row.price
  productDialogVisible.value = false
}

async function handleSave() {
  try {
    await request.post('/api/seckill/activity', form.value)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadActivities()
  } catch {
    // handled
  }
}

async function handleStatus(activity: SeckillActivity, newStatus: string) {
  const label: Record<string, string> = { ACTIVE: '上架', PAUSED: '下架', ENDED: '结束' }
  try {
    await ElMessageBox.confirm(`确认${label[newStatus] || '变更状态'}活动「${activity.productName}」？`, '提示')
    await request.patch(`/api/seckill/activity/${activity.id}/status`, { status: newStatus })
    ElMessage.success('状态已更新')
    loadActivities()
  } catch {
    // cancelled
  }
}

async function handleWarmUp(activity: SeckillActivity) {
  try {
    await ElMessageBox.confirm(`确认预热活动「${activity.productName}」？预热后会将库存同步到Redis。`, '提示')
    await request.post(`/api/seckill/activity/${activity.id}/warm-up`)
    ElMessage.success('预热完成')
    loadActivities()
  } catch {
    // cancelled
  }
}

onMounted(() => loadActivities())
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h3 style="margin: 0;">⏱ 秒杀活动管理</h3>
      <el-button type="danger" :icon="Plus" @click="openCreate">新建活动</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="activities" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="productName" label="商品名称" min-width="140" />
        <el-table-column label="秒杀价" width="100">
          <template #default="{ row }">{{ formatPrice(row.seckillPrice) }}</template>
        </el-table-column>
        <el-table-column label="原价" width="80">
          <template #default="{ row }">{{ formatPrice(row.originalPrice) }}</template>
        </el-table-column>
        <el-table-column label="优惠" width="70">
          <template #default="{ row }">
            <el-tag type="danger" size="small" v-if="row.originalPrice > 0">
              {{ Math.round((1 - row.seckillPrice / row.originalPrice) * 100) }}%OFF
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalStock" label="总库存" width="80" />
        <el-table-column prop="availableStock" label="可用" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type as any" size="small">{{ statusTag(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">
            <div style="font-size: 12px;">
              <div>{{ row.startTime }}</div>
              <div>{{ row.endTime }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT' || row.status === 'PAUSED'" link type="success" @click="handleStatus(row, 'ACTIVE')">上架</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" :icon="Timer" @click="handleWarmUp(row)">预热</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="success" @click="handleStatus(row, 'ACTIVE')">开始</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="primary" :icon="Timer" @click="handleWarmUp(row)">重新预热</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="handleStatus(row, 'PAUSED')">下架</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="handleStatus(row, 'ENDED')">结束</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: center;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="loadActivities"
        />
      </div>
    </el-card>

    <!-- Create Dialog -->
    <el-dialog v-model="dialogVisible" title="新建秒杀活动" width="550px">
      <el-form :model="form" label-width="100px" ref="formRef">
        <el-form-item label="选择商品" required>
          <div style="display: flex; gap: 8px;">
            <el-input :model-value="form.productId ? `已选商品 ID: ${form.productId}` : ''" placeholder="点击右侧选择商品" disabled />
            <el-button type="primary" @click="openProductPicker">选择</el-button>
          </div>
        </el-form-item>
        <el-form-item label="秒杀价(分)" required>
          <el-input-number v-model="form.seckillPrice" :min="0" :step="100" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="原价(分)" required>
          <el-input-number v-model="form.originalPrice" :min="0" :step="100" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="总库存" required>
          <el-input-number v-model="form.totalStock" :min="1" :step="10" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="danger" @click="handleSave">创建</el-button>
      </template>
    </el-dialog>

    <!-- Product Picker Dialog -->
    <el-dialog v-model="productDialogVisible" title="选择商品" width="600px">
      <div style="display: flex; gap: 8px; margin-bottom: 12px;">
        <el-input v-model="productKeyword" placeholder="搜索商品" clearable @keyup.enter="loadProductsForPicker" />
        <el-button type="primary" @click="loadProductsForPicker">搜索</el-button>
      </div>
      <el-table :data="productList" v-loading="productLoading" stripe @row-click="selectProduct">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">{{ formatPrice(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="70" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button link type="primary" @click="selectProduct(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>
