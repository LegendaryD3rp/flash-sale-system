<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis, Goods, Timer, List, UserFilled, HomeFilled, SwitchButton, Document, Coin
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const menuItems = [
  { path: '/admin/dashboard', label: '数据概览', icon: DataAnalysis },
  { path: '/admin/products', label: '商品管理', icon: Goods },
  { path: '/admin/seckill', label: '秒杀管理', icon: Timer },
  { path: '/admin/orders', label: '订单管理', icon: List },
  { path: '/admin/users', label: '用户管理', icon: UserFilled },
  { path: '/admin/audit-log', label: '审计日志', icon: Document },
  { path: '/admin/coupon', label: '优惠券管理', icon: Coin }
]

const activeMenu = computed(() => route.path)

// Breadcrumb map
const breadcrumbMap: Record<string, string> = {
  '/admin/dashboard': '数据概览',
  '/admin/products': '商品管理',
  '/admin/seckill': '秒杀管理',
  '/admin/orders': '订单管理',
  '/admin/users': '用户管理',
  '/admin/audit-log': '审计日志',
  '/admin/coupon': '优惠券管理'
}
const currentPage = computed(() => breadcrumbMap[route.path] || '')

function goHome() {
  router.push('/')
}

function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出')
  router.push('/login')
}
</script>

<template>
  <el-container style="min-height: 100vh;">
    <!-- Sidebar -->
    <el-aside width="220px" style="background: #001529; color: white;">
      <div style="padding: 20px; text-align: center; border-bottom: 1px solid #0d2137;">
        <h3 style="margin: 0; color: #fff;">⚡ FlashSale</h3>
        <span style="font-size: 12px; color: #999;">管理后台</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        background-color="#001529"
        text-color="#fff"
        active-text-color="#ffd04b"
        style="border-right: none;"
        @select="(idx: string) => router.push(idx)"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>

        <!-- Divider -->
        <el-divider style="border-color: #0d2137; margin: 8px 0;" />

        <!-- Back to front -->
        <el-menu-item index="/" @click="goHome">
          <el-icon><HomeFilled /></el-icon>
          <span>返回前台</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- Right side -->
    <el-container>
      <!-- Top bar -->
      <el-header style="background: #fff; border-bottom: 1px solid #e4e7ed; display: flex; align-items: center; justify-content: space-between; padding: 0 20px;">
        <el-breadcrumb>
          <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="currentPage">{{ currentPage }}</el-breadcrumb-item>
        </el-breadcrumb>

        <div style="display: flex; align-items: center; gap: 12px;">
          <el-tag type="warning" size="small" v-if="userStore.isAdmin">管理员</el-tag>
          <span style="color: #666;">{{ userStore.userId ? `ID: ${userStore.userId}` : '' }}</span>
          <el-button text type="danger" :icon="SwitchButton" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <!-- Main Content -->
      <el-main style="background: #f0f2f5; padding: 20px;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.el-menu-item:hover {
  background-color: #1890ff !important;
}
</style>
