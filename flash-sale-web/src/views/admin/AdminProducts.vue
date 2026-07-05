<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'

interface Product {
  id: number
  name: string
  description: string
  price: number
  imageUrl: string
  category: string
  stock: number
  status: string
  createdAt: string
  updatedAt: string
  images?: any[]
  skus?: any[]
}

interface SkuItem {
  name: string
  price: number
  stock: number
  imageUrl: string
}

const products = ref<Product[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
const dialogTitle = ref('新增商品')
const formRef = ref()
const form = ref({ id: 0, name: '', description: '', price: 0, stock: 0, category: '', imageUrl: '' })
const images = ref<any[]>([])
const skus = ref<SkuItem[]>([])
const isEditing = ref(false)

async function loadProducts() {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res: any = await request.get('/api/product', { params })
    products.value = res?.records ?? []
    total.value = res?.total ?? 0
  } catch {
    products.value = []
  } finally {
    loading.value = false
  }
}

function formatPrice(p: number) { return '¥' + (p / 100).toFixed(2) }

function openCreate() {
  isEditing.value = false
  dialogTitle.value = '新增商品'
  form.value = { id: 0, name: '', description: '', price: 0, stock: 0, category: '', imageUrl: '' }
  images.value = []
  skus.value = []
  dialogVisible.value = true
}

function openEdit(row: Product) {
  isEditing.value = true
  dialogTitle.value = '编辑商品'
  form.value = { id: row.id, name: row.name, description: row.description, price: row.price, stock: row.stock, category: row.category, imageUrl: row.imageUrl }
  // 如果有详情数据，加载图片和SKU
  loadProductDetail(row.id)
  dialogVisible.value = true
}

async function loadProductDetail(id: number) {
  try {
    const data: any = await request.get(`/api/product/${id}`)
    images.value = (data.images || []).map((img: any) => ({
      name: img.imageUrl?.split('/').pop() || '图片',
      url: img.imageUrl
    }))
    skus.value = (data.skus || []).map((sku: any) => ({
      name: sku.name || '',
      price: sku.price || 0,
      stock: sku.stock || 0,
      imageUrl: sku.imageUrl || ''
    }))
  } catch {
    images.value = []
    skus.value = []
  }
}

async function handleSave() {
  try {
    const payload: any = { ...form.value }
    // 构建 images 和 skus
    payload.images = images.value.map((img: any, idx: number) => ({
      imageUrl: img.url,
      sortOrder: idx
    }))
    payload.skus = skus.value.map((sku: any) => ({
      name: sku.name,
      price: sku.price,
      stock: sku.stock,
      imageUrl: sku.imageUrl
    }))

    if (isEditing.value) {
      await request.put(`/api/product/${form.value.id}`, payload)
      ElMessage.success('更新成功')
    } else {
      await request.post('/api/product', payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadProducts()
  } catch {
    // handled by interceptor
  }
}

async function handleDelete(row: Product) {
  try {
    await ElMessageBox.confirm(`确认下架商品「${row.name}」？`, '提示', {
      confirmButtonText: '确定下架',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/api/product/${row.id}`)
    ElMessage.success('已下架')
    loadProducts()
  } catch {
    // cancelled
  }
}

// 图片上传成功回调
function handleUploadSuccess(res: any, idx: number) {
  // res 是上传返回的URL字符串
  if (idx >= 0 && idx < images.value.length) {
    images.value[idx].url = res
  }
}

function addImage() {
  images.value.push({ name: '', url: '' })
}

function removeImage(idx: number) {
  images.value.splice(idx, 1)
}

function addSku() {
  skus.value.push({ name: '', price: 0, stock: 0, imageUrl: '' })
}

function removeSku(idx: number) {
  skus.value.splice(idx, 1)
}

onMounted(() => loadProducts())
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h3 style="margin: 0;">📦 商品管理</h3>
      <div style="display: flex; gap: 8px;">
        <el-input v-model="keyword" placeholder="搜索商品名称" :prefix-icon="Search" clearable style="width: 240px;" @keyup.enter="loadProducts" />
        <el-button type="primary" :icon="Search" @click="loadProducts">搜索</el-button>
        <el-button type="danger" :icon="Plus" @click="openCreate">新增商品</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="products" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">{{ formatPrice(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ON' ? 'success' : 'info'" size="small">
              {{ row.status === 'ON' ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'ON'" link type="danger" :icon="Delete" @click="handleDelete(row)">下架</el-button>
            <el-tag v-else type="info" size="small">已下架</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: center;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="loadProducts"
        />
      </div>
    </el-card>

    <!-- Create / Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="form" label-width="80px" ref="formRef">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="商品名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="商品描述" />
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="form.price" :min="0" :step="100" placeholder="单位：分" style="width: 100%;" />
          <span style="color: #999; font-size: 12px; margin-left: 8px;">单位：分（如 399900 = ¥3,999）</span>
        </el-form-item>
        <el-form-item label="库存" required>
          <el-input-number v-model="form.stock" :min="0" :step="10" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="选择分类" clearable style="width: 100%;">
            <el-option label="笔记本" value="笔记本" />
            <el-option label="耳机" value="耳机" />
            <el-option label="外设" value="外设" />
            <el-option label="图书" value="图书" />
            <el-option label="游戏机" value="游戏机" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面图">
          <el-input v-model="form.imageUrl" placeholder="商品封面图片URL" />
        </el-form-item>

        <!-- 商品图片区域 -->
        <el-form-item label="商品图片">
          <div style="width: 100%;">
            <div v-for="(img, idx) in images" :key="idx" style="display: flex; gap: 8px; align-items: center; margin-bottom: 8px;">
              <el-input v-model="img.url" placeholder="图片URL" style="flex: 1;" />
              <el-upload
                :action="'/api/upload'"
                :headers="{ Authorization: 'Bearer ' + (localStorage.getItem('token') || ''), 'X-User-Role': 'ADMIN' }"
                :show-file-list="false"
                :on-success="(res: any) => { img.url = res; ElMessage.success('上传成功') }"
                :on-error="() => ElMessage.error('上传失败')"
                accept="image/*"
              >
                <el-button size="small" type="primary">上传</el-button>
              </el-upload>
              <el-button size="small" type="danger" @click="removeImage(idx)">删除</el-button>
            </div>
            <el-button type="primary" size="small" @click="addImage">+ 添加图片</el-button>
          </div>
        </el-form-item>

        <!-- 商品规格（SKU）区域 -->
        <el-form-item label="商品规格">
          <div style="width: 100%;">
            <div v-for="(sku, idx) in skus" :key="'sku'+idx" style="border: 1px solid #e4e7ed; border-radius: 4px; padding: 12px; margin-bottom: 8px;">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                <strong>规格 #{{ idx + 1 }}</strong>
                <el-button size="small" type="danger" @click="removeSku(idx)">删除</el-button>
              </div>
              <el-row :gutter="12">
                <el-col :span="8">
                  <el-input v-model="sku.name" placeholder="名称（如 16GB+512GB 深空灰）" size="small" />
                </el-col>
                <el-col :span="5">
                  <el-input-number v-model="sku.price" :min="0" :step="100" placeholder="价格(分)" size="small" style="width: 100%;" />
                </el-col>
                <el-col :span="4">
                  <el-input-number v-model="sku.stock" :min="0" placeholder="库存" size="small" style="width: 100%;" />
                </el-col>
                <el-col :span="7">
                  <el-input v-model="sku.imageUrl" placeholder="规格图片URL" size="small" />
                </el-col>
              </el-row>
            </div>
            <el-button type="primary" size="small" @click="addSku">+ 添加规格</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
