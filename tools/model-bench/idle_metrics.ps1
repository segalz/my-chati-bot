# Stage 4.7 idle soak: no input for N seconds, then collect frame/memory/thermal evidence.
param([int]$Seconds = 600, [string]$Out = 'C:\Develop\my-chati-bot\plan\eyes\evidence\stage4-idle-raw.txt')
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"; $s = $env:CHATI_PHONE_SERIAL; if (-not $s) { throw 'set CHATI_PHONE_SERIAL to the phone serial (adb devices -l)' }
function sh($c) { & $adb -s $s shell $c }
function snapshot($label) {
    "=== $label $(sh 'date +%H:%M:%S')"
    $p = "$(sh 'pidof app.mychatibot')".Trim(); "pid=$p"
    if (-not $p) { 'APP PROCESS NOT RUNNING'; sh "logcat -d -v time | grep -E 'mychatibot' | grep -E 'Kill|died|Force' | tail -5"; return }
    & $adb -s $s shell am broadcast -a app.mychatibot.DEBUG_EYES --ez stats true | Out-Null
    Start-Sleep -Milliseconds 500
    & $adb -s $s logcat -d --pid=$p | Select-String 'stats ' | Select-Object -Last 1
    sh "grep -E 'VmRSS|VmHWM|RssAnon|Threads' /proc/$p/status"
    sh "top -b -n 1 -p $p | tail -2"
    sh "dumpsys thermalservice | grep -m1 'mName=CPU'"
    sh "dumpsys battery | grep -E 'temperature|level|USB powered'"
}
& { snapshot 'start'; Start-Sleep -Seconds $Seconds; snapshot 'end'
    '=== meminfo'; sh 'dumpsys meminfo app.mychatibot | head -30'
    '=== crash buffer'; & $adb -s $s logcat -d -b crash | Select-Object -Last 5
} *> $Out
