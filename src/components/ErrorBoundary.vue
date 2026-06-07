<template>
  <div v-if="hasError" class="error-boundary">
    <n-result
      status="error"
      title="页面渲染异常"
      :description="errorMessage"
    >
      <template #footer>
        <n-button @click="reload">重新加载</n-button>
      </template>
    </n-result>
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { NResult, NButton } from 'naive-ui'

const hasError = ref(false)
const errorMessage = ref('页面出现异常，请刷新后重试')

onErrorCaptured((err: Error) => {
  hasError.value = true
  errorMessage.value = err.message || '未知错误'
  console.warn('[ErrorBoundary]', err)
  return false // 阻止继续传播
})

function reload() {
  window.location.reload()
}
</script>

<style scoped>
.error-boundary {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  padding: 48px;
}
</style>
