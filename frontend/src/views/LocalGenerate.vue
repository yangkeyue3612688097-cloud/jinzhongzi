<template>
  <div class="generate-page">
    <div class="page-head">
      <div class="head-text">
        <h2>本地生成与来源存证</h2>
        <p class="sub">任务由浏览器直接提交到本机 ComfyUI，完成后回读本次执行的真实参数，与图片一起写入存证。</p>
      </div>
      <div class="engine">
        <span class="dot" :class="{ ok: online }"></span>
        <span>{{ online ? 'ComfyUI 已连接' : 'ComfyUI 未连接' }}</span>
        <span v-if="deviceLabel" class="device">{{ deviceLabel }}</span>
        <el-button link :icon="Refresh" title="重新检测 ComfyUI" @click="checkEngine" />
      </div>
    </div>

    <el-alert
      v-if="checked && !online"
      type="warning"
      :closable="false"
      show-icon
      title="未检测到本机 ComfyUI（127.0.0.1:8188），请先启动 ComfyUI，再点右侧刷新按钮。"
      class="offline-alert"
    />

    <div class="layout">
      <section class="panel">
        <div class="panel-title"><span>生成任务</span></div>
        <el-form :model="form" label-position="top">
          <el-form-item label="作品标题">
            <el-input v-model="form.title" maxlength="200" placeholder="留空则使用 ComfyUI 任务号" />
          </el-form-item>
          <el-form-item label="正向提示词" required>
            <el-input v-model="form.prompt" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item label="反向提示词">
            <el-input v-model="form.negativePrompt" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="模型">
            <el-select v-model="form.model" :loading="loadingOptions" placeholder="选择 checkpoint" style="width: 100%">
              <el-option v-for="m in models" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>

          <el-row :gutter="12">
            <el-col :xs="24" :sm="12">
              <el-form-item label="Seed">
                <div class="seed-row">
                  <el-input-number
                    v-model="form.seed"
                    :min="0"
                    :max="1125899906842624"
                    :step="1"
                    :precision="0"
                    controls-position="right"
                    style="flex: 1"
                  />
                  <el-button :icon="Refresh" title="随机 seed" @click="randomSeed" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :xs="12" :sm="6">
              <el-form-item label="步数">
                <el-input-number v-model="form.steps" :min="1" :max="150" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :xs="12" :sm="6">
              <el-form-item label="CFG">
                <el-input-number
                  v-model="form.cfg"
                  :min="1"
                  :max="30"
                  :step="0.5"
                  :precision="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="12">
            <el-col :xs="24" :sm="12">
              <el-form-item label="尺寸">
                <div class="size-row">
                  <el-input-number v-model="form.width" :min="64" :max="2048" :step="64" controls-position="right" style="flex: 1" />
                  <span class="x">x</span>
                  <el-input-number v-model="form.height" :min="64" :max="2048" :step="64" controls-position="right" style="flex: 1" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :xs="12" :sm="6">
              <el-form-item label="采样器">
                <el-select v-model="form.sampler" style="width: 100%">
                  <el-option v-for="s in samplers" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="12" :sm="6">
              <el-form-item label="调度器">
                <el-select v-model="form.scheduler" style="width: 100%">
                  <el-option v-for="s in schedulers" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <div class="actions">
            <el-button
              type="primary"
              :icon="MagicStick"
              :loading="busy"
              :disabled="!online || !form.prompt || !form.model"
              @click="handleGenerate"
            >
              {{ busy ? '执行中' : '生成并存证' }}
            </el-button>
          </div>
          <p class="hint">表单只决定提交内容；存证参数全部取自 ComfyUI 的任务执行记录。</p>
        </el-form>
      </section>

      <section class="panel result-panel">
        <div class="panel-title">
          <span>生成结果</span>
          <el-tag v-if="result" size="small" type="success" effect="plain">已存证</el-tag>
          <el-tag v-else-if="busy" size="small" type="warning" effect="plain">执行中</el-tag>
        </div>

        <div v-if="busy" class="running">
          <el-progress :percentage="100" :indeterminate="true" :duration="1.5" :show-text="false" />
          <p class="running-line">
            已等待 {{ elapsed }} 秒<span v-if="promptId"> · 任务 {{ promptId.slice(0, 8) }}</span>
          </p>
          <p class="running-note">{{ deviceNote }}</p>
        </div>

        <template v-else-if="result">
          <img :src="result.imageUrl" class="preview" alt="生成的图片" />
          <el-descriptions :column="1" size="small" border class="evidence">
            <el-descriptions-item label="作品 ID">{{ result.workId }}</el-descriptions-item>
            <el-descriptions-item label="文件 SHA-256">
              <span class="mono">{{ result.fileHash }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="证据哈希">
              <span class="mono">{{ result.dataHash }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="链状态">
              <el-tag size="small" :type="chainTagType(result.chainStatus)" effect="plain">
                {{ chainText(result.chainStatus) }}
              </el-tag>
              <span v-if="result.chainStatus !== 1" class="chain-note">链上凭证等待 FISCO 合约接入</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="result-actions">
            <el-button type="primary" plain @click="openWork">查看作品</el-button>
            <el-button plain @click="openVerify">公开验证</el-button>
            <el-button plain :icon="Download" @click="downloadImage">下载图片</el-button>
          </div>

          <el-collapse class="params">
            <el-collapse-item title="本次采集的真实参数" name="params">
              <div class="param-list">
                <div v-for="row in collectedRows" :key="row.label" class="param-row">
                  <span class="param-key">{{ row.label }}</span>
                  <span class="param-value" :class="{ mono: row.mono }">{{ row.value }}</span>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>

        <el-empty v-else description="还没有生成记录" :image-size="72" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Refresh, Download } from '@element-plus/icons-vue'
import { workApi } from '../api'
import { sha256Hex, sha256File } from '../utils/hash'

const router = useRouter()

const online = ref(false)
const checked = ref(false)
const deviceLabel = ref('')
const deviceType = ref('')
const loadingOptions = ref(false)
const models = ref([])
const samplers = ref(['euler'])
const schedulers = ref(['normal'])

const busy = ref(false)
const elapsed = ref(0)
const promptId = ref('')
const result = ref(null)
let ticker = null
let objectUrl = ''

const form = reactive({
  title: '',
  prompt: 'a quiet lakeside town at dawn, soft mist, cinematic light, highly detailed',
  negativePrompt: 'lowres, blurry, watermark, text, deformed',
  model: '',
  seed: Math.floor(Math.random() * 1000000),
  steps: 20,
  cfg: 7,
  width: 512,
  height: 512,
  sampler: 'euler',
  scheduler: 'normal'
})

const deviceNote = computed(() => {
  if (deviceType.value === 'cpu') {
    return '当前是 CPU 模式，512 x 512 / 20 步大约 2-3 分钟，请保持页面打开。'
  }
  return 'GPU 模式，通常几秒内完成，请保持页面打开。'
})

const collectedRows = computed(() => {
  const m = result.value && result.value.metadata
  if (!m) return []
  return [
    { label: '模型', value: m.modelName || '-' },
    { label: 'Seed', value: m.seed },
    { label: '步数', value: m.steps },
    { label: 'CFG', value: m.cfgScale },
    { label: '尺寸', value: m.width + ' x ' + m.height },
    { label: '采样器', value: m.sampler || '-' },
    { label: '调度器', value: m.scheduler || '-' },
    { label: '正向提示词', value: m.prompt || '-' },
    { label: '反向提示词', value: m.negativePrompt || '-' },
    { label: '工作流哈希', value: m.workflowHash, mono: true },
    { label: '任务 ID', value: m.requestId, mono: true },
    { label: '生成时间', value: m.generatedAt }
  ]
})

const chainText = (status) => {
  if (status === 1) return '已上链'
  if (status === 2) return '上链失败'
  return '待上链'
}

const chainTagType = (status) => {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))

const getJson = async (url, options) => {
  const res = await fetch(url, options)
  const text = await res.text()
  if (!res.ok) {
    throw new Error('HTTP ' + res.status + ' ' + text.slice(0, 200))
  }
  return text ? JSON.parse(text) : {}
}

const randomSeed = () => {
  form.seed = Math.floor(Math.random() * 1000000)
}

const checkEngine = async () => {
  try {
    const stats = await getJson('/comfy/system_stats')
    online.value = true
    const device = (stats.devices || [])[0]
    if (device) {
      deviceType.value = device.type || ''
      const vram = device.vram_total ? ' · ' + (device.vram_total / 1024 / 1024 / 1024).toFixed(1) + ' GB' : ''
      deviceLabel.value = (device.name || device.type || 'device') + vram
    }
  } catch (e) {
    online.value = false
    deviceLabel.value = ''
  } finally {
    checked.value = true
  }
}

const loadOptions = async () => {
  loadingOptions.value = true
  try {
    const ckpt = await getJson('/comfy/object_info/CheckpointLoaderSimple')
    const names = (ckpt.CheckpointLoaderSimple || {}).input
    models.value = (((names || {}).required || {}).ckpt_name || [[]])[0] || []
    if (!form.model && models.value.length) {
      form.model = models.value[0]
    }
    const ks = await getJson('/comfy/object_info/KSampler')
    const ksInput = ((ks.KSampler || {}).input || {}).required || {}
    const samplerList = (ksInput.sampler_name || [[]])[0] || []
    const schedulerList = (ksInput.scheduler || [[]])[0] || []
    if (samplerList.length) samplers.value = samplerList
    if (schedulerList.length) schedulers.value = schedulerList
    if (!samplers.value.includes(form.sampler)) form.sampler = samplers.value[0]
    if (!schedulers.value.includes(form.scheduler)) form.scheduler = schedulers.value[0]
  } catch (e) {
    ElMessage.warning('读取 ComfyUI 模型列表失败，请确认 ComfyUI 已启动')
  } finally {
    loadingOptions.value = false
  }
}

const buildWorkflow = () => ({
  '4': { class_type: 'CheckpointLoaderSimple', inputs: { ckpt_name: form.model } },
  '5': { class_type: 'EmptyLatentImage', inputs: { width: form.width, height: form.height, batch_size: 1 } },
  '6': { class_type: 'CLIPTextEncode', inputs: { text: form.prompt, clip: ['4', 1] } },
  '7': { class_type: 'CLIPTextEncode', inputs: { text: form.negativePrompt, clip: ['4', 1] } },
  '3': {
    class_type: 'KSampler',
    inputs: {
      seed: Math.trunc(form.seed),
      steps: form.steps,
      cfg: form.cfg,
      sampler_name: form.sampler,
      scheduler: form.scheduler,
      denoise: 1.0,
      model: ['4', 0],
      positive: ['6', 0],
      negative: ['7', 0],
      latent_image: ['5', 0]
    }
  },
  '8': { class_type: 'VAEDecode', inputs: { samples: ['3', 0], vae: ['4', 2] } },
  '9': { class_type: 'SaveImage', inputs: { filename_prefix: 'aic', images: ['8', 0] } }
})

const submitPrompt = async (workflow) => {
  const res = await fetch('/comfy/prompt', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: workflow, client_id: crypto.randomUUID() })
  })
  let body = {}
  try { body = await res.json() } catch (e) {}
  if (!res.ok || body.error) {
    const message = body.error ? body.error.message || JSON.stringify(body.error) : 'HTTP ' + res.status
    const nodeError = body.node_errors && Object.keys(body.node_errors).length
      ? '；节点错误：' + JSON.stringify(body.node_errors).slice(0, 300)
      : ''
    throw new Error('ComfyUI 拒绝任务：' + message + nodeError)
  }
  if (!body.prompt_id) {
    throw new Error('ComfyUI 未返回 prompt_id')
  }
  return body.prompt_id
}

const describeError = (status) => {
  const messages = (status && status.messages) || []
  for (const item of messages) {
    if (Array.isArray(item) && item[0] === 'execution_error') {
      const data = item[1] || {}
      return data.exception_message || data.exception_type || '执行出错'
    }
  }
  return '执行出错'
}

const waitForHistory = async (id) => {
  const start = Date.now()
  elapsed.value = 0
  ticker = setInterval(() => {
    elapsed.value = Math.round((Date.now() - start) / 1000)
  }, 1000)
  try {
    while (Date.now() - start < 15 * 60 * 1000) {
      const history = await getJson('/comfy/history/' + id)
      const entry = history[id]
      if (entry) {
        const status = entry.status || {}
        if (status.status_str === 'error') {
          throw new Error('ComfyUI ' + describeError(status))
        }
        if (status.completed) return entry
      }
      await sleep(1500)
    }
    throw new Error('等待 ComfyUI 执行超时')
  } finally {
    clearInterval(ticker)
    ticker = null
  }
}

const firstNode = (nodes, classType) => Object.values(nodes).find(node => node && node.class_type === classType)
const nodeText = (node) => String(((node || {}).inputs || {}).text || '')
const linkedId = (node, input) => {
  const value = ((node || {}).inputs || {})[input]
  return Array.isArray(value) && value.length ? String(value[0]) : ''
}

const canonicalJson = (value) => {
  if (value === null || typeof value !== 'object') return JSON.stringify(value)
  if (Array.isArray(value)) return '[' + value.map(canonicalJson).join(', ') + ']'
  return '{' + Object.keys(value).sort()
    .map(key => JSON.stringify(key) + ': ' + canonicalJson(value[key]))
    .join(', ') + '}'
}

const localIso = () => {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
    'T' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
}

const historyPrompt = (entry, submitted) => {
  if (Array.isArray(entry.prompt) && entry.prompt.length > 2 && typeof entry.prompt[2] === 'object') {
    return entry.prompt[2]
  }
  if (entry.prompt && typeof entry.prompt === 'object' && !Array.isArray(entry.prompt)) {
    return entry.prompt
  }
  return submitted
}

const buildMetadata = async (actual, id) => {
  const ks = firstNode(actual, 'KSampler')
  const checkpoint = firstNode(actual, 'CheckpointLoaderSimple')
  const latent = firstNode(actual, 'EmptyLatentImage')
  const positive = actual[linkedId(ks, 'positive')]
  const negative = actual[linkedId(ks, 'negative')]
  const ksInputs = (ks || {}).inputs || {}
  const latentInputs = (latent || {}).inputs || {}
  const modelFile = String(((checkpoint || {}).inputs || {}).ckpt_name || '')

  return {
    title: form.title.trim() || 'ComfyUI ' + id.slice(0, 8),
    prompt: nodeText(positive),
    negativePrompt: nodeText(negative),
    modelName: modelFile.replace(/\.[^.]+$/, ''),
    modelVersion: '',
    seed: String(ksInputs.seed === undefined ? 0 : ksInputs.seed),
    steps: Number(ksInputs.steps || 0),
    cfgScale: Number(ksInputs.cfg || 0),
    width: Number(latentInputs.width || 0),
    height: Number(latentInputs.height || 0),
    sampler: String(ksInputs.sampler_name || ''),
    scheduler: String(ksInputs.scheduler || ''),
    workflowHash: await sha256Hex(canonicalJson(actual)),
    requestId: id,
    workflowRaw: JSON.stringify(actual),
    generatedAt: localIso()
  }
}

const findImage = (outputs) => {
  for (const nodeOutput of Object.values(outputs || {})) {
    if (nodeOutput && nodeOutput.images && nodeOutput.images.length) {
      return nodeOutput.images[0]
    }
  }
  return null
}

const releasePreview = () => {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

const handleGenerate = async () => {
  if (!form.prompt.trim()) {
    ElMessage.warning('请填写正向提示词')
    return
  }
  if (!form.model) {
    ElMessage.warning('请选择模型')
    return
  }

  busy.value = true
  result.value = null
  releasePreview()
  promptId.value = ''

  try {
    const workflow = buildWorkflow()
    promptId.value = await submitPrompt(workflow)
    const entry = await waitForHistory(promptId.value)
    const image = findImage(entry.outputs)
    if (!image) throw new Error('ComfyUI 执行完成但没有输出图片')

    const metadata = await buildMetadata(historyPrompt(entry, workflow), promptId.value)
    const imageRes = await fetch('/comfy/view?' + new URLSearchParams({
      filename: image.filename,
      subfolder: image.subfolder || '',
      type: image.type || 'output'
    }).toString())
    if (!imageRes.ok) throw new Error('读取生成图片失败：HTTP ' + imageRes.status)
    const blob = await imageRes.blob()
    const file = new File([blob], image.filename, { type: blob.type || 'image/png' })

    objectUrl = URL.createObjectURL(blob)
    const formData = new FormData()
    formData.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }), 'metadata.json')
    formData.append('file', file)

    const res = await workApi.importComfyui(formData)
    const data = res.data || {}
    const work = data.work || {}
    const evidence = data.evidence || {}
    result.value = {
      workId: work.id,
      title: work.title,
      fileHash: work.fileHash || await sha256File(file),
      dataHash: evidence.dataHash,
      chainStatus: evidence.chainStatus,
      imageUrl: objectUrl,
      metadata
    }
    ElMessage.success('已生成并存证')
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
    releasePreview()
  } finally {
    busy.value = false
  }
}

const openWork = () => {
  if (result.value) router.push('/work/' + result.value.workId)
}

const openVerify = () => {
  if (!result.value || !result.value.dataHash) return
  const target = router.resolve('/verify/' + result.value.dataHash)
  window.open(target.href, '_blank')
}

const downloadImage = () => {
  if (!result.value) return
  const link = document.createElement('a')
  link.href = result.value.imageUrl
  link.download = 'work-' + result.value.workId + '.png'
  link.click()
}

onMounted(async () => {
  await checkEngine()
  if (online.value) await loadOptions()
})

onBeforeUnmount(() => {
  if (ticker) clearInterval(ticker)
  releasePreview()
})
</script>

<style scoped>
.generate-page {
  max-width: 1240px;
  margin: 0 auto;
  padding: 24px 30px 48px;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.head-text h2 {
  font-size: 20px;
  color: #303133;
  margin-bottom: 6px;
}
.head-text .sub {
  font-size: 13px;
  color: #909399;
  max-width: 640px;
  line-height: 1.5;
}
.engine {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
  padding: 6px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  max-width: 100%;
}
.engine .device {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  flex: none;
}
.dot.ok {
  background: #67c23a;
}
.offline-alert {
  margin-bottom: 16px;
}
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 16px;
  align-items: start;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 18px 20px;
  min-width: 0;
}
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 14px;
}
.seed-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.size-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.size-row .x {
  color: #909399;
  flex: none;
}
.actions {
  margin-top: 4px;
}
.hint {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.running {
  padding: 18px 4px 8px;
}
.running-line {
  margin-top: 14px;
  font-size: 13px;
  color: #606266;
}
.running-note {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.preview {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: contain;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.evidence {
  margin-top: 14px;
}
.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  word-break: break-all;
}
.chain-note {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
.result-actions .el-button {
  margin-left: 0;
}
.params {
  margin-top: 8px;
  border-top: none;
}
.param-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 13px;
}
.param-row:last-child {
  border-bottom: none;
}
.param-key {
  color: #909399;
}
.param-value {
  color: #303133;
  word-break: break-word;
}
@media (max-width: 1080px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .engine .device {
    max-width: 160px;
  }
}
@media (max-width: 768px) {
  .generate-page {
    padding: 16px 14px 40px;
  }
}
</style>
