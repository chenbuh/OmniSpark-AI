<template>
  <div class="skeleton-card glass-card" :class="[type, size]">
    <div v-if="type === 'chart'" class="skeleton-chart">
      <div class="s-line" v-for="i in 5" :key="i" :style="{ width: (60 + Math.random() * 30) + '%' }"></div>
    </div>
    <div v-else-if="type === 'grid'" class="skeleton-grid">
      <div class="s-grid-item" v-for="i in (count || 6)" :key="i">
        <div class="s-img"></div>
        <div class="s-text s-title"></div>
        <div class="s-text s-meta"></div>
      </div>
    </div>
    <div v-else-if="type === 'table'" class="skeleton-table">
      <div class="s-row s-header">
        <div class="s-cell" v-for="i in (cols || 5)" :key="i"></div>
      </div>
      <div class="s-row" v-for="r in (rows || 5)" :key="r">
        <div class="s-cell" v-for="c in (cols || 5)" :key="c" :style="{ width: cellWidth(c) }"></div>
      </div>
    </div>
    <div v-else class="skeleton-default">
      <div class="s-circle"></div>
      <div class="s-texts">
        <div class="s-text s-title" style="width: 60%"></div>
        <div class="s-text s-meta" style="width: 40%"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  type?: 'card' | 'table' | 'grid' | 'chart'
  count?: number
  rows?: number
  cols?: number
  size?: 'sm' | 'md' | 'lg'
}>(), {
  type: 'card',
  size: 'md'
})

const cellWidth = (c: number) => {
  const widths = ['30%', '50%', '40%', '25%', '60%', '35%', '45%', '55%']
  return widths[(c - 1) % widths.length]
}
</script>

<style scoped>
.glass-card {
  background: rgba(15, 23, 42, 0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  border-radius: 16px !important;
  overflow: hidden;
}

@keyframes shimmer {
  0% { background-position: -200px 0; }
  100% { background-position: calc(200px + 100%) 0; }
}

.skeleton-card {
  padding: 20px;
}

.s-text {
  height: 14px;
  border-radius: 6px;
  background: linear-gradient(90deg, rgba(255,255,255,0.03) 25%, rgba(255,255,255,0.06) 50%, rgba(255,255,255,0.03) 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s infinite;
  margin-bottom: 8px;
}

.s-title { height: 16px; }
.s-meta { height: 10px; width: 50%; }

.s-circle {
  width: 44px; height: 44px; border-radius: 50%;
  background: linear-gradient(90deg, rgba(255,255,255,0.03) 25%, rgba(255,255,255,0.06) 50%, rgba(255,255,255,0.03) 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-default {
  display: flex; align-items: center; gap: 12px;
}

.skeleton-chart {
  display: flex; flex-direction: column; gap: 12px; padding: 20px 0;
}
.s-line {
  height: 24px; border-radius: 6px;
  background: linear-gradient(90deg, rgba(255,255,255,0.03) 25%, rgba(255,255,255,0.06) 50%, rgba(255,255,255,0.03) 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 16px;
}
.s-grid-item { padding: 12px; background: rgba(255,255,255,0.02); border-radius: 12px; }
.s-img {
  height: 120px; border-radius: 8px; margin-bottom: 10px;
  background: linear-gradient(90deg, rgba(255,255,255,0.03) 25%, rgba(255,255,255,0.06) 50%, rgba(255,255,255,0.03) 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-table { display: flex; flex-direction: column; gap: 4px; }
.s-row { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.03); }
.s-row.s-header { border-bottom: 1px solid rgba(255,255,255,0.08); }
.s-cell {
  flex: 1; height: 12px; border-radius: 4px;
  background: linear-gradient(90deg, rgba(255,255,255,0.03) 25%, rgba(255,255,255,0.06) 50%, rgba(255,255,255,0.03) 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s infinite;
}
.s-row.s-header .s-cell { height: 14px; }
</style>
