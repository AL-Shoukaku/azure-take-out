<template>
  <div class="flavor-editor" :class="{ 'has-flavors': flavors.length }">
    <template v-if="flavors.length">
      <p class="flavor-heading">口味名（3个字内）</p>
      <div v-for="(flavor, index) in flavors" :key="index" class="flavor-row">
        <ElSelect
          v-model="flavor.name"
          :aria-label="`口味类型${index + 1}`"
          placeholder="请选择口味"
          :disabled="disabled"
          @change="setFlavor(flavor)"
        >
          <ElOption
            v-for="preset in presets"
            :key="preset.name"
            :label="preset.name"
            :value="preset.name"
            :disabled="flavors.some((item) => item !== flavor && item.name === preset.name)"
          />
        </ElSelect>
        <div class="flavor-values">
          <span v-for="(value, i) in flavor.values" :key="value" class="flavor-tag"
            >{{ value }}
            <button
              type="button"
              :aria-label="`移除${value}`"
              :disabled="disabled"
              @click="flavor.values.splice(i, 1)"
            >
              ×
            </button></span
          >
          <ElSelect
            v-if="flavor.name && remaining(flavor).length"
            :model-value="''"
            :aria-label="`添加${flavor.name}选项`"
            placeholder="添加"
            :disabled="disabled"
            @change="(value: string) => flavor.values.push(value)"
          >
            <ElOption
              v-for="value in remaining(flavor)"
              :key="value"
              :value="value"
              :label="value"
            />
          </ElSelect>
        </div>
        <ElButton
          link
          class="management-action-red"
          :disabled="disabled"
          @click="flavors.splice(index, 1)"
          >删除</ElButton
        >
      </div>
    </template>
    <ElButton
      v-if="flavors.length < presets.length"
      type="primary"
      :disabled="disabled"
      @click="flavors.push({ name: '', values: [] })"
      >{{ flavors.length ? '添加口味' : '+ 添加口味' }}</ElButton
    >
  </div>
</template>

<script setup lang="ts">
import { ElButton, ElOption, ElSelect } from 'element-plus'
import { flavorPresets as presets, type EditableFlavor } from './flavors'

defineProps<{ disabled?: boolean }>()
const flavors = defineModel<EditableFlavor[]>({ required: true })
function setFlavor(flavor: EditableFlavor) {
  flavor.values = [...presets.find((item) => item.name === flavor.name)!.values]
}
function remaining(flavor: EditableFlavor) {
  return (
    presets
      .find((item) => item.name === flavor.name)
      ?.values.filter((value) => !flavor.values.includes(value)) || []
  )
}
</script>

<style scoped>
.flavor-editor {
  width: 100%;
}
.has-flavors {
  padding: 15px;
  border: 1px solid #dfe2e8;
  border-radius: 3px;
  background: var(--admin-surface-soft);
}
.flavor-heading {
  margin: 0 0 15px;
  color: #606168;
}
.flavor-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 10px;
  gap: 15px;
}
.flavor-row > .el-select {
  width: 150px;
  flex-shrink: 0;
}
.flavor-values {
  display: flex;
  flex: 1;
  min-height: 40px;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  padding: 4px 5px;
  border: 1px solid #d8dde3;
  border-radius: 3px;
  background: var(--admin-surface);
}
.flavor-tag {
  padding: 0 8px;
  border: 1px solid var(--admin-border);
  border-radius: 4px;
  background: var(--admin-surface-soft);
  color: var(--admin-primary);
  font-size: 12px;
  line-height: 26px;
}
.flavor-tag button {
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
}
.flavor-values .el-select {
  width: 80px;
}
.flavor-row > .el-button {
  height: 40px;
}
</style>
