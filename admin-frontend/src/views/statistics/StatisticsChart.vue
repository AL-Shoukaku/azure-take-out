<template>
  <div
    ref="container"
    class="statistics-chart"
    role="img"
    :aria-label="label"
    :style="{ height: `${height}px` }"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
import { init, use, type EChartsType } from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { StatisticsChartOption } from './chartOptions'

use([LineChart, BarChart, GridComponent, TooltipComponent, SVGRenderer])
const props = withDefaults(
  defineProps<{ option: StatisticsChartOption; label: string; height?: number }>(),
  { height: 320 },
)
const container = useTemplateRef<HTMLDivElement>('container')
let chart: EChartsType | undefined
let observer: ResizeObserver | undefined

onMounted(() => {
  chart = init(container.value!, undefined, { renderer: 'svg' })
  chart.setOption(props.option)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(container.value!)
})
watch(
  () => props.option,
  (option) => chart?.setOption(option, { notMerge: true }),
)
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<style scoped>
.statistics-chart {
  width: 100%;
  min-width: 0;
}
</style>
