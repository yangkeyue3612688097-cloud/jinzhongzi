<template>
  <el-menu
    :default-active="activeMenu"
    mode="horizontal"
    background-color="#545c64"
    text-color="#fff"
    active-text-color="#ffd04b"
    router
    :ellipsis="false"
    style="position: sticky; top: 0; z-index: 100"
  >
    <el-menu-item index="/home" title="作品列表">
      <el-icon><HomeFilled /></el-icon>
      <span class="nav-label">作品列表</span>
    </el-menu-item>
    <el-menu-item index="/upload" title="上传作品">
      <el-icon><Upload /></el-icon>
      <span class="nav-label">上传作品</span>
    </el-menu-item>
    <el-menu-item index="/generate" title="本地生成">
      <el-icon><MagicStick /></el-icon>
      <span class="nav-label">本地生成</span>
    </el-menu-item>
    <el-menu-item index="/collaboration" title="协作管理">
      <el-icon><UserFilled /></el-icon>
      <span class="nav-label">协作管理</span>
    </el-menu-item>
    <el-menu-item index="/invites" title="我的邀请">
      <el-icon><Bell /></el-icon>
      <span class="nav-label">我的邀请</span>
      <el-badge v-if="inviteCount > 0" :value="inviteCount" class="badge" />
    </el-menu-item>
    <el-menu-item index="/profile" title="个人中心">
      <el-icon><Setting /></el-icon>
      <span class="nav-label">个人中心</span>
    </el-menu-item>
    <div class="menu-spacer"></div>
    <el-menu-item disabled class="welcome-item">
      <span>欢迎，{{ username }}</span>
    </el-menu-item>
    <el-menu-item @click="handleLogout" title="退出登录">
      <el-icon><SwitchButton /></el-icon>
      <span class="nav-label">退出登录</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  HomeFilled, Upload, UserFilled, Setting, SwitchButton, Bell, MagicStick
} from '@element-plus/icons-vue'
import { inviteApi } from '../api'

const router = useRouter()
const route = useRoute()

const username = ref(localStorage.getItem('username') || '用户')
const activeMenu = computed(() => route.path)
const inviteCount = ref(0)

const loadInviteCount = async () => {
  try {
    const res = await inviteApi.myInvites()
    inviteCount.value = (res.data || []).filter(i => i.status === 0).length
  } catch (e) {}
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    router.push('/login')
  }).catch(() => {})
}

onMounted(loadInviteCount)
</script>

<style scoped>
.el-menu {
  min-width: 1200px;
}
.badge {
  margin-left: 5px;
}
.menu-spacer {
  flex: 1;
}
@media (max-width: 768px) {
  .el-menu {
    min-width: 0;
  }
  :deep(.el-menu-item) {
    padding: 0 8px !important;
  }
  .menu-spacer {
    display: none;
  }
  .nav-label,
  .welcome-item {
    display: none;
  }
}
</style>
