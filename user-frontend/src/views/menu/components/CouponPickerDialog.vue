<template>
  <ElDialog
    v-model="visible"
    title="选择优惠券"
    width="600px"
    class="user-dialog coupon-picker-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
  >
    <p class="picker-note">可多选叠加，仅抵扣餐品金额，不抵扣配送费和打包费。</p>
    <div v-if="loading" aria-label="正在加载优惠券" aria-busy="true">
      <ElSkeleton :rows="6" animated />
    </div>
    <ElAlert v-else-if="error" title="优惠券加载失败" type="error" :closable="false" show-icon>
      <p>{{ error }}</p>
      <ElButton @click="load">重新加载</ElButton>
    </ElAlert>
    <div v-else-if="records.length" class="picker-list">
      <article
        v-for="coupon in records"
        :key="coupon.id"
        class="picker-coupon"
        :aria-label="coupon.name"
        :class="{ 'is-disabled': !!disabledReason(coupon), 'is-selected': draft.has(coupon.id) }"
      >
        <ElCheckbox
          :model-value="draft.has(coupon.id)"
          :disabled="!!disabledReason(coupon)"
          :aria-label="`选择${coupon.name}`"
          @change="toggle(coupon, $event === true)"
        >
          <span class="picker-coupon-name">{{ coupon.name }}</span>
        </ElCheckbox>
        <div class="picker-coupon-details">
          <p class="picker-offer">
            <span>{{ coupon.type === 1 ? '代金券' : '满减券' }}</span
            >{{ couponOffer(coupon) }}
          </p>
          <p>有效期至：{{ formatActivityTime(coupon.expireTime) }}</p>
          <p v-if="disabledReason(coupon)" class="picker-reason">{{ disabledReason(coupon) }}</p>
        </div>
      </article>
    </div>
    <ElEmpty v-else description="暂无未使用的优惠券" :image-size="80" />
    <ElPagination
      v-if="total > pageSize && !loading && !error"
      class="picker-pagination"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      :pager-count="5"
      layout="prev, pager, next"
      @current-change="changePage"
    />
    <template #footer>
      <div class="picker-footer">
        <p>
          已选 {{ draftPreview.coupons.length }} 张 · 预计优惠
          <strong>{{ money(draftPreview.discount) }}</strong>
        </p>
        <div>
          <ElButton @click="draft = new Map()">清空选择</ElButton
          ><ElButton @click="visible = false">取消</ElButton
          ><ElButton type="primary" :disabled="loading || !!error" @click="confirm"
            >确认选择</ElButton
          >
        </div>
      </div>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElDialog,
  ElEmpty,
  ElPagination,
  ElSkeleton,
} from 'element-plus'
import { getUserCoupons, type UserCoupon } from '@/api/userCoupons'
import { useSessionStore } from '@/stores/session'
import { getToken } from '@/utils/auth'
import { money } from '@/utils/pricing'
import { couponOffer, formatActivityTime } from '@/views/coupon/couponDisplay'
import { userCouponPageSize } from '@/views/coupon/myCouponDisplay'
import { couponDisabledReason, previewCoupons } from '../couponPreview'

const props = defineProps<{ subtotal: number; fees: number; now: number }>()
const visible = defineModel<boolean>({ required: true })
const selected = defineModel<UserCoupon[]>('selected', { required: true })
const session = useSessionStore()
const pageSize = userCouponPageSize
const page = ref(1),
  total = ref(0)
const records = ref<UserCoupon[]>([])
const loading = ref(false),
  error = ref('')
const draft = ref(new Map<number, UserCoupon>())
const draftPreview = computed(() =>
  previewCoupons([...draft.value.values()], props.subtotal, props.fees, props.now),
)
const disabledReason = (coupon: UserCoupon) =>
  couponDisabledReason(coupon, props.subtotal, props.now)
let revision = 0,
  active = true

async function load(): Promise<void> {
  const currentRevision = ++revision,
    token = getToken()
  const current = () =>
    active && visible.value && revision === currentRevision && token === getToken()
  loading.value = true
  error.value = ''
  try {
    const result = await getUserCoupons(page.value, pageSize, 0)
    if (!current()) return
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize))
    if (page.value > lastPage) {
      page.value = lastPage
      await load()
      return
    }
    records.value = result.records.filter((coupon) => coupon.status === 0)
    if (total.value === 0) draft.value.clear()
    for (const coupon of result.records) {
      if (draft.value.has(coupon.id)) {
        if (disabledReason(coupon)) draft.value.delete(coupon.id)
        else draft.value.set(coupon.id, coupon)
      }
    }
  } catch (cause) {
    if (current()) error.value = cause instanceof Error ? cause.message : '请稍后重试'
  } finally {
    if (current()) loading.value = false
  }
}
function changePage(value: number): void {
  page.value = value
  void load()
}
function toggle(coupon: UserCoupon, checked: boolean): void {
  if (checked && !disabledReason(coupon)) draft.value.set(coupon.id, coupon)
  else draft.value.delete(coupon.id)
}
function confirm(): void {
  if (loading.value || error.value) return
  selected.value = previewCoupons(
    [...draft.value.values()],
    props.subtotal,
    props.fees,
    Date.now(),
  ).coupons
  visible.value = false
}
watch(
  visible,
  (open) => {
    revision++
    if (open) {
      page.value = 1
      records.value = []
      draft.value = new Map(selected.value.map((coupon) => [coupon.id, coupon]))
      void load()
    }
  },
  { flush: 'sync' },
)
watch(
  () => props.now,
  () => {
    for (const [id, coupon] of draft.value) if (disabledReason(coupon)) draft.value.delete(id)
  },
)
watch(
  () => session.token,
  () => {
    visible.value = false
    draft.value.clear()
    records.value = []
    revision++
  },
  { flush: 'sync' },
)
onScopeDispose(() => {
  active = false
  revision++
})
</script>

<style scoped>
.picker-note {
  margin: 0 0 18px;
  color: #8491a6;
  font-size: 13px;
  line-height: 1.8;
}
.picker-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}
.picker-coupon {
  padding: 14px 18px;
  border: 1px solid var(--user-border);
  border-radius: 10px;
  background: #fff;
}
.picker-coupon.is-selected {
  border-color: var(--user-primary);
  background: #f5f9ff;
}
.picker-coupon.is-disabled {
  background: #f8f9fc;
}
.picker-coupon :deep(.el-checkbox) {
  width: 100%;
  height: auto;
  align-items: flex-start;
  white-space: normal;
}
.picker-coupon :deep(.el-checkbox__input) {
  margin-top: 5px;
}
.picker-coupon :deep(.el-checkbox__label) {
  min-width: 0;
  line-height: 1.7;
}
.picker-coupon-name {
  font-size: 15px;
  overflow-wrap: anywhere;
}
.picker-coupon-details {
  margin-left: 24px;
  font-size: 12px;
  color: #8491a6;
}
.picker-coupon-details p {
  margin: 7px 0 0;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.picker-offer {
  color: var(--user-primary);
}
.picker-offer > span {
  margin-right: 12px;
  color: #526580;
}
.picker-coupon-details .picker-reason {
  color: #b57b28;
}
.picker-pagination {
  justify-content: center;
  margin-top: 18px;
}
.picker-footer {
  text-align: left;
}
.picker-footer p {
  margin: 0 0 14px;
  color: #526580;
  font-size: 13px;
}
.picker-footer strong {
  color: var(--user-primary);
}
.picker-footer > div {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.picker-footer .el-button + .el-button {
  margin-left: 0;
}
</style>
