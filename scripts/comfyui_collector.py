#!/usr/bin/env python3
"""Collect a real ComfyUI generation and upload it as attested evidence.

The script submits an API-format workflow, waits for ComfyUI to finish, reads
the actual parameters from ComfyUI's /history endpoint, uploads the generated
image together with the collected metadata, and lets the backend create a work
with sourceType=comfyui plus a local evidence record.

Requirements: requests. The bundled ComfyUI python already includes it.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
import uuid
from pathlib import Path

import requests


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def build_sd_workflow(args) -> dict:
    if not args.model:
        raise SystemExit("inline mode requires --model, for example v1-5-pruned-emaonly.safetensors")
    if not args.prompt:
        raise SystemExit("inline mode requires --prompt")

    return {
        "4": {
            "class_type": "CheckpointLoaderSimple",
            "inputs": {"ckpt_name": args.model},
        },
        "5": {
            "class_type": "EmptyLatentImage",
            "inputs": {
                "width": args.width,
                "height": args.height,
                "batch_size": 1,
            },
        },
        "6": {
            "class_type": "CLIPTextEncode",
            "inputs": {
                "text": args.prompt,
                "clip": ["4", 1],
            },
        },
        "7": {
            "class_type": "CLIPTextEncode",
            "inputs": {
                "text": args.negative_prompt,
                "clip": ["4", 1],
            },
        },
        "3": {
            "class_type": "KSampler",
            "inputs": {
                "seed": args.seed,
                "steps": args.steps,
                "cfg": args.cfg,
                "sampler_name": args.sampler,
                "scheduler": args.scheduler,
                "denoise": 1.0,
                "model": ["4", 0],
                "positive": ["6", 0],
                "negative": ["7", 0],
                "latent_image": ["5", 0],
            },
        },
        "8": {
            "class_type": "VAEDecode",
            "inputs": {
                "samples": ["3", 0],
                "vae": ["4", 2],
            },
        },
        "9": {
            "class_type": "SaveImage",
            "inputs": {
                "filename_prefix": args.filename_prefix,
                "images": ["8", 0],
            },
        },
    }


def load_api_workflow(path: Path) -> dict:
    raw = path.read_text(encoding="utf-8")
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"workflow is not valid JSON: {exc}")
    if isinstance(payload, dict) and "nodes" in payload:
        raise SystemExit(
            "workflow appears to be UI format. Export it with 'Save (API Format)' in ComfyUI, "
            "or use --prompt/--model for an inline SD workflow."
        )
    return payload


def nodes_from_prompt(prompt) -> dict:
    if isinstance(prompt, dict):
        return dict(prompt)
    nodes = {}
    if isinstance(prompt, list):
        for entry in prompt:
            if isinstance(entry, dict):
                nodes.update(entry)
            elif isinstance(entry, (list, tuple)) and len(entry) == 2:
                nodes[str(entry[0])] = entry[1]
    return nodes


def first_node(nodes: dict, class_type: str):
    for node in nodes.values():
        if isinstance(node, dict) and node.get("class_type") == class_type:
            return node
    return None


def node_text(node) -> str:
    if not node:
        return ""
    return str(node.get("inputs", {}).get("text", "") or "")


def linked_node_id(node, input_name: str) -> str:
    value = node.get("inputs", {}).get(input_name)
    if isinstance(value, (list, tuple)) and value:
        return str(value[0])
    return str(value or "")


def build_metadata(prompt, prompt_id: str, history: dict) -> dict:
    nodes = nodes_from_prompt(prompt)
    ksampler = first_node(nodes, "KSampler")
    checkpoint = first_node(nodes, "CheckpointLoaderSimple")
    latent = first_node(nodes, "EmptyLatentImage")

    positive_node = nodes.get(linked_node_id(ksampler, "positive")) if ksampler else None
    negative_node = nodes.get(linked_node_id(ksampler, "negative")) if ksampler else None

    model_name = str((checkpoint or {}).get("inputs", {}).get("ckpt_name", "") or "")
    model_stem = Path(model_name).stem if model_name else ""

    k_inputs = ksampler.get("inputs", {}) if ksampler else {}
    latent_inputs = latent.get("inputs", {}) if latent else {}

    return {
        "title": "ComfyUI " + prompt_id[:8],
        "prompt": node_text(positive_node),
        "negativePrompt": node_text(negative_node),
        "modelName": model_stem,
        "modelVersion": "",
        "seed": str(k_inputs.get("seed", "0")),
        "steps": int(k_inputs.get("steps", 0)),
        "cfgScale": float(k_inputs.get("cfg", 0)),
        "width": int(latent_inputs.get("width", 0)),
        "height": int(latent_inputs.get("height", 0)),
        "sampler": str(k_inputs.get("sampler_name", "") or ""),
        "scheduler": str(k_inputs.get("scheduler", "") or ""),
        "workflowHash": sha256_text(json.dumps(prompt, sort_keys=True, ensure_ascii=False)),
        "requestId": prompt_id,
        "workflowRaw": json.dumps(prompt, ensure_ascii=False),
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()),
    }


def find_output_image(outputs: dict) -> dict:
    for node_output in outputs.values():
        images = (node_output or {}).get("images")
        if images:
            return images[0]
    return None


def image_path(comfyui_dir: Path, image: dict) -> Path:
    filename = image.get("filename", "")
    subfolder = image.get("subfolder", "")
    return comfyui_dir / "output" / subfolder / filename


def upload_image(api_url: str, token: str, metadata: dict, image_file: Path) -> dict:
    metadata_blob = json.dumps(metadata, ensure_ascii=False)
    with image_file.open("rb") as fh:
        response = requests.post(
            api_url + "/work/import/comfyui",
            files={
                "metadata": ("metadata.json", metadata_blob, "application/json"),
                "file": (image_file.name, fh, "image/png"),
            },
            headers={"Authorization": "Bearer " + token} if token else {},
            timeout=120,
        )
    if response.status_code >= 400:
        raise SystemExit(f"upload failed ({response.status_code}): {response.text[:500]}")
    try:
        return response.json()
    except json.JSONDecodeError:
        raise SystemExit(f"upload returned non-JSON: {response.text[:500]}")


def submit_prompt(comfyui_url: str, prompt: dict) -> str:
    client_id = str(uuid.uuid4())
    response = requests.post(
        comfyui_url + "/prompt",
        json={"prompt": prompt, "client_id": client_id},
        timeout=30,
    )
    if response.status_code >= 400:
        raise SystemExit(f"ComfyUI /prompt failed ({response.status_code}): {response.text[:500]}")
    body = response.json()
    prompt_id = body.get("prompt_id")
    if not prompt_id:
        raise SystemExit(f"ComfyUI returned no prompt_id: {body}")
    print(f"submitted prompt_id={prompt_id}")
    return prompt_id


def wait_for_history(comfyui_url: str, prompt_id: str, timeout: int, interval: float) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        response = requests.get(comfyui_url + f"/history/{prompt_id}", timeout=30)
        if response.status_code >= 400:
            raise SystemExit(f"ComfyUI /history failed ({response.status_code}): {response.text[:500]}")
        body = response.json()
        entry = body.get(prompt_id)
        if entry:
            status = entry.get("status", {})
            if status.get("completed"):
                return entry
            if status.get("status_str") == "error":
                raise SystemExit("ComfyUI reported an execution error")
        time.sleep(interval)
    raise SystemExit(f"timed out waiting for ComfyUI prompt {prompt_id}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workflow", type=Path, help="API-format workflow JSON exported from ComfyUI")
    parser.add_argument("--prompt", help="positive prompt, used only in inline workflow mode")
    parser.add_argument("--negative-prompt", default="", help="negative prompt, used only in inline mode")
    parser.add_argument("--model", help="checkpoint filename, used only in inline mode")
    parser.add_argument("--width", type=int, default=512)
    parser.add_argument("--height", type=int, default=512)
    parser.add_argument("--steps", type=int, default=20)
    parser.add_argument("--cfg", type=float, default=7.0)
    parser.add_argument("--sampler", default="euler")
    parser.add_argument("--scheduler", default="normal")
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--filename-prefix", default="aic")
    parser.add_argument("--title", help="optional work title override")
    parser.add_argument("--comfyui-url", default=os.environ.get("COMFYUI_URL", "http://127.0.0.1:8188"))
    parser.add_argument(
        "--comfyui-dir",
        type=Path,
        default=os.environ.get(
            "COMFYUI_DIR",
            r"D:\AI\ComfyUI_windows_portable_nvidia\ComfyUI_windows_portable\ComfyUI",
        ),
    )
    parser.add_argument("--api-url", default=os.environ.get("AIC_API_URL", "http://127.0.0.1:8080/api"))
    parser.add_argument("--api-token", default=os.environ.get("AIC_API_TOKEN", ""))
    parser.add_argument("--poll-timeout", type=int, default=300)
    parser.add_argument("--poll-interval", type=float, default=0.5)
    parser.add_argument("--no-upload", action="store_true", help="collect and print metadata without uploading")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    prompt = build_sd_workflow(args) if args.workflow is None else load_api_workflow(args.workflow)
    prompt_id = submit_prompt(args.comfyui_url.rstrip("/"), prompt)

    entry = wait_for_history(
        args.comfyui_url.rstrip("/"),
        prompt_id,
        args.poll_timeout,
        args.poll_interval,
    )
    output_image = find_output_image(entry.get("outputs", {}))
    if output_image is None:
        raise SystemExit("ComfyUI finished but no output image was found")

    metadata = build_metadata(prompt, prompt_id, entry)
    if args.title:
        metadata["title"] = args.title

    file_path = image_path(args.comfyui_dir, output_image)
    if not file_path.exists():
        raise SystemExit(f"generated image not found: {file_path}")

    print("collected parameters:")
    for key in ("prompt", "negativePrompt", "modelName", "seed", "steps", "cfgScale",
                "width", "height", "sampler", "scheduler", "workflowHash", "requestId"):
        value = metadata.get(key)
        print(f"  {key}: {value if key not in ('prompt', 'negativePrompt') else (str(value)[:80] + '...') if value and len(str(value)) > 80 else value}")
    print(f"output image: {file_path}")

    if args.no_upload:
        return 0

    response = upload_image(args.api_url.rstrip("/"), args.api_token, metadata, file_path)
    if response.get("code") != 200:
        raise SystemExit(f"backend rejected metadata: {response}")
    data = response.get("data") or {}
    work = data.get("work") or {}
    evidence = data.get("evidence") or {}
    print("uploaded:", json.dumps({
        "workId": work.get("id"),
        "title": work.get("title"),
        "dataHash": evidence.get("dataHash"),
        "chainStatus": evidence.get("chainStatus"),
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
