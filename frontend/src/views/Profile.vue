<template>
  <div class="profile-container">
    <h2>个人中心</h2>
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <template #header><span>基本信息</span></template>
          <div class="avatar-section">
            <el-upload class="avatar-uploader" action="/api/user/upload-avatar" :headers="uploadHeaders" :show-file-list="false" :on-success="handleAvatarSuccess" :before-upload="beforeAvatarUpload">
              <img v-if="userInfo.avatar" :src="userInfo.avatar" class="avatar" />
              <div v-else class="avatar-placeholder"><el-icon size="40"><UserFilled /></el-icon><div class="upload-hint">点击上传头像</div></div>
            </el-upload>
          </div>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户 ID">{{ userInfo.id }}</el-descriptions-item>
            <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userInfo.email || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="托管身份地址">
              <span class="identity-address">{{ userInfo.blockchainAddress || '-' }}</span>
              <el-tag v-if="userInfo.walletManaged" type="warning" size="small" style="margin-left: 8px">平台托管</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="身份签名">
              <el-tag v-if="userInfo.identityAttested" type="success" size="small">已签名</el-tag>
              <el-tag v-else type="info" size="small">未签名</el-tag>
              <div v-if="userInfo.identityAttestedAt" class="identity-meta">
                {{ formatTime(userInfo.identityAttestedAt) }}
              </div>
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="身份地址由平台在注册时生成，合约账户接入后替换地址来源"
            style="margin-top: 14px"
          />
          <el-button
            type="primary"
            style="width: 100%; margin-top: 14px"
            :loading="attesting"
            @click="handleAttestIdentity"
          >
            {{ userInfo.identityAttested ? '重新签名身份' : '完成身份签名' }}
          </el-button>
        </el-card>
        <el-card style="margin-top:20px">
          <template #header><span>安全设置</span></template>
          <el-button type="warning" style="width:100%" @click="showPasswordDialog=true">修改密码</el-button>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card>
          <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span>操作日志</span><el-button type="primary" size="small" @click="loadOperationLogs">刷新</el-button></div></template>
          <el-table :data="operationLogs" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="operation" label="操作类型" width="150" />
            <el-table-column prop="detail" label="详情" />
            <el-table-column prop="createTime" label="时间" width="180"><template #default="{row}">{{ formatTime(row.createTime) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="operationLogs.length===0" description="暂无操作记录" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
    <el-dialog v-model="showPasswordDialog" title="修改密码" width="400px">
      <el-form :model="passwordForm" label-width="100px">
        <el-form-item label="原密码"><el-input v-model="passwordForm.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showPasswordDialog=false">取消</el-button><el-button type="primary" @click="handleUpdatePassword">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { userApi } from '../api'

const userInfo = ref({})
const operationLogs = ref([])
const showPasswordDialog = ref(false)
const attesting = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const uploadHeaders = computed(() => ({ 'Authorization': 'Bearer ' + localStorage.getItem('token') }))

const loadUserInfo = async () => { try { const res = await userApi.getInfo(); userInfo.value = res.data } catch (e) {} }
const loadOperationLogs = async () => { try { const res = await userApi.getOperationLogs(); operationLogs.value = res.data || [] } catch (e) {} }

const handleAvatarSuccess = (response) => {
  if (response.code === 200) { ElMessage.success('头像上传成功'); userInfo.value.avatar = response.data }
  else { ElMessage.error(response.message || '上传失败') }
}

const beforeAvatarUpload = (file) => {
  if (!file.type.startsWith('image/')) { ElMessage.error('只能上传图片文件！'); return false }
  if (file.size / 1024 / 1024 > 2) { ElMessage.error('图片大小不能超过 2MB！'); return false }
  return true
}

const handleUpdatePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) { ElMessage.warning('请填写完整'); return }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) { ElMessage.warning('两次密码输入不一致'); return }
  if (passwordForm.newPassword.length < 6) { ElMessage.warning('密码长度不能少于 6 位'); return }
  try { await userApi.updatePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword }); ElMessage.success('密码修改成功'); showPasswordDialog.value = false; passwordForm.oldPassword = ''; passwordForm.newPassword = ''; passwordForm.confirmPassword = '' } catch (e) {}
}

const handleAttestIdentity = async () => {
  attesting.value = true
  try {
    await userApi.attestIdentity()
    ElMessage.success('身份签名已完成')
    await loadUserInfo()
  } catch (e) {}
  finally { attesting.value = false }
}

const formatTime = (time) => { if (!time) return '-'; return new Date(time).toLocaleString('zh-CN') }

onMounted(() => { loadUserInfo(); loadOperationLogs() })
</script>

<style scoped>
.profile-container { padding: 30px; max-width: 1400px; margin: 0 auto; }
.profile-container h2 { margin-bottom: 20px; color: #333; }
.avatar-section { text-align: center; margin-bottom: 20px; }
.avatar-uploader :deep(.el-upload) { border: 2px dashed #d9d9d9; border-radius: 50%; cursor: pointer; overflow: hidden; transition: all 0.3s; width: 120px; height: 120px; margin: 0 auto; }
.avatar-uploader :deep(.el-upload:hover) { border-color: #409eff; }
.avatar { width: 120px; height: 120px; border-radius: 50%; object-fit: cover; }
.avatar-placeholder { width: 120px; height: 120px; border-radius: 50%; background: #f5f7fa; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #999; }
.upload-hint { font-size: 12px; margin-top: 5px; }
.identity-address { font-family: Consolas, Monaco, monospace; font-size: 12px; word-break: break-all; }
.identity-meta { margin-top: 4px; font-size: 12px; color: #909399; }
</style>
