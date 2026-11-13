<template>
  <div class="invites-container">
    <h2>我的邀请</h2>

    <el-card>
      <template #header>
        <span>收到的协作邀请</span>
      </template>
      <el-table :data="invites" border>
        <el-table-column prop="id" label="邀请 ID" width="100" />
        <el-table-column prop="workId" label="作品 ID" width="100" />
        <el-table-column prop="inviterId" label="邀请人 ID" width="120" />
        <el-table-column prop="message" label="邀请留言" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="success"
              size="small"
              @click="handleAccept(row.id)"
            >
              接受
            </el-button>
            <el-button
              v-if="row.status === 0"
              type="danger"
              size="small"
              @click="handleReject(row.id)"
            >
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="invites.length === 0" description="暂无邀请" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inviteApi } from '../api'

const invites = ref([])

const getStatusType = (status) => {
  const types = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 0: '待接受', 1: '已接受', 2: '已拒绝', 3: '已取消' }
  return texts[status] || '未知'
}

const loadInvites = async () => {
  try {
    const res = await inviteApi.myInvites()
    invites.value = res.data
  } catch (e) {}
}

const handleAccept = async (inviteId) => {
  try {
    await ElMessageBox.confirm('确定接受此协作邀请吗？', '提示', { type: 'info' })
    await inviteApi.accept(inviteId)
    ElMessage.success('已接受邀请')
    await loadInvites()
  } catch (e) {}
}

const handleReject = async (inviteId) => {
  try {
    await ElMessageBox.confirm('确定拒绝此协作邀请吗？', '提示', { type: 'warning' })
    await inviteApi.reject(inviteId)
    ElMessage.success('已拒绝邀请')
    await loadInvites()
  } catch (e) {}
}

onMounted(loadInvites)
</script>

<style scoped>
.invites-container {
  padding: 30px;
  max-width: 1200px;
  margin: 0 auto;
}
.invites-container h2 {
  margin-bottom: 20px;
  color: #333;
}
</style>
