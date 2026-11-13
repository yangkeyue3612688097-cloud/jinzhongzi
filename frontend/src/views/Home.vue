<template>
  <div class="home-container">
    <div class="page-head">
      <div>
        <h2>工作台</h2>
        <p class="page-sub">{{ today }}</p>
      </div>
      <div class="head-actions">
        <el-button @click="$router.push('/collaboration')">协作项目</el-button>
        <el-button @click="$router.push('/invites')">我的邀请</el-button>
        <el-button type="primary" :icon="Plus" @click="$router.push('/upload')">上传作品</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6" v-for="item in statCards" :key="item.key">
        <div class="stat-tile">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-hint">{{ item.hint }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="table-panel">
      <div class="panel-head">
        <span class="panel-title">我的作品</span>
        <el-button size="small" @click="loadWorks">刷新</el-button>
      </div>
      <el-table :data="works" v-loading="loading" size="default" empty-text="还没有作品">
        <el-table-column prop="title" label="作品" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="$router.push('/work/' + row.id)">
              {{ row.title }}
            </el-link>
            <div class="row-desc">{{ row.description || '暂无描述' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="100">
          <template #default="{ row }">{{ typeText(row.fileType) }}</template>
        </el-table-column>
        <el-table-column label="存证" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small" effect="plain">已存证</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">未存证</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="100" align="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push('/work/' + row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { statisticsApi, workApi } from '../api'

const stats = ref({})
const works = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const statCards = computed(() => [
  { key: 'works', label: '作品', value: stats.value.totalWorks || 0, hint: '已归档的创作' },
  { key: 'collab', label: '协作项目', value: stats.value.totalCollaborations || 0, hint: '参与中的合作' },
  { key: 'revenue', label: '累计收益', value: (stats.value.totalRevenue || 0) + '', hint: '已结算金额' },
  { key: 'evidence', label: '存证', value: stats.value.totalEvidence || 0, hint: '可验证记录' }
])

const typeText = (type) => {
  const map = { image: '图片', text: '文本', audio: '音频', video: '视频', other: '其他' }
  return map[type] || type || '-'
}

const loadStats = async () => {
  try {
    const res = await statisticsApi.dashboard()
    stats.value = res.data || {}
  } catch (e) {}
}

const loadWorks = async () => {
  loading.value = true
  try {
    const res = await workApi.list({ page: page.value, size: size.value })
    const body = res.data || {}
    works.value = body.records || []
    total.value = body.total || 0
  } catch (e) {}
  finally { loading.value = false }
}

const handlePageChange = (value) => {
  page.value = value
  loadWorks()
}

onMounted(() => {
  loadStats()
  loadWorks()
})
</script>

<style scoped>
.home-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 30px 48px;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.page-head h2 {
  font-size: 20px;
  color: #303133;
}
.page-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}
.head-actions {
  display: flex;
  gap: 8px;
}
.stats-row {
  margin-top: 18px;
}
.stat-tile {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 12px;
}
.stat-label {
  font-size: 13px;
  color: #909399;
}
.stat-value {
  margin: 6px 0 2px;
  font-size: 26px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}
.stat-hint {
  font-size: 12px;
  color: #c0c4cc;
}
.table-panel {
  margin-top: 10px;
  padding: 16px 18px 8px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.row-desc {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0;
}
</style>
