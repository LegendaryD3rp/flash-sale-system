<script setup lang="ts">
import { useUserStore } from './stores/user'
import { useRouter } from 'vue-router'
import { ShoppingCart, Lightning, User } from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div id="app">
    <!-- Header -->
    <el-header class="app-header">
      <div class="header-left">
        <router-link to="/" class="logo">
          <el-icon :size="24"><Lightning /></el-icon>
          <span class="logo-text">FlashSale</span>
        </router-link>
      </div>
      <div class="header-center">
        <el-menu mode="horizontal" :ellipsis="false" router>
          <el-menu-item index="/">首页</el-menu-item>
          <el-menu-item index="/seckill">秒杀</el-menu-item>
          <el-menu-item v-if="userStore.isLoggedIn()" index="/cart">
            <el-icon><ShoppingCart /></el-icon>
            购物车
          </el-menu-item>
          <el-menu-item index="/ai">AI导购</el-menu-item>
          <el-menu-item v-if="userStore.isLoggedIn()" index="/order">订单</el-menu-item>
        </el-menu>
      </div>
      <div class="header-right">
        <template v-if="userStore.isLoggedIn()">
          <el-dropdown trigger="click">
            <el-button text>
              <el-icon><User /></el-icon>
              {{ userStore.isAdmin() ? '管理员' : '我的' }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item @click="router.push('/order')">我的订单</el-dropdown-item>
                <el-dropdown-item @click="router.push('/address')">地址管理</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin()" @click="router.push('/admin')" divided>管理后台</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button text @click="router.push('/login')">登录</el-button>
          <el-button text @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </el-header>

    <!-- Main Content -->
    <el-main class="app-main">
      <router-view />
    </el-main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; width: 100%; font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif; }

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
}

.header-left { display: flex; align-items: center; }
.logo { display: flex; align-items: center; text-decoration: none; color: #409eff; gap: 8px; }
.logo-text { font-size: 20px; font-weight: 700; }

.header-center { flex: 1; display: flex; justify-content: center; }
.header-center .el-menu { border-bottom: none; }

.header-right { display: flex; align-items: center; gap: 8px; }

.app-main {
  margin-top: 60px;
  min-height: calc(100vh - 60px);
  padding: 20px;
  background: #f5f7fa;
}
</style>
