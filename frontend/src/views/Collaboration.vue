<template>
  <div class="collab-container">
    <h2>协作管理</h2>

    <!-- 我的协作项目列表 -->
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>我的协作项目</span>
          <el-button type="primary" size="small" @click="loadMyCollaborations">
            刷新
          </el-button>
        </div>
      </template>
      <el-table :data="myCollaborations" border>
        <el-table-column prop="id" label="协作 ID" width="100" />
        <el-table-column prop="workId" label="作品 ID" width="100" />
        <el-table-column prop="ownerName" label="所有者" width="120" />
        <el-table-column prop="role" label="我的角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === '所有者' ? 'success' : 'info'" size="small">
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalRevenue" label="总收入" width="120">
          <template #default="{ row }">
            <span style="color: #f56c6c; font-weight: bold">{{ row.totalRevenue }} 元</span>
          </template>
        </el-table-column>
        <el-table-column prop="distributedAmount" label="已分配" width="120">
          <template #default="{ row }">
            <span style="color: #67c23a; font-weight: bold">{{ row.distributedAmount }} 元</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="selectCollaboration(row)">
              管理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="myCollaborations.length === 0" description="暂无协作项目" :image-size="80" />
    </el-card>

    <!-- 协作详情 -->
    <el-card style="margin-top: 20px" v-if="selectedCollaboration">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>协作详情 (ID: {{ selectedCollaboration.id }})</span>
          <el-tag type="success">作品 ID: {{ selectedCollaboration.workId }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="isNarrow ? 1 : 3" border>
        <el-descriptions-item label="作品 ID">{{ selectedCollaboration.workId }}</el-descriptions-item>
        <el-descriptions-item label="所有者 ID">{{ selectedCollaboration.ownerId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="selectedCollaboration.status === 1 ? 'success' : 'info'">
            {{ selectedCollaboration.status === 1 ? '进行中' : '已结束' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总收入">
          <span style="color: #f56c6c; font-weight: bold">{{ selectedCollaboration.totalRevenue }} 元</span>
        </el-descriptions-item>
        <el-descriptions-item label="已分配">
          <span style="color: #67c23a; font-weight: bold">{{ selectedCollaboration.distributedAmount }} 元</span>
        </el-descriptions-item>
        <el-descriptions-item label="待分配">
          <span style="color: #e6a23c; font-weight: bold">
            {{ (selectedCollaboration.totalRevenue - selectedCollaboration.distributedAmount).toFixed(2) }} 元
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="链上状态">
          <el-tag :type="chainTagType(selectedCollaboration.chainStatus)" size="small" effect="plain">
            {{ chainStatusText(selectedCollaboration.chainStatus) }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 协作协议 -->
    <el-card style="margin-top: 20px" v-if="selectedCollaboration">
      <template #header>
        <div class="card-head">
          <span>协作协议与协商</span>
          <div class="head-actions">
            <el-tag v-if="agreement" :type="agreementTagType" size="small">
              {{ agreementStatusText }}
            </el-tag>
            <el-button
              type="primary"
              size="small"
              v-if="selectedCollaboration.role === '所有者'"
              @click="openAgreementDialog"
            >
              {{ agreement ? '生成新版本' : '生成协议' }}
            </el-button>
          </div>
        </div>
      </template>

      <template v-if="agreement">
        <el-alert
          :title="agreementNotice"
          :type="agreementNoticeType"
          :closable="false"
          show-icon
        />
        <div
          v-if="effectiveAgreement && effectiveAgreement.id !== agreement.id"
          style="margin-top: 14px"
        >
          <h4 class="section-title">当前生效的结算依据</h4>
          <el-descriptions :column="isNarrow ? 1 : 3" border size="small">
            <el-descriptions-item label="生效版本">v{{ effectiveAgreement.version }}</el-descriptions-item>
            <el-descriptions-item label="比例合计">{{ effectiveAgreement.totalRatio }}%</el-descriptions-item>
            <el-descriptions-item label="生效时间">{{ effectiveAgreement.effectiveTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="协议哈希" :span="isNarrow ? 1 : 3">
              <span class="hash-text">{{ effectiveAgreement.agreementHash }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <h4 class="section-title">
          {{ effectiveAgreement && effectiveAgreement.id !== agreement.id ? '最新待确认提案' : '当前协议' }}
        </h4>
        <el-descriptions :column="isNarrow ? 1 : 2" border size="small" style="margin-top: 14px">
          <el-descriptions-item label="版本">v{{ agreement.version }}</el-descriptions-item>
          <el-descriptions-item label="比例合计">{{ agreement.totalRatio }}%</el-descriptions-item>
          <el-descriptions-item label="确认人数">
            {{ agreement.confirmedCount || 0 }} / {{ agreement.items.length }}
          </el-descriptions-item>
          <el-descriptions-item label="链上状态">
            <el-tag :type="chainTagType(agreement.chainStatus)" size="small" effect="plain">
              {{ chainStatusText(agreement.chainStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="协议哈希" :span="2">
            <span class="hash-text">{{ agreement.agreementHash }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="协商说明" :span="2">
            {{ agreement.negotiationNote || '协作者未填写协商说明' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ agreement.createTime }}</el-descriptions-item>
          <el-descriptions-item label="生效时间">{{ agreement.effectiveTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="agreement.items" border size="small" style="margin-top: 14px">
          <el-table-column prop="userName" label="协作者" />
          <el-table-column prop="shareRatio" label="比例" width="100">
            <template #default="{ row }">{{ row.shareRatio }}%</template>
          </el-table-column>
          <el-table-column label="确认状态" width="150">
            <template #default="{ row }">
              <el-tag :type="row.confirmed ? 'success' : 'info'" size="small">
                {{ row.confirmed ? '已确认' : '待确认' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="confirmedAt" label="确认时间" width="180">
            <template #default="{ row }">{{ row.confirmedAt || '-' }}</template>
          </el-table-column>
        </el-table>

        <el-button
          v-if="agreement.status === 0 && canCurrentUserConfirm"
          type="success"
          style="margin-top: 14px"
          :loading="agreementLoading"
          @click="handleConfirmAgreement"
        >
          确认当前协议
        </el-button>
      </template>
      <el-empty v-else description="尚未生成协作协议" :image-size="64" />
    </el-card>

    <!-- 协议版本历史 -->
    <el-card style="margin-top: 20px" v-if="selectedCollaboration && agreementHistory.length > 0">
      <template #header>
        <span>协议版本历史</span>
      </template>
      <el-table :data="agreementHistory" border size="small">
        <el-table-column label="版本" width="90">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="agreementHistoryTagType(row.status)" size="small">
              {{ agreementHistoryStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="比例" width="120">
          <template #default="{ row }">{{ row.totalRatio }}%</template>
        </el-table-column>
        <el-table-column label="确认" width="100">
          <template #default="{ row }">{{ row.confirmedCount }}/{{ row.items.length }}</template>
        </el-table-column>
        <el-table-column prop="negotiationNote" label="协商说明" min-width="180">
          <template #default="{ row }">{{ row.negotiationNote || '-' }}</template>
        </el-table-column>
        <el-table-column label="协议哈希" min-width="260">
          <template #default="{ row }"><span class="hash-text">{{ row.agreementHash }}</span></template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
      </el-table>
    </el-card>

    <!-- 协作者列表 -->
    <el-card style="margin-top: 20px" v-if="selectedCollaboration">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>协作者列表</span>
          <el-button type="primary" size="small" @click="showAddDialog = true" v-if="selectedCollaboration.role === '所有者'">
            添加协作者
          </el-button>
        </div>
      </template>
      <el-table :data="collaboratorRows" border>
        <el-table-column prop="userId" label="用户 ID" width="100" />
        <el-table-column prop="userName" label="用户名" width="150" />
        <el-table-column prop="email" label="邮箱" width="200" />
        <el-table-column label="协议比例" width="120">
          <template #default="{ row }">
            <span v-if="row.agreementRatio !== null">{{ row.agreementRatio }}%</span>
            <el-tag v-else type="info" size="small" effect="plain">未定</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contributedAmount" label="已获收益">
          <template #default="{ row }">
            <span style="color: #67c23a">{{ row.contributedAmount }} 元</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '正常' : '退出' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 授权订单与收益闭环 -->
    <el-card style="margin-top: 20px" v-if="selectedCollaboration && selectedCollaboration.role === '所有者'">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>授权订单</span>
          <el-button
            type="success"
            @click="handleDistribute"
            :loading="distributeLoading"
            :disabled="!hasSettleableRevenue"
          >
            按协议分配收益
          </el-button>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="买方名称">
          <el-input v-model="orderBuyer" placeholder="例如：某品牌客户" style="width: 210px" />
        </el-form-item>
        <el-form-item label="授权金额">
          <el-input-number v-model="orderAmount" :min="0.01" :precision="2" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCreateOrder" :loading="orderLoading">
            创建订单
          </el-button>
        </el-form-item>
      </el-form>
      <el-table :data="licenseOrders" border size="small">
        <el-table-column prop="orderNo" label="订单号" min-width="210" />
        <el-table-column prop="buyerName" label="买方" min-width="130" />
        <el-table-column prop="amount" label="金额" width="110">
          <template #default="{ row }">{{ row.amount }} 元</template>
        </el-table-column>
        <el-table-column label="收款协议" width="120">
          <template #default="{ row }">
            <span v-if="revenueForOrder(row.id)">v{{ revenueForOrder(row.id).agreementVersion }}</span>
            <span v-else class="muted-text">待确认收款</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusType(row.status)" size="small">
              {{ orderStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="warning"
              size="small"
              @click="handleConfirmOrder(row)"
            >
              确认收款
            </el-button>
            <span v-else class="muted-text">已锁定</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="licenseOrders.length === 0" description="暂无授权订单" :image-size="64" />
      <p class="settlement-note">
        确认收款后生成收入事件；分账只能由收入事件触发，同一订单不会重复分账。
      </p>
    </el-card>

    <!-- 收入事件与结算凭证 -->
    <el-card style="margin-top: 20px" v-if="selectedCollaboration">
      <template #header>
        <div class="card-head">
          <span>收入事件与结算凭证</span>
          <el-tag type="info" size="small" effect="plain">
            收入事件 {{ revenueEvents.length }} 条 / 凭证 {{ settlementReceipts.length }} 张
          </el-tag>
        </div>
      </template>

      <h4 class="section-title">收入事件</h4>
      <el-table :data="revenueEvents" border size="small">
        <el-table-column prop="id" label="事件 ID" width="90" />
        <el-table-column prop="orderId" label="订单 ID" width="90" />
        <el-table-column prop="amount" label="金额" width="110">
          <template #default="{ row }">{{ row.amount }} 元</template>
        </el-table-column>
        <el-table-column label="绑定协议" width="120">
          <template #default="{ row }">v{{ row.agreementVersion }}</template>
        </el-table-column>
        <el-table-column label="事件哈希" min-width="250">
          <template #default="{ row }"><span class="hash-text">{{ row.eventHash }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
              {{ row.status === 1 ? '已结算' : '待结算' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="产生时间" width="170" />
      </el-table>
      <el-empty v-if="revenueEvents.length === 0" description="确认收款后会生成收入事件" :image-size="56" />

      <h4 class="section-title">结算凭证</h4>
      <el-table :data="settlementReceipts" border size="small">
        <el-table-column prop="receiptNo" label="凭证号" min-width="190" />
        <el-table-column prop="agreementVersion" label="协议版本" width="100">
          <template #default="{ row }">v{{ row.agreementVersion }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="结算金额" width="120">
          <template #default="{ row }">{{ row.totalAmount }} 元</template>
        </el-table-column>
        <el-table-column prop="itemCount" label="分账人数" width="100" />
        <el-table-column label="凭证哈希" min-width="250">
          <template #default="{ row }"><span class="hash-text">{{ row.receiptHash }}</span></template>
        </el-table-column>
        <el-table-column label="链上状态" width="110">
          <template #default="{ row }">
            <el-tag :type="chainTagType(row.chainStatus)" size="small" effect="plain">
              {{ chainStatusText(row.chainStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交易哈希" min-width="220">
          <template #default="{ row }">
            <span v-if="row.blockchainTxHash" class="hash-text">{{ row.blockchainTxHash }}</span>
            <span v-else class="muted-text">pending，不伪造交易哈希</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="settlementReceipts.length === 0" description="分账后会生成不可重复的结算凭证" :image-size="56" />
      <p class="settlement-note">
        凭证先写入业务库和 chain_anchor outbox；只有真实链适配器确认后才显示交易哈希和区块号。
      </p>
    </el-card>

    <!-- 添加协作者对话框 -->
    <el-dialog v-model="showAddDialog" title="添加协作者" width="500px">
      <el-form :model="collabForm" label-width="80px">
        <el-form-item label="搜索用户">
          <el-input
            v-model="searchKeyword"
            placeholder="输入用户名或邮箱搜索"
            @input="handleSearch"
          >
            <template #append>
              <el-button @click="handleSearch">搜索</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="选择用户" v-if="searchResults.length > 0">
          <el-select v-model="collabForm.userId" placeholder="选择用户" style="width: 100%">
            <el-option
              v-for="user in searchResults"
              :key="user.id"
              :label="user.username + ' (' + user.email + ')'"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分成比例">
          <el-input-number v-model="collabForm.shareRatio" :min="1" :max="100" />
          <span style="margin-left: 10px; color: #999">%</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddCollaborator">确定</el-button>
      </template>
    </el-dialog>

    <!-- 生成协议对话框 -->
    <el-dialog v-model="showAgreementDialog" title="生成协作协议" width="560px">
      <el-table :data="activeCollaborators" border size="small">
        <el-table-column prop="userName" label="协作者" />
        <el-table-column label="分成比例" width="180">
          <template #default="{ row }">
            <el-input-number v-model="draftRatios[row.userId]" :min="1" :max="100" size="small" />
            <span class="ratio-unit">%</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="ratio-total" :class="{ invalid: draftRatioTotal !== 100 }">
        合计 {{ draftRatioTotal }}%
      </div>
      <el-form-item label="协商说明" style="margin-top: 14px">
        <el-input
          v-model="negotiationNote"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="例如：主账号负责创意与生成，协作者负责后期调色，双方协商 50/50"
        />
      </el-form-item>
      <template #footer>
        <el-button @click="showAgreementDialog = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="draftRatioTotal !== 100"
          :loading="agreementLoading"
          @click="handleCreateAgreement"
        >
          生成并确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { collabApi, userApi } from '../api'

const myCollaborations = ref([])
const selectedCollaboration = ref(null)
const collaborators = ref([])
const agreement = ref(null)
const effectiveAgreement = ref(null)
const agreementHistory = ref([])
const revenueEvents = ref([])
const settlementReceipts = ref([])
const licenseOrders = ref([])
const orderBuyer = ref('')
const orderAmount = ref(1)
const negotiationNote = ref('')
const showAddDialog = ref(false)
const showAgreementDialog = ref(false)
const orderLoading = ref(false)
const distributeLoading = ref(false)
const agreementLoading = ref(false)
const searchKeyword = ref('')
const searchResults = ref([])
const currentUserId = Number(localStorage.getItem('userId') || 0)
const draftRatios = reactive({})
const narrowQuery = window.matchMedia('(max-width: 768px)')
const isNarrow = ref(narrowQuery.matches)
const syncNarrow = (event) => { isNarrow.value = event.matches }

const collabForm = reactive({
  userId: null,
  shareRatio: 30
})

const agreementStatusText = computed(() => {
  if (!agreement.value) return ''
  return agreement.value.status === 1 ? '已生效' : '待全部确认'
})

const agreementTagType = computed(() => agreement.value?.status === 1 ? 'success' : 'warning')

const agreementNotice = computed(() => {
  if (!agreement.value) return ''
  if (agreement.value.status === 1) {
    return '协议已由全部协作者确认，可以按该版本比例生成本地结算流水。'
  }
  if (effectiveAgreement.value) {
    return `v${effectiveAgreement.value.version} 仍是当前生效的结算依据；v${agreement.value.version} 尚未全部确认，不会改变历史收入结算。`
  }
  const pending = agreement.value.items.filter(item => !item.confirmed).length
  return `还有 ${pending} 位协作者未确认；全部确认前不能分账。`
})

const agreementNoticeType = computed(() => agreement.value?.status === 1 ? 'success' : 'warning')

const canCurrentUserConfirm = computed(() => {
  if (!agreement.value || agreement.value.status !== 0) return false
  const item = agreement.value.items.find(row => row.userId === currentUserId)
  return Boolean(item && !item.confirmed)
})

const activeCollaborators = computed(() => collaborators.value.filter(user => user.status === 1))
const hasSettleableRevenue = computed(() => revenueEvents.value.some(event => event.status === 0))

const collaboratorRows = computed(() => {
  const ratios = new Map((effectiveAgreement.value?.items || []).map(item => [item.userId, item.shareRatio]))
  return collaborators.value.map(user => ({
    ...user,
    agreementRatio: ratios.has(user.userId) ? ratios.get(user.userId) : null
  }))
})

const draftRatioTotal = computed(() => activeCollaborators.value.reduce(
  (total, user) => total + Number(draftRatios[user.userId] || 0),
  0
))

const chainStatusText = (status) => {
  if (status === 1) return '已上链'
  if (status === 2) return '上链失败'
  return '待上链'
}

const chainTagType = (status) => {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

const loadMyCollaborations = async () => {
  try {
    const res = await collabApi.myCollaborations()
    myCollaborations.value = res.data || []
    console.log('Loaded collaborations:', myCollaborations.value)
  } catch (e) {
    console.error('Failed to load collaborations:', e)
    ElMessage.error('加载协作项目失败')
  }
}

const selectCollaboration = async (row) => {
  console.log('Selecting collaboration:', row)
  // 使用 JSON 深拷贝确保响应式
  selectedCollaboration.value = JSON.parse(JSON.stringify(row))
  await loadAgreement()
  await Promise.all([
    loadCollaborators(),
    loadAgreementHistory(),
    loadOrders(),
    loadRevenueEvents(),
    loadSettlementReceipts()
  ])
}

const loadCollaborators = async () => {
  if (!selectedCollaboration.value) {
    console.log('No collaboration selected')
    return
  }
  try {
    const res = await collabApi.listCollaborators(selectedCollaboration.value.id)
    collaborators.value = res.data || []
    seedDraftRatios()
    console.log('Loaded collaborators:', collaborators.value)
  } catch (e) {
    console.error('Failed to load collaborators:', e)
    ElMessage.error('加载协作者失败')
  }
}

const loadAgreement = async () => {
  if (!selectedCollaboration.value) {
    agreement.value = null
    effectiveAgreement.value = null
    return
  }
  try {
    const res = await collabApi.currentAgreement(selectedCollaboration.value.id)
    agreement.value = res.data || null
  } catch (e) {
    agreement.value = null
  }
  try {
    const res = await collabApi.effectiveAgreement(selectedCollaboration.value.id)
    effectiveAgreement.value = res.data || null
  } catch (e) {
    effectiveAgreement.value = null
  }
}

const loadAgreementHistory = async () => {
  if (!selectedCollaboration.value) {
    agreementHistory.value = []
    return
  }
  try {
    const res = await collabApi.agreementHistory(selectedCollaboration.value.id)
    agreementHistory.value = res.data || []
  } catch (e) {
    agreementHistory.value = []
  }
}

const seedDraftRatios = () => {
  Object.keys(draftRatios).forEach(key => delete draftRatios[key])
  const ratioSource = agreement.value?.status === 0 ? agreement.value : effectiveAgreement.value
  const agreementRatios = new Map(
    (ratioSource?.items || []).map(item => [item.userId, item.shareRatio])
  )
  const users = activeCollaborators.value
  const baseRatio = users.length ? Math.floor(100 / users.length) : 100
  const remainder = 100 - baseRatio * users.length
  users.forEach((user, index) => {
    draftRatios[user.userId] = agreementRatios.get(user.userId) ?? (baseRatio + (index === 0 ? remainder : 0))
  })
}

const openAgreementDialog = () => {
  seedDraftRatios()
  negotiationNote.value = ''
  showAgreementDialog.value = true
}

const handleCreateAgreement = async () => {
  if (!selectedCollaboration.value || draftRatioTotal.value !== 100) return
  agreementLoading.value = true
  try {
    const items = activeCollaborators.value.map(user => ({
      userId: user.userId,
      shareRatio: Number(draftRatios[user.userId])
    }))
    const res = await collabApi.createAgreement(
      selectedCollaboration.value.id,
      items,
      negotiationNote.value.trim()
    )
    agreement.value = res.data
    showAgreementDialog.value = false
    ElMessage.success(agreement.value.status === 1 ? '协议已生效' : '协议已生成，等待协作者确认')
    await loadMyCollaborations()
    await loadAgreement()
    await loadCollaborators()
    await loadAgreementHistory()
  } catch (e) {}
  finally { agreementLoading.value = false }
}

const handleConfirmAgreement = async () => {
  if (!agreement.value) return
  agreementLoading.value = true
  try {
    const res = await collabApi.confirmAgreement(agreement.value.id)
    const wasPending = agreement.value.status === 0
    agreement.value = res.data
    ElMessage.success(agreement.value.status === 1 ? '全部协作者已确认，协议生效' : '协议确认成功')
    await loadMyCollaborations()
    await loadAgreement()
    await loadCollaborators()
    await loadAgreementHistory()
    if (wasPending && agreement.value?.status === 1) {
      await loadRevenueEvents()
    }
  } catch (e) {}
  finally { agreementLoading.value = false }
}

const handleSearch = async () => {
  if (!searchKeyword.value) {
    searchResults.value = []
    return
  }
  try {
    const res = await userApi.search(searchKeyword.value)
    searchResults.value = res.data || []
  } catch (e) {}
}

const handleAddCollaborator = async () => {
  if (!collabForm.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  try {
    await collabApi.addCollaborator(selectedCollaboration.value.id, collabForm)
    ElMessage.success('协作者添加成功')
    showAddDialog.value = false
    searchKeyword.value = ''
    searchResults.value = []
    collabForm.userId = null
    collabForm.shareRatio = 30
    await loadCollaborators()
  } catch (e) {}
}

const loadOrders = async () => {
  if (!selectedCollaboration.value) {
    licenseOrders.value = []
    return
  }
  try {
    const res = await collabApi.listOrders(selectedCollaboration.value.id)
    licenseOrders.value = res.data || []
  } catch (e) {
    licenseOrders.value = []
  }
}

const loadRevenueEvents = async () => {
  if (!selectedCollaboration.value) {
    revenueEvents.value = []
    return
  }
  try {
    const res = await collabApi.listRevenueEvents(selectedCollaboration.value.id)
    revenueEvents.value = res.data || []
  } catch (e) {
    revenueEvents.value = []
  }
}

const loadSettlementReceipts = async () => {
  if (!selectedCollaboration.value) {
    settlementReceipts.value = []
    return
  }
  try {
    const res = await collabApi.listSettlementReceipts(selectedCollaboration.value.id)
    settlementReceipts.value = res.data || []
  } catch (e) {
    settlementReceipts.value = []
  }
}

const revenueForOrder = (orderId) => revenueEvents.value.find(item => item.orderId === orderId)

const handleCreateOrder = async () => {
  if (!orderBuyer.value.trim()) {
    ElMessage.warning('请输入买方名称')
    return
  }
  if (!orderAmount.value || orderAmount.value <= 0) {
    ElMessage.warning('请输入授权金额')
    return
  }
  orderLoading.value = true
  try {
    await collabApi.createOrder(selectedCollaboration.value.id, {
      buyerName: orderBuyer.value.trim(),
      amount: orderAmount.value
    })
    ElMessage.success('授权订单已创建')
    orderBuyer.value = ''
    orderAmount.value = 1
    await loadOrders()
    await loadMyCollaborations()
    await selectCollaboration(selectedCollaboration.value)
  } catch (e) {}
  finally { orderLoading.value = false }
}

const handleConfirmOrder = async (row) => {
  try {
    await collabApi.confirmOrder(row.id)
    ElMessage.success('已确认收款并生成收入事件')
    await Promise.all([loadOrders(), loadRevenueEvents(), loadMyCollaborations()])
  } catch (e) {}
}

const handleDistribute = async () => {
  if (!hasSettleableRevenue.value) {
    ElMessage.warning('当前没有待结算的收入事件')
    return
  }
  distributeLoading.value = true
  try {
    await collabApi.distribute(selectedCollaboration.value.id)
    ElMessage.success('结算凭证已生成，链上锚定状态为 pending')
    await Promise.all([
      loadMyCollaborations(),
      loadRevenueEvents(),
      loadSettlementReceipts(),
      loadAgreementHistory()
    ])
  } catch (e) {}
  finally { distributeLoading.value = false }
}

const orderStatusText = (status) => {
  const map = { 0: '待收款', 1: '已收款', 2: '已分账', 3: '已取消' }
  return map[status] || '未知'
}

const orderStatusType = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }
  return map[status] || 'info'
}

const agreementHistoryStatusText = (status) => {
  const map = { 0: '待全部确认', 1: '已生效', 2: '已被替代', 3: '已撤回' }
  return map[status] || '未知'
}

const agreementHistoryTagType = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }
  return map[status] || 'info'
}

onMounted(() => {
  narrowQuery.addEventListener('change', syncNarrow)
  loadMyCollaborations()
})

onUnmounted(() => {
  narrowQuery.removeEventListener('change', syncNarrow)
})
</script>

<style scoped>
.collab-container {
  padding: 30px;
  max-width: 1400px;
  margin: 0 auto;
}
.collab-container h2 {
  margin-bottom: 20px;
  color: #333;
}
.card-head,
.head-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.hash-text {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  word-break: break-all;
  color: #606266;
}
.settlement-note {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.section-title {
  margin: 18px 0 10px;
  color: #303133;
  font-size: 14px;
}
.section-title:first-of-type {
  margin-top: 0;
}
.muted-text {
  color: #909399;
  font-size: 12px;
}
.ratio-unit {
  margin-left: 6px;
  color: #909399;
}
.ratio-total {
  margin-top: 12px;
  text-align: right;
  color: #67c23a;
  font-weight: 600;
}
.ratio-total.invalid {
  color: #f56c6c;
}
@media (max-width: 768px) {
  .collab-container {
    padding: 16px 12px;
  }
  .collab-container h2 {
    font-size: 20px;
  }
  .card-head {
    flex-wrap: wrap;
  }
}
</style>
