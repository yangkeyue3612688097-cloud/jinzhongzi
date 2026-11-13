<template>
  <div class="verify-page">
    <header class="verify-top">
      <div class="brand">存证验证</div>
      <div class="top-hash" v-if="data">{{ data.dataHash }}</div>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-result
      v-else-if="notFound"
      icon="warning"
      title="未找到该存证"
      sub-title="请确认链接是否完整，或联系作者重新获取"
    >
      <template #extra>
        <el-button @click="$router.push('/login')">返回</el-button>
      </template>
    </el-result>

    <template v-else-if="data">
      <section class="status-band" :class="statusClass">
        <el-icon class="status-icon"><component :is="statusIcon" /></el-icon>
        <div class="status-text">
          <h1>{{ publicStatusTitle }}</h1>
          <p>{{ publicStatusSubtitle }}</p>
        </div>
      </section>

      <section class="panel chain-verification">
        <div class="panel-title">
          <span>三层公开核验</span>
          <el-tag :type="chainReviewTagType" size="small" effect="plain">
            {{ chainReviewLabel }}
          </el-tag>
        </div>
        <el-skeleton v-if="chainVerificationLoading" :rows="2" animated />
        <el-alert
          v-else-if="chainVerificationError"
          type="warning"
          :closable="false"
          show-icon
          :title="chainVerificationError"
        />
        <el-descriptions v-else :column="1" border size="small">
          <el-descriptions-item label="1. 本地业务事实">
            <el-tag type="success" size="small">已找到作品与规范化内容</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="2. 上链发件箱">
            <el-tag :type="outboxState.type" size="small" effect="plain">
              {{ outboxState.label }}
            </el-tag>
            <span class="chain-hint">{{ outboxState.note }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="3. 联盟链复核">
            <el-tag :type="chainReviewTagType" size="small">
              {{ chainReviewLabel }}
            </el-tag>
            <span class="chain-hint">{{ chainReviewNote }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="核验模式">
            {{ chainVerification?.mode || data.chainMode || 'pending' }}
          </el-descriptions-item>
          <el-descriptions-item label="链上交易哈希">
            <span class="mono wrap">{{ chainTxHash || '暂无真实交易哈希' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="区块高度">
            {{ chainBlockNumber ?? '暂未确认' }}
          </el-descriptions-item>
        </el-descriptions>
        <p v-if="chainVerification?.message" class="chain-message">
          {{ chainVerification.message }}
        </p>
      </section>

      <el-row :gutter="16" class="main-row">
        <el-col :xs="24" :md="14">
          <div class="panel">
            <div class="panel-title">存证信息</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="作品">{{ data.title || '-' }}</el-descriptions-item>
              <el-descriptions-item label="作者">{{ data.authorName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创作者地址">
                <span class="mono wrap">{{ data.creatorAddress || '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="创作者签名">
                <el-tag :type="data.signatureVerified ? 'success' : 'info'" size="small">
                  {{ data.signatureVerified ? '已验证' : '未签名' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="类型">{{ data.fileType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="存证时间">{{ data.createTime }}</el-descriptions-item>
              <el-descriptions-item label="链上状态">
                <el-tag :type="chainTagType" size="small" effect="plain">{{ chainText }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="区块号" v-if="data.blockNumber">{{ data.blockNumber }}</el-descriptions-item>
              <el-descriptions-item label="交易哈希" v-if="data.blockchainTxHash">
                <span class="mono">{{ data.blockchainTxHash }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="文件指纹">
                <span class="mono wrap">{{ data.fileHash }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>

        <el-col :xs="24" :md="10">
          <div class="panel">
            <div class="panel-title">文件校验</div>
            <input ref="fileInput" type="file" class="hidden-input" @change="handleFilePick" />
            <div
              class="drop-zone"
              :class="{ dragging }"
              @click="fileInput.click()"
              @dragover.prevent="dragging = true"
              @dragleave.prevent="dragging = false"
              @drop.prevent="handleDrop"
            >
              <el-icon :size="22"><UploadFilled /></el-icon>
              <span>拖入文件比对指纹</span>
            </div>
            <div v-if="fileCheck" class="check-result" :class="fileCheck.match ? 'ok' : 'bad'">
              <el-icon><component :is="fileCheck.match ? CircleCheckFilled : CircleCloseFilled" /></el-icon>
              <div>
                <div>{{ fileCheck.match ? '文件与存证一致' : '文件与存证不一致' }}</div>
                <div class="mono small">{{ fileCheck.hash }}</div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="panel">
        <div class="panel-title">生成参数</div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="来源">{{ sourceTypeText(gen.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="工作流哈希" :span="2">
            <span class="mono wrap">{{ gen.workflowHash || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="模型">{{ gen.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ gen.modelVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="采样器">{{ gen.sampler || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Seed">{{ gen.seed || '-' }}</el-descriptions-item>
          <el-descriptions-item label="步数">{{ gen.steps || '-' }}</el-descriptions-item>
          <el-descriptions-item label="CFG">{{ gen.cfgScale || '-' }}</el-descriptions-item>
          <el-descriptions-item label="调度器">{{ gen.scheduler || '-' }}</el-descriptions-item>
          <el-descriptions-item label="尺寸">
            {{ gen.width && gen.height ? gen.width + ' x ' + gen.height : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="请求 ID">{{ gen.requestId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提示词" :span="3">
            <span class="prompt">{{ gen.prompt || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="反向提示词" :span="3" v-if="gen.negativePrompt">
            <span class="prompt">{{ gen.negativePrompt }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="panel">
        <div class="panel-title">
          <span>规范化内容</span>
          <div class="panel-actions">
            <el-button size="small" @click="downloadManifest">下载来源清单</el-button>
            <el-button size="small" @click="downloadCertificate">下载存证证书</el-button>
            <el-button size="small" @click="copyPayload">复制</el-button>
            <el-button size="small" @click="recompute">重新计算哈希</el-button>
          </div>
        </div>
        <pre class="payload">{{ prettyPayload }}</pre>
        <div class="algo-line">
          <span class="muted">算法</span>
          <span class="mono">{{ data.hashAlgorithm }}</span>
        </div>
        <div v-if="recomputeResult" class="check-result" :class="recomputeResult.match ? 'ok' : 'bad'">
          <el-icon><component :is="recomputeResult.match ? CircleCheckFilled : CircleCloseFilled" /></el-icon>
          <span>{{ recomputeResult.match ? '哈希复算一致' : '哈希复算不一致' }}</span>
          <span class="mono small">{{ recomputeResult.hash }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { publicApi, publicChainApi } from '../api'
import { sha256File, sha256Hex } from '../utils/hash'

const route = useRoute()
const loading = ref(true)
const notFound = ref(false)
const data = ref(null)
const fileInput = ref(null)
const dragging = ref(false)
const fileCheck = ref(null)
const recomputeResult = ref(null)
const chainVerificationLoading = ref(false)
const chainVerification = ref(null)
const chainVerificationError = ref('')

const gen = computed(() => data.value?.generation || {})
const prettyPayload = computed(() => {
  if (!data.value?.canonicalPayload) return ''
  try {
    return JSON.stringify(JSON.parse(data.value.canonicalPayload), null, 2)
  } catch (e) {
    return data.value.canonicalPayload
  }
})

const chainText = computed(() => {
  const status = data.value?.chainStatus
  if (status === 1) return '已上链'
  if (status === 2) return '上链失败'
  return '待上链'
})

const chainTagType = computed(() => {
  const status = data.value?.chainStatus
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
})

const onChainConfirmed = computed(() => chainVerification.value?.onChainVerified === true)
const chainTxHash = computed(() => chainVerification.value?.blockchainTxHash || '')
const chainBlockNumber = computed(() => chainVerification.value?.blockNumber ?? null)
const outboxState = computed(() => {
  const verification = chainVerification.value
  if (!verification?.anchorFound) {
    return { label: '未登记', type: 'warning', note: '未找到该哈希对应的上链发件箱记录。' }
  }
  if (verification.anchorStatus === 1) {
    return { label: '已生成链上锚定记录', type: 'success', note: '本地事实已进入待公开复核的上链记录。' }
  }
  if (verification.anchorStatus === 2) {
    return { label: '上链失败', type: 'danger', note: '可由作品作者在作品详情页重试。' }
  }
  return { label: '待链上确认', type: 'warning', note: '发件箱已登记，等待联盟链 SDK 返回真实回执。' }
})
const chainReviewLabel = computed(() => {
  if (onChainConfirmed.value) return '链上复核通过'
  if (chainVerification.value?.verificationAvailable) return '链上复核未通过'
  return '等待 FISCO 确认'
})
const chainReviewTagType = computed(() => {
  if (onChainConfirmed.value) return 'success'
  if (chainVerification.value?.verificationAvailable) return 'danger'
  return 'warning'
})
const chainReviewNote = computed(() => {
  if (onChainConfirmed.value) return '链上合约已确认该业务标识与数据哈希。'
  if (chainVerification.value?.verificationAvailable) return '当前未取得合约验证通过结果，请检查交易与合约配置。'
  return '当前为本地存证模式，不宣称已完成真实链上验证。'
})
const publicStatusTitle = computed(() => onChainConfirmed.value ? '存证已通过链上复核' : '本地存证有效')
const publicStatusSubtitle = computed(() => {
  if (onChainConfirmed.value) return '文件指纹、规范化内容与链上锚定记录一致。'
  if (chainVerification.value?.verificationAvailable) return '本地数据可验证，但链上复核未通过。'
  return '文件指纹与规范化内容已在本地校验，等待联盟链确认。'
})
const statusClass = computed(() => onChainConfirmed.value ? 'anchored' : 'pending')
const statusIcon = CircleCheckFilled
const statusTitle = computed(() => data.value?.chainStatus === 1 ? '存证有效，已在链上' : '存证有效')
const statusSubtitle = computed(() => data.value?.chainMessage || '')

const loadChainVerification = async () => {
  if (!data.value?.workId || !data.value?.dataHash) return
  chainVerificationLoading.value = true
  chainVerificationError.value = ''
  try {
    const res = await publicChainApi.verify({
      businessType: 'work_evidence',
      businessId: data.value.workId,
      dataHash: data.value.dataHash
    })
    const body = res.data || {}
    if (body.code !== 200 || !body.data) {
      chainVerificationError.value = body.message || '链上核验暂时不可用'
      return
    }
    chainVerification.value = body.data
  } catch (e) {
    chainVerificationError.value = '链上核验暂时不可用，请稍后重试'
  } finally {
    chainVerificationLoading.value = false
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await publicApi.get('/public/evidence/' + route.params.hash)
    const body = res.data || {}
    if (body.code !== 200 || !body.data) {
      notFound.value = true
    } else {
      data.value = body.data
    }
  } catch (e) {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

const handleDrop = async (event) => {
  dragging.value = false
  const file = event.dataTransfer.files && event.dataTransfer.files[0]
  if (file) await compareFile(file)
}

const handleFilePick = async (event) => {
  const file = event.target.files && event.target.files[0]
  if (file) await compareFile(file)
  event.target.value = ''
}

const compareFile = async (file) => {
  try {
    const hash = await sha256File(file)
    fileCheck.value = { hash, match: hash === data.value.fileHash }
  } catch (e) {
    ElMessage.error('无法读取文件')
  }
}

const copyPayload = async () => {
  try {
    await navigator.clipboard.writeText(data.value.canonicalPayload)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

const recompute = async () => {
  const hash = await sha256Hex(data.value.canonicalPayload)
  recomputeResult.value = { hash, match: hash === data.value.dataHash }
}

const downloadManifest = () => {
  window.open('/api/public/evidence/' + data.value.dataHash + '/manifest', '_blank')
}

const downloadCertificate = () => {
  window.open('/api/public/evidence/' + data.value.dataHash + '/certificate', '_blank')
}

const sourceTypeText = (sourceType) => {
  const map = { comfyui: 'ComfyUI 本地生成', manual: '手工上传', cloud: '云端生成' }
  return map[sourceType] || sourceType || '手工上传'
}

onMounted(async () => {
  await load()
  if (data.value) await loadChainVerification()
})
</script>

<style scoped>
.verify-page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 20px 48px;
}
.verify-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 56px;
}
.brand {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.top-hash {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}
.status-band {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 8px;
  border: 1px solid #e1f3d8;
  background: #f0f9eb;
}
.status-band.pending {
  border-color: #faecd8;
  background: #fdf6ec;
}
.status-band.anchored .status-icon {
  color: #67c23a;
}
.status-band.pending .status-icon {
  color: #e6a23c;
}
.status-icon {
  font-size: 28px;
  flex: none;
}
.chain-verification {
  margin-bottom: 0;
}
.chain-hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.chain-message {
  margin: 10px 0 0;
  color: #606266;
  font-size: 13px;
}
.status-text h1 {
  font-size: 18px;
  color: #303133;
  margin-bottom: 2px;
}
.status-text p {
  font-size: 13px;
  color: #606266;
}
.main-row {
  margin-top: 16px;
}
.panel {
  margin-top: 16px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.main-row .panel {
  margin-top: 0;
  height: 100%;
}
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.panel-actions {
  display: flex;
  gap: 8px;
}
.hidden-input {
  display: none;
}
.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 108px;
  border: 1px dashed #c0c4cc;
  border-radius: 6px;
  color: #909399;
  font-size: 13px;
  cursor: pointer;
  transition: border-color .2s, background .2s;
}
.drop-zone:hover,
.drop-zone.dragging {
  border-color: #409eff;
  background: #f2f8ff;
}
.check-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
}
.check-result.ok {
  background: #f0f9eb;
  color: #529b2e;
}
.check-result.bad {
  background: #fef0f0;
  color: #c45656;
}
.payload {
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  margin: 0;
  background: #f7f8fa;
  border-radius: 6px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
.algo-line {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  font-size: 12px;
}
.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
.wrap {
  word-break: break-all;
}
.small {
  font-size: 11px;
}
.muted {
  color: #909399;
}
.prompt {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
