<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Check } from '@element-plus/icons-vue'

const router = useRouter()
const addresses = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = ref({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  isDefault: 0
})

async function fetchAddresses() {
  loading.value = true
  try {
    const data: any = await request.get('/api/address/list')
    addresses.value = data || []
  } catch {
    addresses.value = []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editingId.value = null
  form.value = { receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 }
  dialogVisible.value = true
}

function openEdit(addr: any) {
  editingId.value = addr.id
  form.value = {
    receiverName: addr.receiverName,
    receiverPhone: addr.receiverPhone,
    province: addr.province || '',
    city: addr.city || '',
    district: addr.district || '',
    detailAddress: addr.detailAddress,
    isDefault: addr.isDefault
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.receiverName || !form.value.receiverPhone || !form.value.detailAddress) {
    ElMessage.warning('请填写收件人、联系电话和详细地址')
    return
  }
  try {
    if (editingId.value) {
      await request.put(`/api/address/${editingId.value}`, form.value)
      ElMessage.success('修改成功')
    } else {
      await request.post('/api/address', form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchAddresses()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function deleteAddress(addr: any) {
  try {
    await ElMessageBox.confirm('确定删除该地址？', '提示', { type: 'warning' })
    await request.delete(`/api/address/${addr.id}`)
    ElMessage.success('已删除')
    fetchAddresses()
  } catch {
    // cancelled
  }
}

async function setDefault(addr: any) {
  try {
    await request.put(`/api/address/${addr.id}/default`)
    ElMessage.success('已设为默认')
    fetchAddresses()
  } catch {
    ElMessage.error('操作失败')
  }
}

function fullAddress(addr: any) {
  return [addr.province, addr.city, addr.district, addr.detailAddress].filter(Boolean).join(' ')
}

onMounted(fetchAddresses)
</script>

<template>
  <div style="max-width: 800px; margin: 0 auto;">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h3 style="margin: 0;">📍 收货地址</h3>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增地址</el-button>
    </div>

    <div v-if="loading" style="text-align: center; padding: 60px 0; color: #999;">加载中...</div>

    <el-empty v-else-if="addresses.length === 0" description="暂无收货地址" />

    <div v-else>
      <el-card v-for="addr in addresses" :key="addr.id" shadow="hover" style="margin-bottom: 12px;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <div style="flex: 1;">
            <div>
              <strong>{{ addr.receiverName }}</strong>
              <span style="margin-left: 16px; color: #666;">{{ addr.receiverPhone }}</span>
              <el-tag v-if="addr.isDefault === 1" type="warning" size="small" style="margin-left: 8px;">默认</el-tag>
            </div>
            <div style="margin-top: 8px; color: #333;">{{ fullAddress(addr) }}</div>
          </div>
          <div style="display: flex; gap: 8px; flex-shrink: 0;">
            <el-button v-if="addr.isDefault !== 1" text size="small" type="primary" :icon="Check" @click="setDefault(addr)">
              设为默认
            </el-button>
            <el-button text size="small" :icon="Edit" @click="openEdit(addr)">编辑</el-button>
            <el-button text size="small" type="danger" :icon="Delete" @click="deleteAddress(addr)">删除</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑地址' : '新增地址'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="收件人" required>
          <el-input v-model="form.receiverName" placeholder="收件人姓名" />
        </el-form-item>
        <el-form-item label="联系电话" required>
          <el-input v-model="form.receiverPhone" placeholder="手机号码" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="省份">
              <el-input v-model="form.province" placeholder="省" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市">
              <el-input v-model="form.city" placeholder="市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区县">
              <el-input v-model="form.district" placeholder="区" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址" required>
          <el-input v-model="form.detailAddress" placeholder="街道、门牌号等" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
