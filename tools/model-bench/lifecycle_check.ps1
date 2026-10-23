# Stage 4.4 lifecycle check: rotation and background/foreground cycles on the phone.
# Rotation is forced temporarily with `wm user-rotation`; the original state
# (auto-rotate on, user_rotation 0) is restored in `finally`.
param([int]$Cycles = 10, [string]$Out = 'C:\Develop\my-chati-bot\plan\eyes\evidence\stage4-lifecycle-raw.txt')
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"; $s = $env:CHATI_PHONE_SERIAL; if (-not $s) { throw 'set CHATI_PHONE_SERIAL to the phone serial (adb devices -l)' }
$shots = 'C:\Develop\my-chati-bot\plan\eyes\evidence\screens\stage4'
function sh($c) { & $adb -s $s shell $c }
function pidof() { "$(sh 'pidof app.mychatibot')".Trim() }
function stats() {
    & $adb -s $s shell am broadcast -a app.mychatibot.DEBUG_EYES --ez stats true | Out-Null
    Start-Sleep -Milliseconds 400
    "$(& $adb -s $s logcat -d -s ChatiEyes:I | Select-String 'stats ' | Select-Object -Last 1)"
}
function shot($name) { cmd /c "`"$adb`" -s $s exec-out screencap -p > `"$shots\$name.png`"" }

& {
    "=== start $(sh 'date +%H:%M:%S') pid=$(pidof)"
    stats
    try {
        "=== rotation x$Cycles"
        for ($i = 1; $i -le $Cycles; $i++) {
            sh 'wm user-rotation lock 1' | Out-Null; Start-Sleep -Seconds 2
            if ($i -eq 1) { shot '17-landscape' }
            sh 'wm user-rotation lock 0' | Out-Null; Start-Sleep -Seconds 2
        }
    } finally {
        sh 'wm user-rotation free' | Out-Null
        sh 'settings put system accelerometer_rotation 1' | Out-Null
        "restored: accelerometer_rotation=$(sh 'settings get system accelerometer_rotation') user_rotation=$(sh 'settings get system user_rotation') wm=$(sh 'wm user-rotation')"
    }
    "after rotation pid=$(pidof)"
    stats
    shot '18-after-rotation'

    "=== background x$Cycles"
    for ($i = 1; $i -le $Cycles; $i++) {
        sh 'input keyevent KEYCODE_HOME' | Out-Null; Start-Sleep -Seconds 2
        sh 'am start -n app.mychatibot/.MainActivity' | Out-Null; Start-Sleep -Seconds 2
    }
    "after background pid=$(pidof)"
    stats

    "=== suspended while in background? (CPU ticks over 5 s, utime+stime from /proc/<pid>/stat)"
    $p = pidof
    function ticks() { $f = "$(sh "cat /proc/$p/stat")".Split(' '); [int64]$f[13] + [int64]$f[14] }
    $t0 = ticks; Start-Sleep -Seconds 5; $fg = (ticks) - $t0
    sh 'input keyevent KEYCODE_HOME' | Out-Null; Start-Sleep -Seconds 2
    $t1 = ticks; Start-Sleep -Seconds 5; $bg = (ticks) - $t1
    "foreground 5 s: $fg ticks; background 5 s: $bg ticks (100 ticks = 1 CPU-second)"
    sh 'am start -n app.mychatibot/.MainActivity' | Out-Null; Start-Sleep -Seconds 3
    "resumed: $(stats)"
    shot '19-after-background'

    "=== errors"
    & $adb -s $s logcat -d -s ChatiEyes:W LVGL:W | Select-Object -Last 10
    "=== crash buffer"
    & $adb -s $s logcat -d -b crash | Select-Object -Last 5
    "=== end $(sh 'date +%H:%M:%S') pid=$(pidof)"
} *> $Out
