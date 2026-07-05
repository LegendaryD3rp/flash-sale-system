<script setup lang="ts">
import { ref, nextTick } from 'vue'
import request from '../utils/request'
import { Promotion, ChatDotSquare } from '@element-plus/icons-vue'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

const messages = ref<Message[]>([
  { role: 'assistant', content: '👋 您好！我是 FlashSale AI 导购助手，有什么可以帮您的？' }
])
const input = ref('')
const loading = ref(false)
const chatContainer = ref<HTMLElement | null>(null)

async function sendMessage() {
  if (!input.value.trim() || loading.value) return

  const userMsg = input.value.trim()
  messages.value.push({ role: 'user', content: userMsg })
  input.value = ''
  scrollToBottom()

  loading.value = true
  try {
    // Try real API first, fall back to mock
    const res: any = await request.post('/api/ai/chat', {
      message: userMsg,
      history: messages.value.slice(0, -1).map(m => ({ role: m.role, content: m.content }))
    })
    messages.value.push({ role: 'assistant', content: res.reply })
  } catch {
    // Mock fallback
    const reply = mockReply(userMsg)
    messages.value.push({ role: 'assistant', content: reply })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function mockReply(msg: string): string {
  const m = msg.toLowerCase()
  if (m.includes('秒杀') || m.includes('抢购')) {
    return '⚡ iPhone 16 Pro 秒杀进行中！5折仅¥3,999，还剩12件，快去抢！'
  }
  if (m.includes('推荐') || m.includes('买')) {
    return '🛒 推荐您看看：\n1️⃣ iPhone 16 Pro — 旗舰5G手机\n2️⃣ MacBook Air M4 — 轻薄办公本\n3️⃣ AirPods Pro 3 — 降噪耳机'
  }
  if (m.includes('价格') || m.includes('优惠')) {
    return '💰 秒杀商品低至5折！iPhone 16 Pro 仅¥3,999（原价¥7,999），限时抢购中！'
  }
  if (m.includes('你好') || m.includes('hi')) {
    return '👋 您好！我是 AI 导购，可以为您推荐商品、查询秒杀信息、解答购物问题！'
  }
  return '🤔 您可以试试问我「秒杀」「推荐」「价格」等问题哦！'
}

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}
</script>

<template>
  <div style="max-width: 700px; margin: 0 auto; display: flex; flex-direction: column; height: calc(100vh - 140px);">
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 0; border-radius: 8px 8px 0 0;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <el-icon :size="24" color="#b2070a"><ChatDotSquare /></el-icon>
        <h3 style="margin: 0;">AI 导购助手</h3>
        <el-tag size="small" type="danger">智能</el-tag>
      </div>
    </el-card>

    <!-- Chat Messages -->
    <div ref="chatContainer" style="flex: 1; overflow-y: auto; padding: 16px; background: #fafafa; border: 1px solid #e4e7ed; border-top: none;">
      <div v-for="(msg, idx) in messages" :key="idx" :style="{ display: 'flex', marginBottom: '16px', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }">
        <div :style="{ maxWidth: '75%', padding: '12px 16px', borderRadius: '12px', backgroundColor: msg.role === 'user' ? '#b2070a' : '#fff', color: msg.role === 'user' ? '#fff' : '#333', border: msg.role === 'user' ? 'none' : '1px solid #e4e7ed', whiteSpace: 'pre-wrap', lineHeight: '1.6' }">
          {{ msg.content }}
        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" style="display: flex; margin-bottom: 16px;">
        <div style="max-width: 75%; padding: 12px 16px; border-radius: 12px; background: #fff; border: 1px solid #e4e7ed; color: #999;">
          AI 正在思考...
        </div>
      </div>
    </div>

    <!-- Input Bar -->
    <el-card shadow="never" style="border-radius: 0 0 8px 8px; border-top: 1px solid #e4e7ed;">
      <div style="display: flex; gap: 8px;">
        <el-input
          v-model="input"
          placeholder="输入您的问题..."
          size="large"
          clearable
          @keyup.enter="sendMessage"
        />
        <el-button type="danger" size="large" :icon="Promotion" :loading="loading" @click="sendMessage" />
      </div>
    </el-card>
  </div>
</template>
