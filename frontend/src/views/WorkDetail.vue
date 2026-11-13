<template>
  <div class="detail-container" v-if="work">
    <el-page-header @back="$router.push('/home')" :title="'作品详情'" />

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :lg="16">
        <el-card>
          <div class="title-row">
            <h2>{{ work.title }}</h2>
            <el-tag v-if="work.status === 1" type="success" effect="plain">已生成存证</el-tag>
            <el-tag v-else type="info" effect="plain">未存证</el-tag>
          </div>
          <p class="desc">{{ work.description || '暂无描述' }}</p>
          <el-divider />
          <el-descriptions :column="2" border>
            <el-descriptions-item label="作品 ID">{{ work.id }}</el-descriptions-item>
            <el-descriptions-item label="文件类型">{{ work.fileType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ work.createTime }}</el-descriptions-item>
            <el-descriptions-item label="存证时间">{{ latestEvidence ? latestEvidence.createTime : '-' }}</el-descriptions-item>
            <el-descriptions-item label="文件指纹" :span="2">
              <span class="hash-text">{{ work.fileHash || '-' }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card style="margin-top: 20px">
          <template #header><span>生成参数</span></template>
          <el-descriptions v-if="generation" :column="3" border size="small">
            <el-descriptions-item label="来源">
              {{ sourceTypeText(generation.sourceType) }}
            </el-descriptions-item>
            <el-descriptions-item label="工作流哈希" :span="2">
              <span class="hash-text">{{ generation.workflowHash || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="模型">
              {{ generation.modelName || '-' }}
              <span v-if="generation.modelVersion" class="muted">/ {{ generation.modelVersion }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="采样器">{{ generation.sampler || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Seed">{{ generation.seed || '-' }}</el-descriptions-item>
            <el-descriptions-item label="步数">{{ generation.steps ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="CFG">{{ generation.cfgScale ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="调度器">{{ generation.scheduler || '-' }}</el-descriptions-item>
            <el-descriptions-item label="尺寸">
              {{ generation.width && generation.height ? generation.width + ' x ' + generation.height : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="生成时间">{{ generation.generatedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="ComfyUI 请求 ID" :span="2">
              <span class="hash-text">{{ generation.requestId || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="提示词" :span="3">
              <span class="prompt-text">{{ generation.prompt || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="反向提示词" :span="3" v-if="generation.negativePrompt">
              <span class="prompt-text">{{ generation.negativePrompt }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="未记录生成参数" :image-size="60" />
        </el-card>

        <el-card style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>版本历史</span>
              <el-button size="small" @click="$router.push('/work/' + work.id + '/versions')">查看全部</el-button>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="version in recentVersions"
              :key="version.id"
              :timestamp="version.createTime"
              placement="top"
              :color="version.status === 2 ? '#67c23a' : '#409eff'"
            >
              <el-tag size="small">版本 {{ version.versionNumber }}</el-tag>
              <el-tag :type="version.status === 2 ? 'success' : 'info'" size="small" style="margin-left: 8px">
                {{ version.status === 2 ? '已确认' : '已提交' }}
              </el-tag>
              <p class="version-desc">{{ version.changeDescription || '无修改说明' }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="recentVersions.length === 0" description="暂无版本记录" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>存证</span>
              <el-tag :type="chainTagType" size="small" effect="plain">{{ chainLabel }}</el-tag>
            </div>
          </template>
          <template v-if="latestEvidence">
            <div class="evidence-hash">
              <div class="evidence-key">dataHash</div>
              <div class="hash-text">{{ latestEvidence.dataHash }}</div>
            </div>
            <el-button type="primary" plain style="width: 100%" @click="copyVerifyLink(latestEvidence.dataHash)">
              复制验证链接
            </el-button>
            <el-button style="width: 100%; margin: 8px 0 0" @click="openVerify(latestEvidence.dataHash)">
              预览验证页
            </el-button>
            <div style="display: flex; gap: 8px; margin-top: 8px">
              <el-button style="flex: 1" @click="downloadManifest(latestEvidence.dataHash)">
                下载来源清单
              </el-button>
              <el-button style="flex: 1" @click="downloadCertificate(latestEvidence.dataHash)">
                下载存证证书
              </el-button>
            </div>
          </template>
          <el-button
            v-else
            type="primary"
            style="width: 100%"
            :loading="evidenceLoading"
            @click="handleEvidence"
          >
            生成存证
          </el-button>
          <el-button
            v-if="latestEvidence && latestEvidence.chainStatus === 2"
            type="warning"
            plain
            style="width: 100%; margin-top: 8px"
            :loading="retryingEvidence"
            @click="handleRetryLatestEvidence"
          >
            重试上链
          </el-button>
          <p class="chain-note">{{ chainNote }}</p>
        </el-card>

        <el-card style="margin-top: 20px">
          <template #header><span>存证记录</span></template>
          <el-timeline v-if="evidenceList.length">
            <el-timeline-item
              v-for="item in evidenceList"
              :key="item.id"
              :timestamp="item.createTime"
              placement="top"
            >
              <div class="evidence-item">
                <el-tag size="small" :type="statusType(item.chainStatus)" effect="plain">
                  {{ statusText(item.chainStatus) }}
                </el-tag>
                <span class="hash-text">{{ item.dataHash }}</span>
                <div style="display: flex; gap: 8px">
                  <el-button link size="small" type="primary" @click="downloadManifest(item.dataHash)">清单</el-button>
                  <el-button link size="small" type="primary" @click="downloadCertificate(item.dataHash)">证书</el-button>
                </div>
              </div>
              <div class="evidence-actions">
                <el-button link size="small" type="primary" @click="openVerify(item.dataHash)">公开核验</el-button>
                <el-button
                  v-if="item.chainStatus === 2"
                  link
                  size="small"
                  type="warning"
                  :loading="retryingEvidence"
                  @click="handleRetryEvidence"
                >
                  重试上链
                </el-button>
              </div>
              <div v-if="item.blockchainTxHash" class="hash-text muted">tx: {{ item.blockchainTxHash }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无存证记录" :image-size="60" />
        </el-card>

        <el-card style="margin-top: 20px">
          <template #header><span>协作</span></template>
          <el-button type="success" plain style="width: 100%" @click="showInviteDialog = true">邀请协作者</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="showInviteDialog" title="邀请协作者" width="480px">
      <el-form :model="inviteForm" label-width="80px">
        <el-form-item label="搜索用户">
          <el-input v-model="inviteForm.searchKeyword" placeholder="用户名或邮箱" @keyup.enter="handleSearch">
            <template #append>
              <el-button @click="handleSearch">搜索</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="选择用户" v-if="inviteForm.searchResults.length">
          <el-select v-model="inviteForm.inviteeId" placeholder="选择用户" style="width: 100%">
            <el-option
              v-for="user in inviteForm.searchResults"
              :key="user.id"
              :label="user.username + (user.email ? ' (' + user.email + ')' : '')"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="留言">
          <el-input v-model="inviteForm.message" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInviteDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!inviteForm.inviteeId" @click="handleSendInvite">发送邀请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { inviteApi, publicApi, userApi, versionApi, workApi } from '../api'

const route = useRoute()
const router = useRouter()

const work = ref(null)
const generation = ref(null)
const evidenceList = ref([])
const recentVersions = ref([])
const evidenceLoading = ref(false)
const retryingEvidence = ref(false)
const showInviteDialog = ref(false)
const chainEnabled = ref(false)

const inviteForm = reactive({
  inviteeId: null,
  message: '',
  searchKeyword: '',
  searchResults: []
})

const latestEvidence = computed(() => evidenceList.value[0] || null)

const chainLabel = computed(() => {
  if (!latestEvidence.value) return '未存证'
  const status = latestEvidence.value.chainStatus
  if (status === 1) return '已上链'
  if (status === 2) return '上链失败'
  return '待上链'
})

const chainTagType = computed(() => {
  if (!latestEvidence.value) return 'info'
  const status = latestEvidence.value.chainStatus
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
})

const chainNote = computed(() => chainEnabled.value ? '' : '链上合约接入后，同一份证据会自动写入区块链')

const statusText = (status) => {
  if (status === 1) return '已上链'
  if (status === 2) return '上链失败'
  return '待上链'
}

const statusType = (status) => {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

const loadDetail = async () => {
  try {
    const res = await workApi.detail(route.params.id)
    work.value = res.data
  } catch (e) {}
}

const loadGeneration = async () => {
  try {
    const res = await workApi.generation(route.params.id)
    generation.value = res.data
  } catch (e) {}
}

const loadEvidence = async () => {
  try {
    const res = await workApi.evidenceList(route.params.id)
    evidenceList.value = res.data || []
  } catch (e) {}
}

const loadVersions = async () => {
  try {
    const res = await versionApi.history(route.params.id)
    recentVersions.value = (res.data || []).slice(-3).reverse()
  } catch (e) {}
}

const loadChainStatus = async () => {
  try {
    const res = await publicApi.get('/public/chain-status')
    const body = res.data || {}
    chainEnabled.value = Boolean(body.data && body.data.chainEnabled)
  } catch (e) {}
}

const handleEvidence = async () => {
  evidenceLoading.value = true
  try {
    const res = await workApi.evidence(route.params.id)
    const status = res.data.chainStatus
    if (status === 1) {
      ElMessage.success('存证已写入区块链')
    } else {
      ElMessage.success('存证已生成，等待上链')
    }
    await loadDetail()
    await loadEvidence()
  } catch (e) {}
  finally { evidenceLoading.value = false }
}

const handleRetryEvidence = async () => {
  if (!latestEvidence.value) return
  retryingEvidence.value = true
  try {
    const res = await workApi.retryEvidenceAnchor(route.params.id)
    const status = res.data?.chainStatus
    if (status === 1) {
      ElMessage.success('链上锚定已确认')
    } else {
      ElMessage.info('重试已登记，等待联盟链返回真实回执')
    }
    await loadEvidence()
    await loadDetail()
  } catch (e) {}
  finally { retryingEvidence.value = false }
}

const handleRetryLatestEvidence = () => handleRetryEvidence()

const verifyUrl = (dataHash) => window.location.origin + '/verify/' + dataHash

const copyVerifyLink = async (dataHash) => {
  try {
    await navigator.clipboard.writeText(verifyUrl(dataHash))
    ElMessage.success('验证链接已复制')
  } catch (e) {
    ElMessage.warning(verifyUrl(dataHash))
  }
}

const openVerify = (dataHash) => {
  router.push('/verify/' + dataHash)
}

const downloadManifest = (dataHash) => {
  window.open('/api/public/evidence/' + dataHash + '/manifest', '_blank')
}

const downloadCertificate = (dataHash) => {
  window.open('/api/public/evidence/' + dataHash + '/certificate', '_blank')
}

const sourceTypeText = (sourceType) => {
  const map = { comfyui: 'ComfyUI 本地生成', manual: '手工上传', cloud: '云端生成' }
  return map[sourceType] || sourceType || '手工上传'
}

const handleSearch = async () => {
  if (!inviteForm.searchKeyword) {
    inviteForm.searchResults = []
    return
  }
  try {
    const res = await userApi.search(inviteForm.searchKeyword)
    inviteForm.searchResults = res.data || []
  } catch (e) {}
}

const handleSendInvite = async () => {
  if (!inviteForm.inviteeId) {
    ElMessage.warning('请选择用户')
    return
  }
  try {
    await inviteApi.send(route.params.id, inviteForm.inviteeId, inviteForm.message)
    ElMessage.success('邀请已发送')
    showInviteDialog.value = false
    Object.assign(inviteForm, { inviteeId: null, message: '', searchKeyword: '', searchResults: [] })
  } catch (e) {}
}

onMounted(() => {
  loadDetail()
  loadGeneration()
  loadEvidence()
  loadVersions()
  loadChainStatus()
})
</script>

<style scoped>
.detail-container {
  padding: 24px 30px 40px;
  max-width: 1400px;
  margin: 0 auto;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.title-row h2 {
  font-size: 20px;
  color: #303133;
}
.desc {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hash-text {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  word-break: break-all;
  color: #606266;
}
.muted {
  color: #909399;
}
.prompt-text {
  white-space: pre-wrap;
  word-break: break-word;
}
.version-desc {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
}
.evidence-hash {
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.evidence-key {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.evidence-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.evidence-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chain-note {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}
</style>
