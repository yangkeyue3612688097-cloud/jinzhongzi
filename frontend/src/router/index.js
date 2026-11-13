import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: '工作台', requiresAuth: true }
  },
  {
    path: '/upload',
    name: 'Upload',
    component: () => import('../views/WorkUpload.vue'),
    meta: { title: '上传作品', requiresAuth: true }
  },
  {
    path: '/generate',
    name: 'LocalGenerate',
    component: () => import('../views/LocalGenerate.vue'),
    meta: { title: 'Local Generate', requiresAuth: true }
  },
  {
    path: '/work/:id',
    name: 'WorkDetail',
    component: () => import('../views/WorkDetail.vue'),
    meta: { title: '作品详情', requiresAuth: true }
  },
  {
    path: '/work/:id/versions',
    name: 'VersionHistory',
    component: () => import('../views/VersionHistory.vue'),
    meta: { title: '版本历史', requiresAuth: true }
  },
  {
    path: '/collaboration',
    name: 'Collaboration',
    component: () => import('../views/Collaboration.vue'),
    meta: { title: '协作管理', requiresAuth: true }
  },
  {
    path: '/invites',
    name: 'Invites',
    component: () => import('../views/Invites.vue'),
    meta: { title: '我的邀请', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/verify/:hash',
    name: 'Verify',
    component: () => import('../views/Verify.vue'),
    meta: { title: '存证验证' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
