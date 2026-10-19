export interface EditableFlavor {
  name: string
  values: string[]
}

export const flavorPresets: EditableFlavor[] = [
  { name: '甜味', values: ['无糖', '少糖', '半糖', '多糖', '全糖'] },
  { name: '温度', values: ['热饮', '常温', '去冰', '少冰', '多冰'] },
  { name: '忌口', values: ['不要葱', '不要蒜', '不要香菜', '不要辣'] },
  { name: '辣度', values: ['不辣', '微辣', '中辣', '重辣'] },
]
