param(
    [ValidateSet("sd15", "sdxl-turbo")]
    [string]$Model = "sd15",

    [string]$ComfyUiDir = "D:\AI\ComfyUI_windows_portable_nvidia\ComfyUI_windows_portable\ComfyUI",

    [switch]$SkipDownload
)

$ErrorActionPreference = "Stop"

$checkpointDir = Join-Path $ComfyUiDir "models\checkpoints"
if (-not (Test-Path -LiteralPath $checkpointDir)) {
    throw "Checkpoint folder not found: $checkpointDir"
}

$modelMap = @{
    "sd15" = @{
        Name = "v1-5-pruned-emaonly-fp16.safetensors"
        Url  = "https://hf-mirror.com/Comfy-Org/stable-diffusion-1.5/resolve/main/v1-5-pruned-emaonly-fp16.safetensors"
        Note = "SD1.5 fp16, 512x512, about 2.0 GB. Recommended baseline for 8 GB VRAM."
    }
    "sdxl-turbo" = @{
        Name = "sd_xl_turbo_1.0_fp16.safetensors"
        Url  = "https://hf-mirror.com/stabilityai/sdxl-turbo/resolve/main/sd_xl_turbo_1.0_fp16.safetensors"
        Note = "SDXL Turbo, low-step workflow, about 6.9 GB. Use with --lowvram when prompted by ComfyUI."
    }
}

$selected = $modelMap[$Model]
$target = Join-Path $checkpointDir $selected.Name

Write-Host "Model: $($selected.Name)"
Write-Host "Target: $target"
Write-Host $selected.Note

if (-not $SkipDownload) {
    if (Test-Path -LiteralPath $target) {
        Write-Host "Checkpoint already exists, skipping download."
    } else {
        Write-Host "Downloading from Hugging Face..."
        curl.exe -L --fail --retry 3 -o $target $selected.Url
        if ($LASTEXITCODE -ne 0) {
            throw "curl download failed with exit code $LASTEXITCODE"
        }
    }
}

if (Test-Path -LiteralPath $target) {
    $sizeGb = [Math]::Round((Get-Item -LiteralPath $target).Length / 1GB, 2)
    Write-Host "Ready: $target ($sizeGb GB)"
} else {
    Write-Host "Checkpoint not present. Re-run without -SkipDownload to fetch it."
}

Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Start ComfyUI with run_nvidia_gpu.bat"
Write-Host "  2. Set AIC_API_TOKEN from the app login response"
Write-Host "  3. Run the collector with the inline SD workflow:"
Write-Host "     python scripts\comfyui_collector.py --prompt \"a red fox in a forest\" --model $($selected.Name) --steps 20 --cfg 7"
