import { writeFileSync } from 'node:fs'

const CDP = 'http://127.0.0.1:9222'
const APP = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api'
const outDir = 'D:\\ai_block_project'

const loginResponse = await fetch(`${API}/user/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: process.argv[2], password: process.argv[3] })
})
const login = await loginResponse.json()
if (login.code !== 200) throw new Error(`login failed: ${login.message}`)
const { token, userId, username } = login.data

const created = await fetch(`${CDP}/json/new?about:blank`, { method: 'PUT' })
const target = await created.json()
const ws = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  ws.onopen = resolve
  ws.onerror = reject
})

let nextId = 0
const pending = new Map()
const errors = []
ws.onmessage = (event) => {
  const message = JSON.parse(event.data)
  if (message.id && pending.has(message.id)) {
    const { resolve, reject } = pending.get(message.id)
    pending.delete(message.id)
    if (message.error) reject(new Error(message.error.message))
    else resolve(message.result)
    return
  }
  if (message.method === 'Runtime.exceptionThrown') {
    errors.push(message.params.exceptionDetails.text)
  }
  if (message.method === 'Log.entryAdded' && message.params.entry.level === 'error') {
    errors.push(message.params.entry.text)
  }
}

function send(method, params = {}) {
  const id = ++nextId
  ws.send(JSON.stringify({ id, method, params }))
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }))
}

async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text)
  }
  return result.result.value
}

async function waitFor(expression, label, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (await evaluate(expression)) return
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error(`timeout waiting for ${label}`)
}

async function viewport(width, height) {
  await send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: false
  })
}

async function screenshot(name) {
  const shot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  writeFileSync(`${outDir}\\${name}`, Buffer.from(shot.data, 'base64'))
  console.log(`SHOT ${outDir}\\${name}`)
}

await send('Page.enable')
await send('Runtime.enable')
await send('Log.enable')
await viewport(1440, 900)
await send('Page.navigate', { url: `${APP}/login` })
await waitFor(`document.readyState === 'complete'`, 'login page')

await evaluate(`
  localStorage.setItem('token', ${JSON.stringify(token)});
  localStorage.setItem('userId', ${JSON.stringify(String(userId))});
  localStorage.setItem('username', ${JSON.stringify(username)});
  true
`)
await send('Page.navigate', { url: `${APP}/collaboration` })
await waitFor(`document.body.innerText.includes('协作管理')`, 'collaboration page')
await waitFor(`[...document.querySelectorAll('button')].some(b => b.innerText.trim() === '管理')`, 'collaboration rows')

await evaluate(`
  (() => {
    const button = [...document.querySelectorAll('button')].find(b => b.innerText.trim() === '管理')
    if (!button) return false
    button.click()
    return true
  })()
`)
await waitFor(`document.body.innerText.includes('协作协议')`, 'agreement card')
await waitFor(`document.body.innerText.includes('已生效')`, 'effective agreement')
await new Promise(resolve => setTimeout(resolve, 800))

const desktop = await evaluate(`({
  title: document.title,
  innerWidth: window.innerWidth,
  scrollWidth: document.documentElement.scrollWidth,
  hasHash: /[0-9a-f]{64}/.test(document.body.innerText),
  hasPendingChain: document.body.innerText.includes('待上链'),
  hasRatio: document.body.innerText.includes('协议比例')
})`)
console.log('DESKTOP', JSON.stringify(desktop))
await screenshot('collab-desktop.png')

await viewport(390, 844)
await send('Page.reload', { ignoreCache: true })
await waitFor(`document.body.innerText.includes('协作管理')`, 'mobile collaboration page')
await waitFor(`[...document.querySelectorAll('button')].some(b => b.innerText.trim() === '管理')`, 'mobile rows')
await evaluate(`
  (() => {
    const button = [...document.querySelectorAll('button')].find(b => b.innerText.trim() === '管理')
    if (!button) return false
    button.click()
    return true
  })()
`)
await waitFor(`document.body.innerText.includes('协作协议')`, 'mobile agreement card')
await new Promise(resolve => setTimeout(resolve, 800))

const mobile = await evaluate(`({
  innerWidth: window.innerWidth,
  scrollWidth: document.documentElement.scrollWidth,
  bodyWidth: document.body.getBoundingClientRect().width
})`)
console.log('MOBILE', JSON.stringify(mobile))
await screenshot('collab-mobile.png')

if (desktop.scrollWidth > desktop.innerWidth + 1) throw new Error('desktop horizontal overflow')
if (mobile.scrollWidth > mobile.innerWidth + 1) throw new Error('mobile horizontal overflow')
if (!desktop.hasHash) throw new Error('agreement hash not visible')
if (!desktop.hasPendingChain) throw new Error('pending chain status not visible')
if (!desktop.hasRatio) throw new Error('agreement ratio column not visible')
if (errors.length) throw new Error(`browser errors: ${errors.join(' | ')}`)

console.log('UI_VERIFY_OK')
ws.close()
