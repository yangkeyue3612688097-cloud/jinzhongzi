import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

api.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

// 公开验证接口：不带 token，不触发登录跳转
export const publicApi = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const userApi = {
  register: (data) => api.post('/user/register', data),
  login: (data) => api.post('/user/login', data),
  getInfo: () => api.get('/user/info'),
  search: (keyword) => api.get('/user/search', { params: { keyword } }),
  updatePassword: (data) => api.post('/user/update-password', data),
  updateWallet: (data) => api.post('/user/update-wallet', data),
  attestIdentity: () => api.post('/user/attest-identity'),
  getOperationLogs: () => api.get('/user/operation-logs')
}

export const workApi = {
  create: (formData) => api.post('/work/create', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  importComfyui: (formData) => api.post('/work/import/comfyui', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  }),
  list: (params) => api.get('/work/list', { params }),
  detail: (id) => api.get('/work/detail/' + id),
  generation: (workId) => api.get('/work/generation/' + workId),
  evidence: (id) => api.post('/work/evidence/' + id),
  retryEvidenceAnchor: (workId) => api.post('/work/evidence/' + workId + '/anchor/retry'),
  evidenceList: (workId) => api.get('/work/evidence/list/' + workId),
  verifyEvidence: (dataHash) => api.get('/work/evidence/verify', { params: { dataHash } })
}

export const publicChainApi = {
  status: () => publicApi.get('/public/chain-status'),
  verify: (params) => publicApi.get('/public/chain/verify', { params })
}

export const versionApi = {
  create: (workId, formData, changeDescription) => {
    formData.append('changeDescription', changeDescription || '')
    return api.post('/work/version/create', formData, {
      params: { workId },
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  history: (workId) => api.get('/work/version/history/' + workId),
  detail: (versionId) => api.get('/work/version/detail/' + versionId),
  confirm: (versionId) => api.post('/work/version/confirm/' + versionId)
}

export const inviteApi = {
  send: (workId, inviteeId, message) => api.post('/collaboration/invite/send', null, {
    params: { workId, inviteeId, message }
  }),
  accept: (inviteId) => api.post('/collaboration/invite/accept/' + inviteId),
  reject: (inviteId) => api.post('/collaboration/invite/reject/' + inviteId),
  myInvites: () => api.get('/collaboration/invite/my'),
  workInvites: (workId) => api.get('/collaboration/invite/work/' + workId)
}

export const collabApi = {
  create: (workId) => api.post('/collaboration/create', null, { params: { workId } }),
  addCollaborator: (collaborationId, data) => api.post('/collaboration/add-collaborator', data, { params: { collaborationId } }),
  listCollaborators: (collaborationId) => api.get('/collaboration/collaborators/' + collaborationId),
  createOrder: (collaborationId, data) => api.post('/collaboration/order/create', null, { params: { collaborationId, ...data } }),
  confirmOrder: (orderId) => api.post('/collaboration/order/confirm/' + orderId),
  listOrders: (collaborationId) => api.get('/collaboration/orders/' + collaborationId),
  listRevenueEvents: (collaborationId) => api.get('/collaboration/revenue-events/' + collaborationId),
  listSettlementReceipts: (collaborationId) => api.get('/collaboration/receipts/' + collaborationId),
  distribute: (collaborationId) => api.post('/collaboration/distribute', null, { params: { collaborationId } }),
  detail: (id) => api.get('/collaboration/detail/' + id),
  myCollaborations: () => api.get('/collaboration/my'),
  currentAgreement: (collaborationId) => api.get('/collaboration/agreement/current/' + collaborationId),
  effectiveAgreement: (collaborationId) => api.get('/collaboration/agreement/effective/' + collaborationId),
  agreementHistory: (collaborationId) => api.get('/collaboration/agreement/history/' + collaborationId),
  createAgreement: (collaborationId, items, negotiationNote) => api.post(
    '/collaboration/agreement/draft',
    { items, negotiationNote },
    { params: { collaborationId } }
  ),
  confirmAgreement: (agreementId) => api.post('/collaboration/agreement/' + agreementId + '/confirm')
}

export const statisticsApi = {
  dashboard: () => api.get('/statistics/dashboard')
}

export default api
