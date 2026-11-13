<template>
  <div class="upload-container">
    <h2>上传作品</h2>
    <el-card>
      <el-form :model="form" label-width="110px" label-position="top">
        <el-form-item label="作品标题" required>
          <el-input v-model="form.title" placeholder="例如：春季新品主视觉" maxlength="200" />
        </el-form-item>
        <el-form-item label="作品描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="文件类型">
          <el-select v-model="form.fileType" style="width: 200px">
            <el-option label="图片" value="image" />
            <el-option label="文本" value="text" />
            <el-option label="音频" value="audio" />
            <el-option label="视频" value="video" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="作品文件" required>
          <el-upload
            drag
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :limit="1"
            class="file-upload"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
          </el-upload>
          <div v-if="fileHash" class="hash-line">
            <span class="hash-label">SHA-256</span>
            <span class="hash-value">{{ fileHash }}</span>
          </div>
        </el-form-item>

        <el-divider content-position="left">生成参数</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="提示词">
              <el-input v-model="form.prompt" type="textarea" :rows="3" placeholder="正向提示词" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="反向提示词">
              <el-input v-model="form.negativePrompt" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模型名称">
              <el-input v-model="form.modelName" placeholder="例如 SDXL" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="模型版本">
              <el-input v-model="form.modelVersion" placeholder="例如 1.0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采样器">
              <el-input v-model="form.sampler" placeholder="例如 DPM++ 2M" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="Seed">
              <el-input v-model="form.seed" placeholder="123456" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="步数">
              <el-input-number v-model="form.steps" :min="1" :max="200" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="CFG">
              <el-input-number v-model="form.cfgScale" :min="0" :max="30" :step="0.5" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="尺寸">
              <div class="size-row">
                <el-input-number v-model="form.width" :min="1" :max="8192" controls-position="right" />
                <span class="size-x">x</span>
                <el-input-number v-model="form.height" :min="1" :max="8192" controls-position="right" />
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="loading">保存作品</el-button>
          <el-button @click="router.push('/home')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { workApi } from '../api'
import { sha256File } from '../utils/hash'

const router = useRouter()
const loading = ref(false)
const file = ref(null)
const fileHash = ref('')

const form = reactive({
  title: '',
  description: '',
  fileType: 'image',
  prompt: '',
  negativePrompt: '',
  modelName: '',
  modelVersion: '',
  seed: '',
  steps: null,
  cfgScale: null,
  width: null,
  height: null,
  sampler: ''
})

const handleFileChange = async (uploadFile) => {
  file.value = uploadFile.raw
  fileHash.value = ''
  try {
    fileHash.value = await sha256File(uploadFile.raw)
  } catch (e) {
    ElMessage.warning('无法在本地计算文件哈希')
  }
}

const handleFileRemove = () => {
  file.value = null
  fileHash.value = ''
}

const appendIfPresent = (formData, key, value) => {
  if (value === null || value === undefined || value === '') return
  formData.append(key, value)
}

const handleSubmit = async () => {
  if (!form.title) {
    ElMessage.warning('请输入作品标题')
    return
  }
  if (!file.value) {
    ElMessage.warning('请选择文件')
    return
  }

  loading.value = true
  try {
    const formData = new FormData()
    formData.append('title', form.title)
    formData.append('fileType', form.fileType)
    formData.append('file', file.value)
    appendIfPresent(formData, 'description', form.description)
    appendIfPresent(formData, 'prompt', form.prompt)
    appendIfPresent(formData, 'negativePrompt', form.negativePrompt)
    appendIfPresent(formData, 'modelName', form.modelName)
    appendIfPresent(formData, 'modelVersion', form.modelVersion)
    appendIfPresent(formData, 'seed', form.seed)
    appendIfPresent(formData, 'steps', form.steps)
    appendIfPresent(formData, 'cfgScale', form.cfgScale)
    appendIfPresent(formData, 'width', form.width)
    appendIfPresent(formData, 'height', form.height)
    appendIfPresent(formData, 'sampler', form.sampler)

    const res = await workApi.create(formData)
    ElMessage.success('作品已保存')
    router.push('/work/' + res.data.id)
  } catch (e) {}
  finally { loading.value = false }
}
</script>

<style scoped>
.upload-container {
  padding: 24px 30px 40px;
  max-width: 960px;
  margin: 0 auto;
}
.upload-container h2 {
  margin-bottom: 16px;
  font-size: 20px;
  color: #303133;
}
.file-upload {
  width: 100%;
}
.hash-line {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}
.hash-label {
  flex: none;
  padding: 1px 6px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  color: #909399;
}
.size-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.size-row :deep(.el-input-number) {
  width: 100%;
}
.size-x {
  color: #909399;
}
</style>
