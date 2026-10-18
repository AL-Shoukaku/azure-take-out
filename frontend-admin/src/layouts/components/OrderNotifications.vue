<template>
  <audio ref="incomingAudio" :src="incomingSound" preload="auto" hidden />
  <audio ref="reminderAudio" :src="reminderSound" preload="auto" hidden />
</template>

<script setup lang="ts">
import { h, onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification, type NotificationHandle } from 'element-plus'
import { connectOrderSocket, type OrderNotification } from '@/services/orderSocket'
import { useOrderEventsStore } from '@/stores/orderEvents'
import incomingSound from '@/assets/sounds/preview.mp3'
import reminderSound from '@/assets/sounds/reminder.mp3'

const router = useRouter()
const orderEvents = useOrderEventsStore()
const incomingAudio = useTemplateRef<HTMLAudioElement>('incomingAudio')
const reminderAudio = useTemplateRef<HTMLAudioElement>('reminderAudio')
const notifications = new Set<NotificationHandle>()
let disconnect: (() => void) | undefined
let connectionWarning: NotificationHandle | undefined
let connectionWarningShown = false

function showOrder(message: OrderNotification): void {
  orderEvents.received()
  const isIncoming = message.type === 1
  const audio = isIncoming ? incomingAudio.value : reminderAudio.value
  if (audio) {
    audio.currentTime = 0
    void audio.play().catch(() => undefined)
  }

  function openOrder(): void {
    void router.push({ path: '/order', query: { orderId: String(message.orderId) } })
    notification.close()
  }

  const action = h(
    'button',
    {
      type: 'button',
      class: 'order-notification-action',
      onClick: (event: MouseEvent) => {
        event.stopPropagation()
        openOrder()
      },
    },
    isIncoming ? '订单待处理' : '去处理',
  )
  const notification = ElNotification({
    title: isIncoming ? '待接单' : '催单',
    position: 'top-right',
    customClass: 'order-notification',
    duration: 0,
    message: h(
      'div',
      null,
      isIncoming
        ? ['您有1个', action, `，${message.content}，请及时接单`]
        : [`${message.content}，客户催单，`, action],
    ),
    onClick: openOrder,
    onClose: () => notifications.delete(notification),
  })
  notifications.add(notification)
}

onMounted(() => {
  disconnect = connectOrderSocket({
    onMessage: showOrder,
    onDisconnected: () => {
      if (connectionWarningShown) return
      connectionWarningShown = true
      connectionWarning = ElNotification({
        title: '连接中断',
        message: '订单提醒连接已断开，正在重新连接',
        type: 'warning',
        duration: 0,
      })
    },
    onConnected: () => {
      connectionWarningShown = false
      connectionWarning?.close()
      connectionWarning = undefined
    },
  })
})

onBeforeUnmount(() => {
  disconnect?.()
  connectionWarning?.close()
  notifications.forEach((notification) => notification.close())
  incomingAudio.value?.pause()
  reminderAudio.value?.pause()
})
</script>
