import json, urllib.request, urllib.parse, time, sys

COMFY = "http://127.0.0.1:8188"
BACKEND = "http://127.0.0.1:8080"

def req(method, url, data=None, headers=None, raw=False):
    if isinstance(data, dict):
        data = json.dumps(data).encode(); headers = {**(headers or {}), "Content-Type":"application/json"}
    r = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    try:
        resp = urllib.request.urlopen(r)
        b = resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8","ignore")
    return resp.status, (b if raw else b.decode("utf-8","ignore"))

print("[0] 登录 demo ...")
st, body = req("POST", f"{BACKEND}/api/user/login", {"username":"demo","password":"123456"})
login = json.loads(body)
if login.get("code") != 200:
    print("    登录失败:", body); sys.exit(1)
TOKEN = login["data"]["token"]
print("    token 获取成功 (长度 %d)" % len(TOKEN))

workflow = {"prompt": {
    "4": {"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"sd_turbo.safetensors"}},
    "5": {"class_type":"EmptyLatentImage","inputs":{"width":512,"height":512,"batch_size":1}},
    "6": {"class_type":"CLIPTextEncode","inputs":{"text":"a cute cat, masterpiece","clip":["4",1]}},
    "7": {"class_type":"CLIPTextEncode","inputs":{"text":"","clip":["4",1]}},
    "3": {"class_type":"KSampler","inputs":{"seed":123456789,"steps":1,"cfg":1.0,"sampler_name":"euler","scheduler":"simple","denoise":1.0,"model":["4",0],"positive":["6",0],"negative":["7",0],"latent_image":["5",0]}},
    "8": {"class_type":"VAEDecode","inputs":{"samples":["3",0],"vae":["4",2]}},
    "9": {"class_type":"SaveImage","inputs":{"images":["8",0],"filename_prefix":"demo_test"}}
}}

print("[1] 提交 ComfyUI 任务 ...")
import uuid
submit = json.loads(req("POST", f"{COMFY}/prompt", {"prompt": workflow["prompt"], "client_id": "e2e-test-" + uuid.uuid4().hex[:8]})[1])
pid = submit["prompt_id"]
print("    prompt_id =", pid)

print("[2] 等待执行完成 ...")
img = None
for _ in range(60):
    try:
        st, body = req("GET", f"{COMFY}/history/{pid}")
        hist = json.loads(body)
    except Exception:
        time.sleep(2); continue
    if pid in hist:
        node = hist[pid]
        if node.get("status",{}).get("status_str") == "error":
            print("    ! ComfyUI 执行错误:", json.dumps(node.get("status",{}), ensure_ascii=False)[:600]); sys.exit(1)
        for out in node["outputs"].values():
            if "images" in out:
                img = out["images"][0]; break
        if img: break
    time.sleep(2)

if not img:
    print("    ! 未拿到输出图片"); sys.exit(1)

print("    输出图片:", img["filename"])
url = f"{COMFY}/view?filename={urllib.parse.quote(img['filename'])}&subfolder={urllib.parse.quote(img.get('subfolder',''))}&type={img.get('type','output')}"
st, data = req("GET", url, raw=True)
open("C:/Users/YYDS/WorkBuddy/2026-10-30-20-40-09/gen_test.png","wb").write(data)
print("    已下载图片字节数:", len(data))

print("[3] 调用后端 import/comfyui 自动上链存证 ...")
boundary = "----b0undary123456"
body_b = b""
meta = {
  "title":"ComfyUI 端到端验证图",
  "prompt":"a cute cat, masterpiece",
  "negativePrompt":"",
  "modelName":"sd_turbo",
  "modelVersion":"1.0",
  "seed":"123456789",
  "steps":1,
  "cfgScale":1.0,
  "width":512,
  "height":512,
  "sampler":"euler",
  "scheduler":"simple",
  "requestId": pid,
  "workflowHash":"",
  "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S")
}
body_b += f"--{boundary}\r\n".encode()
body_b += b'Content-Disposition: form-data; name="metadata"\r\n'
body_b += b"Content-Type: application/json\r\n\r\n"
body_b += json.dumps(meta).encode() + b"\r\n"
body_b += f"--{boundary}\r\n".encode()
body_b += b'Content-Disposition: form-data; name="file"; filename="gen.png"\r\n'
body_b += b"Content-Type: application/octet-stream\r\n\r\n"
body_b += data + b"\r\n"
body_b += f"--{boundary}--\r\n".encode()

st, body = req("POST", f"{BACKEND}/api/work/import/comfyui", data=body_b, headers={
    "Content-Type": f"multipart/form-data; boundary={boundary}",
    "Authorization": f"Bearer {TOKEN}"
})
print("    HTTP", st)
print(body)
