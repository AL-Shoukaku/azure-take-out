<template>
  <div class="statistics-page">
    <div class="statistics-toolbar">
      <div class="statistics-presets" role="tablist" aria-label="统计时间">
        <button
          v-for="item in reportPresets"
          :key="item.value"
          type="button"
          role="tab"
          :aria-selected="preset === item.value"
          :class="{ 'is-active': preset === item.value }"
          @click="preset = item.value"
        >
          {{ item.label }}
        </button>
      </div>
      <p class="selected-range">已选时间：{{ range.begin }} 至 {{ range.end }}</p>
      <ElButton
        class="export-button"
        :loading="exporting"
        :disabled="exporting"
        @click="exportReport"
      >
        <svg v-if="!exporting" viewBox="0 0 20 20" aria-hidden="true">
          <path d="M7 5H3v12h14V5h-4M10 12V1m-3 3 3-3 3 3" />
        </svg>
        数据导出
      </ElButton>
    </div>

    <div class="statistics-grid">
      <StatisticsCard
        title="营业额统计"
        :loading="turnover.state.loading"
        :error="turnover.state.error"
        @retry="turnover.reload"
      >
        <template v-if="turnover.state.data">
          <StatisticsChart :option="turnoverOption(turnover.state.data)" label="营业额统计折线图" />
          <div class="chart-legend">营业额(元)</div>
        </template>
      </StatisticsCard>
      <StatisticsCard
        title="用户统计"
        :loading="users.state.loading"
        :error="users.state.error"
        @retry="users.reload"
      >
        <template v-if="users.state.data">
          <StatisticsChart :option="userOption(users.state.data)" label="用户统计折线图" />
          <div class="chart-legend">
            <span class="legend-yellow">用户总量（个）</span
            ><span class="legend-pink">新增用户（个）</span>
          </div>
        </template>
      </StatisticsCard>
      <StatisticsCard
        title="订单统计"
        :loading="orders.state.loading"
        :error="orders.state.error"
        @retry="orders.reload"
      >
        <template v-if="orders.state.data">
          <div class="order-completion">
            <div>
              <p>订单完成率</p>
              <strong>{{ (orders.state.data.orderCompletionRate * 100).toFixed(1) }}%</strong>
            </div>
            <span>=</span>
            <div>
              <p>有效订单</p>
              <strong aria-label="有效订单数量">{{ orders.state.data.validOrderCount }}</strong>
            </div>
            <span>/</span>
            <div>
              <p>订单总数</p>
              <strong aria-label="订单总数">{{ orders.state.data.totalOrderCount }}</strong>
            </div>
          </div>
          <StatisticsChart
            :option="orderOption(orders.state.data)"
            label="订单统计折线图"
            :height="300"
          />
          <div class="chart-legend">
            <span class="legend-yellow">订单总数（个）</span
            ><span class="legend-pink">有效订单（个）</span>
          </div>
        </template>
      </StatisticsCard>
      <StatisticsCard
        title="销量排名TOP10"
        :loading="sales.state.loading"
        :error="sales.state.error"
        @retry="sales.reload"
      >
        <template v-if="sales.state.data">
          <StatisticsChart
            v-if="sales.state.data.nameList"
            :option="salesOption(sales.state.data)"
            label="销量排名TOP10柱状图"
            :height="380"
          />
          <ElEmpty v-else description="暂无销量数据" :image-size="100" />
        </template>
      </StatisticsCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElMessageBox } from 'element-plus'
import {
  getTurnoverReport,
  getUserReport,
  getOrderReport,
  getSalesTop10,
  exportBusinessReport,
} from '@/api/report'
import { getReportRange, reportPresets, type ReportPreset } from './dateRange'
import { turnoverOption, userOption, orderOption, salesOption } from './chartOptions'
import { useReport } from './useReport'
import StatisticsCard from './StatisticsCard.vue'
import StatisticsChart from './StatisticsChart.vue'

const preset = ref<ReportPreset>('last7')
const range = computed(() => getReportRange(preset.value))
const turnover = useReport(getTurnoverReport, range)
const users = useReport(getUserReport, range)
const orders = useReport(getOrderReport, range)
const sales = useReport(getSalesTop10, range)
const exporting = ref(false)

async function exportReport() {
  if (exporting.value) return
  try {
    await ElMessageBox.confirm('是否确认导出最近30天运营数据?', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  exporting.value = true
  try {
    const file = await exportBusinessReport()
    const url = URL.createObjectURL(file)
    const link = document.createElement('a')
    link.href = url
    link.download = '运营数据统计报表.xlsx'
    document.body.append(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.statistics-toolbar {
  display: flex;
  min-height: 40px;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 15px;
  margin-bottom: 20px;
}
.statistics-presets {
  display: flex;
  overflow: hidden;
  border: 1px solid #e5e4e4;
  border-radius: 4px;
  background: #fff;
}
.statistics-presets button {
  width: 80px;
  height: 38px;
  padding: 0;
  border: 0;
  border-right: 1px solid #e5e4e4;
  background: #fff;
  color: #333;
  font-size: 16px;
  cursor: pointer;
}
.statistics-presets button:last-child {
  border-right: 0;
}
.statistics-presets button.is-active {
  background: #ffc200;
}
.statistics-presets button:focus-visible {
  outline: 2px solid #333;
  outline-offset: -3px;
}
.selected-range {
  margin: 0;
  color: #666;
  font-size: 14px;
  white-space: nowrap;
}
.export-button {
  height: 40px;
  margin-left: auto;
  color: #333;
}
.export-button svg {
  width: 16px;
  height: 16px;
  margin-right: 3px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
}
.statistics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.chart-legend {
  display: flex;
  min-height: 20px;
  justify-content: center;
  align-items: center;
  gap: 20px;
  color: #666;
  font-size: 12px;
}
.chart-legend span {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
}
.chart-legend span::before {
  width: 12px;
  height: 2px;
  margin-right: 8px;
  background: #ffc100;
  content: '';
}
.chart-legend .legend-pink::before {
  background: #fd7f7f;
}
.order-completion {
  display: flex;
  height: 60px;
  align-items: flex-start;
  gap: 20px;
  color: #666;
  font-size: 12px;
}
.order-completion p {
  margin: 0 0 5px;
}
.order-completion strong {
  color: #000;
  font-size: 16px;
}
.order-completion > span {
  padding-top: 10px;
  font-size: 16px;
}
@media (max-width: 1050px) {
  .statistics-presets button {
    width: 68px;
  }
  .selected-range {
    order: 1;
    flex-basis: 100%;
  }
}
@media (max-width: 850px) {
  .statistics-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
