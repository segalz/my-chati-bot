# Fetches the sherpa-onnx v1.13.8 Android JNI library into third_party/ and verifies its SHA-256.
$ErrorActionPreference = 'Stop'
$name = 'sherpa-onnx-v1.13.8-android-static-link-onnxruntime.tar.bz2'
$sha = '7583ca385ae7d981e65468455c2ea2c9f2da383921dccfc5658d0dc19d309e6f'
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root 'third_party\sherpa-onnx-v1.13.8\jniLibs\arm64-v8a'
$tmp = Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Force $tmp, $dest | Out-Null
Invoke-WebRequest "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/$name" -OutFile "$tmp\$name"
if ((Get-FileHash "$tmp\$name" -Algorithm SHA256).Hash.ToLower() -ne $sha) { throw "SHA-256 mismatch for $name" }
tar -xjf "$tmp\$name" -C $tmp jniLibs/arm64-v8a/libsherpa-onnx-jni.so
Copy-Item "$tmp\jniLibs\arm64-v8a\libsherpa-onnx-jni.so" $dest -Force
Remove-Item -Recurse -Force $tmp
Write-Host "ok: $dest\libsherpa-onnx-jni.so"
