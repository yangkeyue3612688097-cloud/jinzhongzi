<template>
  <div class="version-container">
    <el-page-header @back="$router.push('/work/' + workId)" title="返回作品详情" />
    <h2 style="margin: 20px 0">版本历史</h2>

    <el-card style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>创建新版本</span>
        </div>
      </template>
      <el-form :model="versionForm" label-width="100px">
        <el-form-item label="修改说明">
          <el-input v-model="versionForm.changeDescription" type="textarea" :rows="3" placeholder="描述本次修改内容" />
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            drag
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="1"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCreateVersion" :loading="createLoading">
            创建新版本
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-timeline>
      <el-timeline-item
        v-for="version in versions"
        :key="version.id"
        :timestamp="version.createTime"
        placement="top"
        :color="version.status === 2 ? '#67c23a' : version.status === 1 ? '#409eff' : '#909399'"
      >
        <el-card>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <div>
              <el-tag type="primary" size="large">版本 {{ version.versionNumber }}</el-tag>
              <el-tag :type="version.status === 2 ? 'success' : version.status === 1 ? 'info' : 'warning'" style="margin-left: 10px">
                {{ version.status === 2 ? '已确认' : version.status === 1 ? '已提交' : '草稿' }}
              </el-tag>
            </div>
            <el-button
              v-if="version.status === 1"
              type="success"
              size="small"
              @click="handleConfirm(version.id)"
            >
              确认此版本
            </el-button>
          </div>
          <div class="chain-state-row">
            <el-tag type="success" size="small" effect="plain">本地版本哈希已生成</el-tag>
            <el-tag :type="outboxType(version)" size="small" effect="plain">
              发件箱：{{ outboxText(version) }}
            </el-tag>
            <el-tag :type="reviewType(version)" size="small" effect="plain">
              链上：{{ reviewText(version) }}
            </el-tag>
          </div>
          <el-descriptions :column="2" style="margin-top: 15px">
            <el-descriptions-item label="创建者 ID">{{ version.creatorId }}</el-descriptions-item>
            <el-descriptions-item label="文件哈希">
              <el-text class="hash-text" type="info">{{ version.fileHash }}</el-text>
            </el-descriptions-item>
            <el-descriptions-item label="版本哈希">
              <el-text class="hash-text" type="primary">{{ version.versionHash || '-' }}</el-text>
            </el-descriptions-item>
            <el-descriptions-item label="修改说明" :span="2">
              {{ version.changeDescription || '无' }}
            </el-descriptions-item>
            <el-descriptions-item label="交易哈希" :span="2" v-if="version.blockchainTxHash">
              <el-text class="hash-text" type="success">{{ version.blockchainTxHash }}</el-text>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-timeline-item>
    </el-timeline>

    <el-empty v-if="versions.length === 0" description="暂无版本记录" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { publicChainApi, versionApi } from '../api'
import { UploadFilled } from '@element-plus/icons-vue'

const route = useRoute()
const workId = ref(route.params.id)
const versions = ref([])
const versionChainStates = ref({})
const createLoading = ref(false)
const file = ref(null)

const versionForm = reactive({
  changeDescription: ''
})

const loadVersions = async () => {
  try {
    const res = await versionApi.history(workId.value)
    versions.value = res.data || []
    await loadVersionChainStates()
  } catch (e) {}
}

const loadVersionChainStates = async () => {
  const statePairs = await Promise.all(versions.value.map(async (version) => {
    if (!version.versionHash) return [version.id, null]
    try {
      const res = await publicChainApi.verify({
        businessType: 'work_evidence',
        businessId: workId.value,
        dataHash: version.versionHash
      })
      const body = res.data || {}
      return [version.id, body.code === 200 ? body.data : null]
    } catch (e) {
      return [version.id, null]
    }
  }))
  versionChainStates.value = Object.fromEntries(statePairs)
}

const outboxText = (version) => {
  const state = versionChainStates.value[version.id]
  if (!state) return '待核验'
  if (!state.anchorFound) return '未登记'
  if (state.anchorStatus === 1) return '已锚定'
  if (state.anchorStatus === 2) return '上链失败'
  return '待链上确认'
}

const outboxType = (version) => {
  const state = versionChainStates.value[version.id]
  if (!state?.anchorFound) return 'warning'
  if (state.anchorStatus === 1) return 'success'
  if (state.anchorStatus === 2) return 'danger'
  return 'warning'
}

const reviewText = (version) => {
  const state = versionChainStates.value[version.id]
  if (!state) return '待核验'
  if (state.onChainVerified === true) return '复核通过'
  if (state.verificationAvailable) return '复核未通过'
  return '等待 FISCO 确认'
}

const reviewType = (version) => {
  const state = versionChainStates.value[version.id]
  if (state?.onChainVerified === true) return 'success'
  if (state?.verificationAvailable) return 'danger'
  return 'warning'
}

const handleFileChange = (uploadFile) => {
  file.value = uploadFile.raw
}

const handleCreateVersion = async () => {
  if (!file.value) {
    ElMessage.warning('请选择文件')
    return
  }

  createLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file.value)
    await versionApi.create(workId.value, formData, versionForm.changeDescription)
    ElMessage.success('版本创建成功')
    versionForm.changeDescription = ''
    file.value = null
    await loadVersions()
  } catch (e) {}
  finally { createLoading.value = false }
}

const handleConfirm = async (versionId) => {
  try {
    await ElMessageBox.confirm('确定要确认此版本吗？', '提示', { type: 'warning' })
    await versionApi.confirm(versionId)
    ElMessage.success('版本已确认')
    await loadVersions()
  } catch (e) {}
}

onMounted(loadVersions)
</script>

<style scoped>
.version-container {
  padding: 30px;
  max-width: 1000px;
  margin: 0 auto;
}
.hash-text {
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
}
.chain-state-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
</style>
