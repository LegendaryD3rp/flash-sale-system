<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import request from '../../utils/request'
import * as echarts from 'echarts'

const loading = ref(true)
const summary = ref({
  todayOrderCount: 0,
  todaySalesAmount: 0,
  totalUsers: 0,
  totalProducts: 0,
  totalOrders: 0
})

const trendData = ref<{ date: string; count: number; amount: number }[]>([])
const topProducts = ref<{ productId: number; name: string; salesCount: number; salesAmount: number }[]>([])

// 图表实例
let trendChart: echarts.ECharts | null = null
let topChart: echarts.ECharts | null = null

function formatPrice(price: number) {
  return '¥' + (price / 100).toFixed(2)
}

async function loadData() {
  loading.value = true
  try {
    // 并发请求三个接口
    const [summaryRes, trendRes, topRes] = await Promise.all([
      request.get('/api/admin/statistics/summary'),
      request.get('/api/admin/statistics/order-trend', { params: { days: 7 } }),
      request.get('/api/admin/statistics/top-products', { params: { limit: 10 } })
    ])
    summary.value = summaryRes || summary.value
    trendData.value = trendRes || []
    topProducts.value = topRes || []
  } catch {
    // 接口不可用时显示空数据
  } finally {
    loading.value = false
    await nextTick()
    initCharts()
  }
}

function initCharts() {
  // 折线图：订单趋势
  const trendEl = document.getElementById('trendChart')
  if (trendEl) {
    if (trendChart) trendChart.dispose()
    trendChart = echarts.init(trendEl)
    trendChart.setOption({
      title: { text: '近7天订单趋势', left: 'center' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['订单数', '销售额(元)'], top: 30 },
      grid: { left: 60, right: 20, bottom: 30, top: 80 },
      xAxis: {
        type: 'category',
        data: trendData.value.map(d => d.date.substring(5)),
        axisLabel: { rotate: 0 }
      },
      yAxis: [
        { type: 'value', name: '订单数', min: 0 },
        { type: 'value', name: '销售额(元)', min: 0 }
      ],
      series: [
        {
          name: '订单数',
          type: 'line',
          data: trendData.value.map(d => d.count),
          smooth: true,
          lineStyle: { color: '#409eff', width: 2 },
          itemStyle: { color: '#409eff' }
        },
        {
          name: '销售额(元)',
          type: 'line',
          yAxisIndex: 1,
          data: trendData.value.map(d => Math.round(d.amount / 100)),
          smooth: true,
          lineStyle: { color: '#e6a23c', width: 2 },
          itemStyle: { color: '#e6a23c' }
        }
      ]
    })
  }

  // 柱状图：热销商品
  const topEl = document.getElementById('topChart')
  if (topEl) {
    if (topChart) topChart.dispose()
    topChart = echarts.init(topEl)
    const names = topProducts.value.map(p => p.name.length > 8 ? p.name.substring(0, 8) + '...' : p.name)
    const counts = topProducts.value.map(p => p.salesCount)
    const amounts = topProducts.value.map(p => Math.round(p.salesAmount / 100))
    topChart.setOption({
      title: { text: '热销商品 Top' + topProducts.value.length, left: 'center' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['销量(件)', '销售额(元)'], top: 30 },
      grid: { left: 60, right: 20, bottom: 60, top: 80 },
      xAxis: {
        type: 'category',
        data: names,
        axisLabel: { rotate: topProducts.value.length > 5 ? 30 : 0 }
      },
      yAxis: [
        { type: 'value', name: '销量(件)', min: 0 },
        { type: 'value', name: '销售额(元)', min: 0 }
      ],
      series: [
        {
          name: '销量(件)',
          type: 'bar',
          data: counts,
          itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] }
        },
        {
          name: '销售额(元)',
          type: 'bar',
          yAxisIndex: 1,
          data: amounts,
          itemStyle: { color: '#67c23a', borderRadius: [4, 4, 0, 0] }
        }
      ]
    })
  }
}

onMounted(() => loadData())

onUnmounted(() => {
  trendChart?.dispose()
  topChart?.dispose()
})
</script>

<template>
  <div>
    <h3 style="margin: 0 0 20px;">📊 数据概览</h3>

    <!-- 统计卡片 -->
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="4" v-for="card in [
        { label: '今日订单', value: summary.todayOrderCount, color: '#409eff', suffix: '单' },
        { label: '今日销售额', value: formatPrice(summary.todaySalesAmount), color: '#67c23a', suffix: '' },
        { label: '总用户数', value: summary.totalUsers, color: '#e6a23c', suffix: '人' },
        { label: '总商品数', value: summary.totalProducts, color: '#b2070a', suffix: '件' },
        { label: '总订单数', value: summary.totalOrders, color: '#909399', suffix: '单' }
      ]" :key="card.label">
        <el-card shadow="hover" style="margin-bottom: 20px;">
          <div style="text-align: center;">
            <div style="font-size: 26px; font-weight: bold; color: card.color;">{{ card.value }}</div>
            <div style="color: #999; font-size: 13px; margin-top: 4px;">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="20">
      <el-col :span="14">
        <el-card shadow="never">
          <div id="trendChart" style="width: 100%; height: 380px;"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <div id="topChart" style="width: 100%; height: 380px;"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
