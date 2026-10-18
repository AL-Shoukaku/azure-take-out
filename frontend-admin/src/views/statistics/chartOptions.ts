import type { ComposeOption } from 'echarts/core'
import type { BarSeriesOption, LineSeriesOption } from 'echarts/charts'
import type { GridComponentOption, TooltipComponentOption } from 'echarts/components'
import type { TurnoverReport, UserReport, OrderReport, SalesTop10Report } from '@/api/report'

export type StatisticsChartOption = ComposeOption<
  BarSeriesOption | LineSeriesOption | GridComponentOption | TooltipComponentOption
>
export const chartColors = { yellow: '#ffc100', pink: '#fd7f7f' }
const axisLabel = { color: '#666', fontSize: 12, hideOverlap: true }

function numbers(list: string): number[] {
  return list ? list.split(',').map(Number) : []
}

function lineOption(
  dateList: string,
  lines: { name: string; values: string; color: string }[],
  integerAxis: boolean,
): StatisticsChartOption {
  const dates = dateList.split(',')
  return {
    animationDuration: 400,
    tooltip: { trigger: 'axis', confine: true, textStyle: { fontSize: 12, color: '#333' } },
    grid: { top: 16, left: 12, right: 34, bottom: 32, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel,
      axisLine: { lineStyle: { color: '#e5e4e4' } },
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: integerAxis ? 1 : undefined,
      axisLabel,
      splitLine: { lineStyle: { color: '#e0e6f1' } },
    },
    series: lines.map((line): LineSeriesOption => ({
      type: 'line',
      name: line.name,
      data: numbers(line.values),
      smooth: false,
      showSymbol: dates.length === 1,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: { width: 2, color: line.color },
      itemStyle: { color: line.color },
      emphasis: { itemStyle: { color: '#fff', borderColor: line.color, borderWidth: 3 } },
    })),
  }
}

export function turnoverOption(data: TurnoverReport): StatisticsChartOption {
  return lineOption(
    data.dateList,
    [{ name: '营业额', values: data.turnoverList, color: chartColors.yellow }],
    false,
  )
}
export function userOption(data: UserReport): StatisticsChartOption {
  return lineOption(
    data.dateList,
    [
      { name: '用户总量', values: data.totalUserList, color: chartColors.yellow },
      { name: '新增用户', values: data.newUserList, color: chartColors.pink },
    ],
    true,
  )
}
export function orderOption(data: OrderReport): StatisticsChartOption {
  return lineOption(
    data.dateList,
    [
      { name: '订单总数', values: data.orderCountList, color: chartColors.yellow },
      { name: '有效订单', values: data.validOrderCountList, color: chartColors.pink },
    ],
    true,
  )
}
export function salesOption(data: SalesTop10Report): StatisticsChartOption {
  return {
    animationDuration: 400,
    tooltip: {
      trigger: 'axis',
      confine: true,
      axisPointer: { type: 'shadow' },
      textStyle: { fontSize: 12, color: '#333' },
    },
    grid: { top: 0, left: 0, right: 8, bottom: 0, containLabel: true },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category',
      inverse: true,
      data: data.nameList.split(','),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { ...axisLabel, width: 140, overflow: 'truncate' },
    },
    series: [
      {
        name: '销量',
        type: 'bar',
        data: numbers(data.numberList),
        barMaxWidth: 20,
        showBackground: true,
        backgroundStyle: { color: '#f3f4f7' },
        label: { show: true, position: 'insideLeft', color: '#333', distance: 8 },
        itemStyle: {
          borderRadius: [0, 10, 10, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#f9da42' },
              { offset: 1, color: '#f9c23c' },
            ],
          },
        },
      },
    ],
  }
}
