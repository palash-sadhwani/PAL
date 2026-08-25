# One-shot Pixel 7a validation helper
# Run from repository root (Windows PowerShell)
# Adjust these variables if your paths/package differ
$apk = "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "com.palash.pal"
$logFile = "$PWD\pal_logcat.txt"
$modelFileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
$pollTimeoutSec = 1800    # how long to wait for model file (30 min)
$pollIntervalSec = 5

function Check-ADB {
    Write-Host "Checking adb..."
    $adbPath = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if (-not $adbPath) { Write-Error "adb not found in PATH. Install Android platform-tools and ensure adb is on PATH."; exit 2 }
    & adb version
}

function Install-Apk {
    param($apkPath)
    if (-not (Test-Path $apkPath)) { Write-Error "APK not found at $apkPath"; exit 3 }
    Write-Host "Installing APK: $apkPath"
    & adb install -r $apkPath
    if ($LASTEXITCODE -ne 0) { Write-Error "adb install failed (exit $LASTEXITCODE)"; exit 4 }
}

function Clear-Logcat { Write-Host "Clearing logcat..."; & adb logcat -c }

function Start-LogCapture {
    param($outFile)
    if (Test-Path $outFile) { Remove-Item $outFile -Force -ErrorAction SilentlyContinue }
    Write-Host "Starting background logcat -> $outFile"
    $cmd = "/c adb logcat -v time > `"$outFile`""
    $proc = Start-Process -FilePath "cmd.exe" -ArgumentList $cmd -WindowStyle Hidden -PassThru
    Start-Sleep -Milliseconds 600
    return $proc
}

function Stop-LogCapture {
    param($proc)
    if ($proc -and !$proc.HasExited) {
        Write-Host "Stopping log capture (PID $($proc.Id))..."
        Stop-Process -Id $proc.Id -Force
        Start-Sleep -Milliseconds 300
    } else {
        Write-Host "Log capture process already stopped."
    }
}

function Launch-App {
    param($package)
    Write-Host "Launching app $package..."
    & adb shell am start -n "$package/.MainActivity"
    if ($LASTEXITCODE -ne 0) {
        & adb shell monkey -p $package -c android.intent.category.LAUNCHER 1
    }
}

function Poll-For-ModelFile {
    param($package, $filename, $timeoutSec, $intervalSec)
    Write-Host "Polling device for model file: $filename (up to $timeoutSec seconds)..."
    $start = Get-Date
    while ((Get-Date) - $start -lt [TimeSpan]::FromSeconds($timeoutSec)) {
        $out = & adb shell run-as $package ls files/models 2>&1
        if ($out -and $out -match [regex]::Escape($filename)) {
            Write-Host "Model file present on device (files/models/$filename)"
            return $true
        }
        Start-Sleep -Seconds $intervalSec
    }
    Write-Warning "Timed out waiting for model file on device."
    return $false
}

function Dump-Important-LogLines {
    param($logPath)
    Write-Host ""
    Write-Host "---- Important diagnostic lines from $logPath ----"
    if (-not (Test-Path $logPath)) { Write-Warning "Log file not found: $logPath"; return }
    $patterns = @(
        "REAL_INFERENCE_SUCCESS",
        "MODEL_NOT_READY",
        "MODEL_FILE_MISSING",
        "NATIVE_LIBRARY_UNAVAILABLE",
        "NATIVE_MODEL_LOAD_ATTEMPT",
        "NATIVE_MODEL_LOAD_RESULT",
        "NATIVE_MODEL_LOAD_FAILED",
        "JNI_ERROR",
        "GENERATION_ERROR",
        "Placeholder response",
        "Model download failed",
        "Downloaded model checksum"
    )
    foreach ($pat in $patterns) {
        Write-Host "`n--- Matches for [$pat] ---"
        Select-String -Path $logPath -Pattern $pat -SimpleMatch -CaseSensitive:$false |
            ForEach-Object { $_.Line } | Select-Object -First 200
    }
    Write-Host "---- End diagnostics ----`n"
}

# ---- Script main ----
Check-ADB
Install-Apk -apkPath $apk
Clear-Logcat
$logProc = Start-LogCapture -outFile $logFile
Launch-App -package $pkg

Write-Host ""
Write-Host "On the device: accept the download consent dialog and tap Download."
Write-Host "The script will wait up to $pollTimeoutSec seconds for the model file to appear, or press Enter now once you have tapped Download and want to proceed."
Write-Host "If you want to wait for the script to detect the file automatically, do not press Enter."

$entered = $false
$detected = $false
$start = Get-Date
while ((Get-Date) - $start -lt [TimeSpan]::FromSeconds(600)) {
    if ([console]::KeyAvailable) {
        $k = [console]::ReadKey($true)
        if ($k.Key -eq 'Enter') { $entered = $true; break }
    }
    $out = & adb shell run-as $pkg ls files/models 2>&1
    if ($out -and $out -match [regex]::Escape($modelFileName)) {
        $detected = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $detected -and -not $entered) {
    Write-Host "Model file not yet detected."
    Read-Host -Prompt "Press Enter to stop log capture and collect diagnostics now"
}

if (-not $detected) {
    $detected = Poll-For-ModelFile -package $pkg -filename $modelFileName -timeoutSec $pollTimeoutSec -intervalSec $pollIntervalSec
}

Stop-LogCapture -proc $logProc

if (-not (Test-Path $logFile)) {
    Write-Host "Local log file not found, attempting to pull adb logcat dump to $logFile..."
    & adb logcat -d > $logFile
}

Dump-Important-LogLines -logPath $logFile

if ($detected) {
    Write-Host "Model file was detected on device. For final verification, send a prompt from the app and check the log for REAL_INFERENCE_SUCCESS."
} else {
    Write-Warning "Model file not detected on device; check device UI and network. Use the Retry button in the app to attempt download again."
}

Write-Host "Script finished."
