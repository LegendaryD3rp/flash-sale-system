<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'

interface CouponItem {
  id: number
  name: string
  type: string
  discount: number
  minAmount: number
  stock: number
  taken: number
  status: string
  startTime: string
  endTime: string
}

const coupons = ref<CouponItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const dialogVisible = ref(false)
const dialogTitle = ref('')
const editingId = ref<number | null>(null)
const form = ref({
  name: '',
  type: 'FIXED',
  discount: 0,
  minAmount: 0,
  stock: 0,
  startTime: '',
  endTime: ''
})
const submitting = ref(false)

onMounted(() => fetchCoupons())

async function fetchCoupons() {
  loading.value = true
  try {
    const data: any = await request.get('/api/admin/coupon/list', { params: { page: page.value, size: size.value } })
    coupons.value = data.records || []
    total.value = data.total || 0
  } catch {
    coupons.value = []
  } finally {
    loading.value = false
  }
}

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

function formatTime(time: string) {
  if (!time) return ''
  return time.substring(0, 16).replace('T', ' ')
}

function typeLabel(type: string) {
  return type === 'PERCENT' ? '百分比' : '固定金额'
}

function discountLabel(item: CouponItem) {
  if (item.type === 'PERCENT') return item.discount + '%'
  return formatPrice(item.discount)
}

function openAdd() {
  editingId.value = null
  dialogTitle.value = '新增优惠券'
  form.value = { name: '', type: 'FIXED', discount: 0, minAmount: 0, stock: 0, startTime: '', endTime: '' }
  dialogVisible.value = true
}

function openEdit(row: CouponItem) {
  editingId.value = row.id
  dialogTitle.value = '编辑优惠券'
  form.value = {
    name: row.name,
    type: row.type,
    discount: row.discount,
    minAmount: row.minAmount,
    stock: row.stock,
    startTime: row.startTime,
    endTime: row.endTime
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.name.trim()) { ElMessage.warning('请输入优惠券名称'); return }
  if (!form.value.discount || form.value.discount <= 0) { ElMessage.warning('请输入优惠数值'); return }
  if (!form.value.stock || form.value.stock <= 0) { ElMessage.warning('请输入库存'); return }
  submitting.value = true
  try {
    if (editingId.value) {
      await request.put(`/api/admin/coupon/${editingId.value}`, form.value)
      ElMessage.success('修改成功')
    } else {
      await request.post('/api/admin/coupon', form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchCoupons()
  } finally {
    submitting.value = false
  }
}

async function disableCoupon(id: number) {
  await ElMessageBox.confirm('确定下架该优惠券？', '提示', { type: 'warning' })
  await request.put(`/api/admin/coupon/${id}/disable`)
  ElMessage.success('已下架')
  fetchCoupons()
}

function onPageChange(newPage: number) {
  page.value = newPage
  fetchCoupons()
}
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h3 style="margin: 0;">优惠券管理</h3>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增优惠券</el-button>
    </div>

    <el-card>
      <el-table :data="coupons" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="优惠" width="100">
          <template #default="{ row }">{{ discountLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="门槛" width="100">
          <template #default="{ row }">{{ row.minAmount > 0 ? formatPrice(row.minAmount) : '无' }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="60" />
        <el-table-column prop="taken" label="已领" width="60" />
        <el-table-column label="有效期" min-width="200">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button text :icon="Edit" @click="openEdit(row)" :disabled="row.status !== 'ACTIVE'">编辑</el-button>
            <el-button text type="danger" :icon="Delete" @click="disableCoupon(row.id)" :disabled="row.status !== 'ACTIVE'">下架</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: center;">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="onPageChange" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-position="top">
        <el-form-item label="优惠券名称">
          <el-input v-model="form.name" placeholder="如：新人专享8折券" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优惠类型">
              <el-select v-model="form.type" style="width: 100%;">
                <el-option label="固定金额" value="FIXED" />
                <el-option label="百分比" value="PERCENT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优惠数值">
              <el-input v-model.number="form.discount" :min="1">
                <template #suffix>{{ form.type === 'PERCENT' ? '%' : '分' }}</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="最低订单金额（分）">
              <el-input v-model.number="form.minAmount" :min="0" placeholder="0=无门槛" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存">
              <el-input v-model.number="form.stock" :min="1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生效时间">
              <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始时间" style="width: 100%;" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效时间">
              <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束时间" style="width: 100%;" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
