export async function sha256Hex(input) {
  const data = typeof input === 'string' ? new TextEncoder().encode(input) : input
  const digest = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(digest))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('')
}

export async function sha256File(file) {
  return sha256Hex(await file.arrayBuffer())
}

export function shortHash(hash, head = 10, tail = 8) {
  if (!hash) return ''
  if (hash.length <= head + tail + 3) return hash
  return hash.slice(0, head) + '...' + hash.slice(-tail)
}
