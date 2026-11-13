# 录屏操作手册

这份手册按"先准备、再开机、最后分段录"的顺序写。命令可以直接复制，路径都是本机真实路径。

---

## 0. 录屏前先过一遍的四个事实

| 项目 | 当前状态 |
| --- | --- |
| 后端 | `http://127.0.0.1:8080`，链模式 `pending`（本地存证，链上接口预留） |
| 前端 | `http://127.0.0.1:5173` |
| ComfyUI | `http://127.0.0.1:8188`，`--cpu` 模式，实测 512×512 / 20 步 = **150 秒** |
| 显卡 | RTX 4060 8G，驱动 537.53 / CUDA 12.2，**跑不了 cu130 的 PyTorch** |

结论：不升驱动也能完整闭环，用 CPU 生成即可，单张约 2.5 分钟。升驱动只是让单张变成 2~4 秒，属于加速项，不是必需项。

---

## 1. 两条路线，选一条

**路线 A（推荐，今天就能录）：不升驱动，CPU 生成**

什么都不用装。缺点只有速度：512×512 / 20 步约 150 秒一张。录屏时让它跑完，后期把这一段剪成 6~8 倍速，或者边跑边讲架构，观感完全够用。参数、哈希、证书、验证页和 GPU 模式一模一样。

**路线 B（可选，以后再做）：升级驱动到 580+，GPU 生成**

1. 打开 [NVIDIA 驱动下载页](https://www.nvidia.cn/drivers/)，产品或直接搜索 `RTX 4060`（笔记本选 Notebook）。
2. 下载 **Game Ready 或 Studio 580 以上**版本，安装时选"仅安装驱动"或默认均可。
3. 安装完**重启电脑**。
4. 开 PowerShell 验证：

```powershell
nvidia-smi
```

左上角 `Driver Version` 显示 `580.xx` 或更高才算成功。`CUDA Version` 显示 13.x 更稳。

> 还有一条中间路线：把 ComfyUI 自带 Python 的 PyTorch 换成 CUDA 12 版本，兼容 537 驱动，但要重下 2~3 GB 依赖，还可能和当前 ComfyUI / Python 3.13 版本冲突。**录屏前不要动它**，跑通 CPU 版本最稳。

---

## 2. 每次录屏前的启动顺序

严格按这个顺序，前一个起来了再开下一个。

**① ComfyUI（CPU，路线 A）**

```powershell
cd D:\AI\ComfyUI_windows_portable_nvidia\ComfyUI_windows_portable
.\run_cpu.bat
```

看到 `Starting server` 和 `To see the GUI go to: http://127.0.0.1:8188` 就绪。
验证（另开一个窗口）：

```powershell
curl.exe -s http://127.0.0.1:8188/system_stats
```

返回里 `devices[0].name` 是 `cpu` 就对了（走路线 A）。如果哪天升了驱动，把启动命令换成 `.\run_nvidia_gpu.bat`，这里的 `devices[0].name` 应变成 `cuda:0 NVIDIA GeForce RTX 4060 ...`。

**② 后端（WSL）**

```powershell
wsl.exe -d Ubuntu -u ubuntu -- bash /mnt/d/ai_block_project/scripts/start_backend.sh
```

验证：

```powershell
curl.exe -s http://127.0.0.1:8080/api/public/chain-status
```

应返回 `"mode":"pending"`。

**③ 前端**

```powershell
cd D:\ai_block_project\frontend
npm run dev
```

浏览器打开 `http://127.0.0.1:5173`。

---

## 3. 登录账号

| 用途 | 账号 | 密码 |
| --- | --- | --- |
| 主账号（作品所有者） | `comfy_demo_1030` | 使用你本地设置的演示密码 |
| 协作者账号（第二个浏览器/无痕窗口） | `mate_091023` | 使用你本地设置的演示密码 |

录屏建议：Chrome 正常窗口登主账号，无痕窗口登协作者账号，两个窗口并排。

---

## 4. 终端里拿 token（录屏时只用这一行）

```powershell
$env:AIC_API_TOKEN = (Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/user/login -ContentType 'application/json' -Body (@{ username = 'comfy_demo_1030'; password = $env:DEMO_ACCOUNT_PASSWORD } | ConvertTo-Json)).data.token
```

不报错就说明拿到了。

---

## 5. 采集命令（这是"自动采集"那一段的主角）

```powershell
cd D:\ai_block_project

& 'D:\AI\ComfyUI_windows_portable_nvidia\ComfyUI_windows_portable\python_embeded\python.exe' `
  .\scripts\comfyui_collector.py `
  --prompt "a red fox in a snowy forest, cinematic light, detailed fur" `
  --negative-prompt "blurry, low quality, watermark" `
  --model v1-5-pruned-emaonly-fp16.safetensors `
  --width 512 --height 512 --steps 20 --cfg 7 `
  --sampler euler --scheduler normal `
  --seed 42 `
  --title "SD1.5 512x512 provenance demo"
```

成功时终端会打印 `workId`、`dataHash`、`signatureVerified`。**把 `dataHash` 记下来**，后面验证页要用。

想换一张就改 `--seed`，比如 20261030。

### 录屏节奏建议

采集器是确定性的：seed、prompt、步数不变，出图就不变。所以**重录时保持 seed 不变**，剪出来前后画面一致。

但注意 ComfyUI 有节点缓存：同样的参数第二次提交会**秒回**，不是真的重新算。录"真实生成"那一段时，seed 换一个新数字（比如 `--seed 20261031`），进度条才会真的走。

CPU 下这一段约 150 秒。三种处理方式，按你的成片时长挑：

1. **实拍后加速**（推荐）：原速录完，剪辑里 6~8 倍速，成片里约 20 秒，字幕标"CPU 生成，加速播放"。
2. **边跑边讲**：让它在后台跑，你先讲产品和架构，回来正好出结果，不用加速。
3. **分两段拍**：一段只拍命令行提交和 ComfyUI 进度条，一段拍出图后的哈希和验证页，中间剪掉等待。

建议成片 6~8 分钟，分段拍、分段存，不要一口气录完。

**现成兜底数据**（现场生成失败时可以直接讲这条）：

- 作品：`SD1.5 512x512 provenance demo`，workId `11`
- 存证哈希：`5a9fcdd0997f16aaf773f8165b5abb753c1860cee98bfc74854a350c038d9609`
- 验证页：`http://127.0.0.1:5173/verify/5a9fcdd0997f16aaf773f8165b5abb753c1860cee98bfc74854a350c038d9609`

---

## 6. 分镜脚本（建议按这 8 段录）

每段 40~90 秒，录完单独存文件，最后剪在一起，出错只重录一段。

**第 1 段：一句话定位（30 秒）**
画面：浏览器停在作品列表。
讲："这是一个 AI 作品的可信生成与协作分账平台。生成在本地 ComfyUI 完成，系统自动采集完整生成参数，和作品文件绑定后做哈希存证，再通过公开验证页和 PDF 证书对外证明来源。"

**第 2 段：真实生成 + 自动采集（核心，2 分钟）**
画面：左边 ComfyUI 界面（`http://127.0.0.1:8188`），右边 PowerShell 跑上面那条采集命令。
动作：命令回车 → 切到 ComfyUI 看队列在跑 → 出图。
讲："注意这里不是我手填参数。采集器把 prompt、negative prompt、模型、seed、steps、CFG、尺寸、采样器直接提交给 ComfyUI，再从执行历史里把真实参数和 workflow 哈希读回来，和输出文件一起上传。"

**第 3 段：存证详情（1 分钟）**
画面：`http://127.0.0.1:5173/work/{workId}`。
动作：指文件哈希、存证哈希、签名状态、生成参数表。
讲："文件哈希证明文件没被换过；存证哈希是把创作信息按固定规则序列化后算的；这里签名已验证，说明是创作者私钥签的。"

**第 4 段：公开验证页（1 分钟）**
画面：`http://127.0.0.1:5173/verify/{dataHash}`，未登录也能打开。
动作：点"重新计算哈希" → 显示一致。
讲："任何人都能拿这个链接复核，不需要登录，也不依赖我们的后台说法。"

**第 5 段：来源清单 + PDF 证书（1 分钟）**
画面：同一页面点"下载来源清单"、"下载存证证书"。
动作：打开 JSON 展示字段；打开 PDF 展示二维码。
讲："清单是机器可读的完整来源证明，证书是给人看的，二维码指回这个验证页。"

**第 6 段：版本演进（1 分钟）**
画面：作品详情 → "查看全部" 进 `/work/{workId}/versions`。
动作：上传一张新图作为新版本 → 列表出现新版本 → 设为当前版本。
讲："每次改稿都是独立哈希、独立版本，旧版本留着。作品被替换能直接看出来。"

**第 7 段：协作 → 订单 → 分账（3 分钟，最容易被问）**
顺序不能乱：

1. 主账号：作品详情 → "邀请协作者" → 搜索 `mate` → 选 `mate_091023` → 发送邀请。
2. 无痕窗口：登 `mate_091023` → 进 `/invites` → 接受。此时协作自动建立。
3. 主账号：进 `/collaboration` → 选中这条协作 → 起草协议（比例合计 100%）→ 提交。
4. 无痕窗口：`/collaboration` → 选中协作 → 确认协议。
5. 主账号：协议状态变"已生效"后 → 填买方、金额 → "创建订单"。
6. 主账号：点"确认收款" → 生成收入事件。
7. 主账号：点"按协议分配收益" → 成功。
8. 再点一次分账 → 被拒绝。

讲："分账不是手填数字，只能由订单确认收款后产生的收入事件触发，同一订单重复分账会被业务层直接拦住。"

**第 8 段：审计日志 + 链状态（1 分钟）**
画面：`/profile` 操作日志；再切到终端 `curl.exe -s http://127.0.0.1:8080/api/public/chain-status`。
讲："存证、签协议、分账、改比例都落库留痕。链上目前是 pending 模式，可信时间戳和结算凭证由合作方 FISCO 合约承接，后端适配层已经留好，切过去业务层不动。"

---

## 7. 常见问题

| 现象 | 处理 |
| --- | --- |
| 采集器报连不上 8188 | ComfyUI 没启动完，等 `To see the GUI` 出现再跑 |
| 采集器报 401 | token 过期（默认 24 小时），重跑第 4 节那一行 |
| `cudaGetDeviceCount() returned cudaErrorNotSupported` | 驱动太旧，回第 1 节升级 |
| 生成要两三分钟 | 正常，CPU 模式就是 512×512 / 20 步约 150 秒，不影响存证结论 |
| `ASSETS_STARTUP_FAILED: in_use` | 已经有另一个 ComfyUI 占着 `comfyui.db`，先关掉旧实例再启动，同一目录只能开一个 |
| 前端空白/接口 404 | 后端没起，或前端不是用 `npm run dev` 起的 |
| 分账按钮灰色 | 协议还没全部确认，或订单还没确认收款 |

---

## 8. 录完收尾

```powershell
wsl.exe -d Ubuntu -u ubuntu -- bash /mnt/d/ai_block_project/scripts/stop_backend.sh
```

ComfyUI 和前端窗口直接关掉即可。产物在：

- 生成图：`D:\AI\ComfyUI_windows_portable_nvidia\ComfyUI_windows_portable\ComfyUI\output\`
- 证书：浏览器下载目录，或 `D:\ai_block_project\outputs\`

---

## 9. 三条不能说的话

1. 不说"已经上链"，说"本地存证完成，链上适配层待合作方合约接入"。
2. 不说"支持 Flux 全精度 / 视频生成 / 模型微调"。
3. 不说云端免费额度是系统前提，它只是没显卡时的答辩兜底。
