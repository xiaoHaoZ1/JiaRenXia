# 家人侠 命令分发器（jrx.cmd 调用；PS 5.1 / PS 7 均可用）。
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = $PSScriptRoot
$root = (Resolve-Path (Join-Path $scriptRoot "..")).Path

function Write-JrxErr([string]$m) { Write-Host $m -ForegroundColor Red }
function Write-JrxInfo([string]$m) { Write-Host $m -ForegroundColor Cyan }

# ---------- 官网静态服务（apps\web\serve.mjs，127.0.0.1:8088）----------
$webDir   = Join-Path $root "apps\web"
$webPort  = 8088
$pidFile  = Join-Path $root "runtime\run\web.pid.json"

function Get-WebProcesses {
    # 只认本工程的 serve.mjs（按命令行里的绝对路径核对身份）
    $marker = (Join-Path $webDir "serve.mjs").ToLower()
    @(Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine.ToLower().Contains($marker) })
}

function Read-WebPid {
    if (-not (Test-Path $pidFile)) { return $null }
    try { Get-Content $pidFile -Raw | ConvertFrom-Json } catch { $null }
}

function Write-WebPid([int]$procId) {
    $dir = Split-Path $pidFile -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
    @{ pid = $procId; port = $webPort; started = (Get-Date).ToString("s") } |
        ConvertTo-Json | Set-Content $pidFile -Encoding UTF8
}

function Show-Help {
    Write-Host "家人侠 命令行入口（jrx.cmd <命令>）："
    Write-Host "  web-build        构建前端（npm ci + npm run build，产物 apps\web\dist）"
    Write-Host "  web-dev          前端开发服务器（Vite）"
    Write-Host "  web-e2e          前端端到端自检（Playwright，自动构建并起 vite preview）"
    Write-Host "  web-start        官网后台上线（127.0.0.1:8088，隐藏进程，经 cloudflared 对公网）"
    Write-Host "  web-stop         官网下线"
    Write-Host "  web-status       官网运行状态"
    Write-Host "  web-deploy       官网发布到 Cloudflare Pages（build + pages deploy）"
    Write-Host "  dl-deploy        下载服务 Worker 部署（apps\dl-worker，wrangler deploy）"
    Write-Host "  dl-stats         安装包下载计数（D1 jrx-stats，近 30 天明细与汇总）"
    Write-Host "  public-snapshot <目录>  导出公开源码快照（排除清单 + 私密段落剔除 + 泄漏扫描）"
    Write-Host "  android <args>   调 apps\android\gradlew.bat（原样传参）"
    Write-Host "  android-verify   Android 完整验证（日志在 runtime\logs）"
}

if ($Args.Count -eq 0) { Show-Help; exit 0 }
$cmd = $Args[0].ToLower()
$rest = @()
if ($Args.Count -gt 1) { $rest = $Args[1..($Args.Count - 1)] }

switch ($cmd) {
    "help"      { Show-Help; exit 0 }
    "web-build" {
        $web = Join-Path $root "apps\web"
        Push-Location $web
        try {
            & npm.cmd ci --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & npm.cmd run build
            exit $LASTEXITCODE
        } finally { Pop-Location }
    }
    "web-dev" {
        $web = Join-Path $root "apps\web"
        Push-Location $web
        try {
            & npm.cmd run dev @rest
            exit $LASTEXITCODE
        } finally { Pop-Location }
    }
    "web-e2e" {
        $web = Join-Path $root "apps\web"
        Push-Location $web
        try {
            & npm.cmd run e2e @rest
            exit $LASTEXITCODE
        } finally { Pop-Location }
    }
    "web-start" {
        if (-not (Test-Path (Join-Path $webDir "dist\index.html"))) {
            Write-JrxErr "apps\web\dist 不存在，先运行 jrx.cmd web-build"
            exit 2
        }
        $procs = @(Get-WebProcesses)
        if ($procs.Count -gt 0) {
            Write-JrxInfo "官网已在运行（PID $($procs[0].ProcessId)，http://127.0.0.1:$webPort）"
            exit 0
        }
        $conn = Get-NetTCPConnection -LocalPort $webPort -LocalAddress 127.0.0.1 `
                    -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $conn) {
            $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($conn.OwningProcess)" -ErrorAction SilentlyContinue
            Write-JrxErr "127.0.0.1:$webPort 被占用（PID $($conn.OwningProcess) $($owner.Name)），请先处理"
            exit 3
        }
        $logDir = Join-Path $root "runtime\logs"
        if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Force $logDir | Out-Null }
        $log = Join-Path $logDir "web.log"
        $node = (Get-Command node.exe -ErrorAction Stop).Source
        # 经 WMI 直接拉起 node.exe：进程脱离调用方的控制台/作业对象（日志由
        # serve.mjs 自己写 runtime\logs\web.log，不靠 stdout 重定向）
        $cmdline = "`"$node`" `"$(Join-Path $webDir 'serve.mjs')`""
        $r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create `
                -Arguments @{ CommandLine = $cmdline; CurrentDirectory = $webDir }
        if ($r.ReturnValue -ne 0) { Write-JrxErr "启动失败（WMI $($r.ReturnValue)）"; exit 1 }
        for ($i = 0; $i -lt 50; $i++) {
            Start-Sleep -Milliseconds 200
            $procs = @(Get-WebProcesses)
            if ($procs.Count -gt 0) {
                Write-WebPid $procs[0].ProcessId
                Write-JrxInfo "官网已上线：http://127.0.0.1:$webPort（PID $($procs[0].ProcessId)，日志 $log）"
                exit 0
            }
        }
        Write-JrxErr "进程未起来，看日志 $log"
        exit 1
    }
    "web-stop" {
        $procs = @(Get-WebProcesses)
        if ($procs.Count -eq 0) {
            Write-JrxInfo "官网未在运行"
            if (Test-Path $pidFile) { Remove-Item $pidFile -Force }
            exit 0
        }
        $procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
        if (Test-Path $pidFile) { Remove-Item $pidFile -Force }
        Write-JrxInfo "官网已下线"
        exit 0
    }
    "web-status" {
        $procs = @(Get-WebProcesses)
        $rec = Read-WebPid
        if ($procs.Count -gt 0) {
            Write-JrxInfo "官网运行中：PID $($procs[0].ProcessId)（记录于 $(if ($rec) { $rec.started } else { '无记录' })）"
        } else { Write-JrxInfo "官网未在运行" }
        try {
            $resp = Invoke-WebRequest "http://127.0.0.1:$webPort/" -UseBasicParsing -TimeoutSec 5
            Write-Host "  HTTP $($resp.StatusCode)  http://127.0.0.1:$webPort/"
        } catch { Write-Host "  127.0.0.1:$webPort 无响应" }
        exit 0
    }
    "web-deploy" {
        $web = Join-Path $root "apps\web"
        Push-Location $web
        try {
            & npm.cmd run build
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & npx.cmd --yes "wrangler@4.151.3" pages deploy dist `
                --project-name jiarenxia --branch main --commit-dirty=true
            exit $LASTEXITCODE
        } finally { Pop-Location }
    }
    "dl-deploy" {
        $dw = Join-Path $root "apps\dl-worker"
        Push-Location $dw
        try {
            & npx.cmd --yes "wrangler@4.151.3" deploy
            exit $LASTEXITCODE
        } finally { Pop-Location }
    }
    "dl-stats" {
        $sql = "SELECT day, file, src, n FROM daily " +
            "WHERE day >= date('now', '-30 days') ORDER BY day DESC, file, src; " +
            "SELECT file, src, SUM(n) AS total FROM daily " +
            "WHERE day >= date('now', '-30 days') GROUP BY file, src ORDER BY file, total DESC"
        & npx.cmd --yes "wrangler@4.151.3" d1 execute jrx-stats --remote --command $sql
        exit $LASTEXITCODE
    }
    "public-snapshot" {
        if ($rest.Count -lt 1) {
            Write-JrxErr "用法：jrx.cmd public-snapshot <目标目录>"
            exit 2
        }
        $target = [System.IO.Path]::GetFullPath($rest[0])
        if ((Test-Path $target) -and ((Get-ChildItem -Force $target | Measure-Object).Count -gt 0)) {
            Write-JrxErr "目标目录非空：$target"
            exit 2
        }
        New-Item -ItemType Directory -Force $target | Out-Null
        $excludeFile = Join-Path $root "scripts\public-exclude.txt"
        $scanFile = Join-Path $root "scripts\public-scan.txt"
        $excludes = @(Get-Content $excludeFile | Where-Object {
            $_ -match '\S' -and -not $_.Trim().StartsWith('#') } | ForEach-Object { $_.Trim() })
        $patterns = @(Get-Content $scanFile | Where-Object {
            $_ -match '\S' -and -not $_.Trim().StartsWith('#') } | ForEach-Object { $_.Trim() })
        Push-Location $root
        try {
            $tmp = Join-Path $env:TEMP ("jrx-snap-" + [guid]::NewGuid().ToString("N"))
            New-Item -ItemType Directory -Force $tmp | Out-Null
            $tarPath = Join-Path $tmp "repo.tar"
            # worktree 的 .git 指针是 WSL 路径（/mnt/x/...）时 Windows git 认不出；
            # 转成盘符路径后用 --git-dir/--work-tree 直接指定
            $gitArgs = @()
            $dotgit = Join-Path $root ".git"
            if ((Test-Path $dotgit) -and -not (Get-Item $dotgit -Force).PSIsContainer) {
                $gd = (Get-Content $dotgit -Raw).Trim() -replace '^gitdir:\s*', ''
                if ($gd -match '^/mnt/([a-z])/(.*)$') {
                    $gdWin = ($Matches[1].ToUpper() + ":\" + ($Matches[2] -replace '/', '\'))
                    $gitArgs = @("--git-dir=$gdWin", "--work-tree=$root")
                }
            }
            & git @gitArgs archive HEAD -o $tarPath
            if ($LASTEXITCODE -ne 0) { Write-JrxErr "git archive 失败"; exit 1 }
            & tar -xf $tarPath -C $tmp
            if ($LASTEXITCODE -ne 0) { Write-JrxErr "tar 解包失败"; exit 1 }
            Remove-Item -Force $tarPath
            # 排除清单：先删文件/目录本体，再整体拷贝
            foreach ($e in $excludes) {
                $f = Join-Path $tmp ($e -replace '/', '\')
                if (Test-Path $f) { Remove-Item -Recurse -Force $f }
            }
            Copy-Item -Recurse -Force (Join-Path $tmp '*') $target
            # 被排除文件替换为占位文件（scripts/public-stubs/ 下，键 = 路径以 _ 连接）
            foreach ($e in $excludes) {
                $stub = Join-Path $root ("scripts\public-stubs\" + ($e -replace '/', '_' -replace '\.md$', '') + '.md')
                if (Test-Path $stub) {
                    $dst = Join-Path $target ($e -replace '/', '\')
                    New-Item -ItemType Directory -Force (Split-Path $dst) | Out-Null
                    Copy-Item -Force $stub $dst
                }
            }
            # 剔除 private 标记块（所有含标记的文本文件）
            Get-ChildItem -Recurse -Force -File $target | ForEach-Object {
                $fp = $_.FullName
                $c = [System.IO.File]::ReadAllText($fp)
                if ($c.Contains('<!-- private:start -->')) {
                    $c2 = [regex]::Replace($c, '(?s)<!-- private:start -->.*?<!-- private:end -->\s*', '')
                    [System.IO.File]::WriteAllText($fp, $c2)
                }
            }
            # 泄漏扫描
            $hits = 0
            Get-ChildItem -Recurse -Force -File $target | ForEach-Object {

                $fp = $_.FullName
                $ln = 0
                foreach ($line in [System.IO.File]::ReadAllLines($fp)) {
                    $ln++
                    foreach ($pat in $patterns) {
                        if ($line -imatch $pat) {
                            Write-Host ("命中 {0}:{1}: {2}" -f $fp, $ln, $line.Trim())
                            $hits++
                        }
                    }
                }
            }
            Remove-Item -Recurse -Force $tmp
            if ($hits -gt 0) { Write-JrxErr "泄漏扫描发现 $hits 处命中"; exit 1 }
            Write-JrxInfo "公开快照完成：$target"
            exit 0
        } finally { Pop-Location }
    }
    "android" {
        $ad = Join-Path $root "apps\android"
        if (-not (Test-Path (Join-Path $ad "gradlew.bat"))) {
            Write-JrxErr "Android 工程不在 apps\android"
            exit 2
        }
        Push-Location $ad
        try {
            & .\gradlew.bat @rest
            exit $LASTEXITCODE
        } finally { Pop-Location }
    }
    "android-verify" {
        $ad = Join-Path $root "apps\android"
        if (-not (Test-Path (Join-Path $ad "gradlew.bat"))) {
            Write-JrxErr "Android 工程不在 apps\android"
            exit 2
        }
        $logDir = Join-Path $root "runtime\logs"
        if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Force $logDir | Out-Null }
        $ts = Get-Date -Format "yyyyMMdd-HHmmss"
        $tasks = @(":feature:guardian:testDebugUnitTest", ":app:testDebugUnitTest",
                   ":core:kinship:test", ":core:model:test", ":app:lintDebug",
                   ":app:assembleDebug", ":app:assembleRelease",
                   ":guardianapp:testDebugUnitTest", ":guardianapp:lintDebug",
                   ":guardianapp:assembleDebug", ":guardianapp:assembleRelease")
        $maxTry = 2
        for ($try = 1; $try -le $maxTry; $try++) {
            $log = Join-Path $logDir "android-verify-$ts-$try.log"
            Write-JrxInfo "第 $try 次验证，日志：$log"
            # PS 5.1 下重定向的原生 stderr 会变成 NativeCommandError，
            # 全局 Stop 会中断管道而 gradle 继续跑；局部降为 Continue，
            # 并经 cmd.exe 合并 stderr，避免再触发该问题。
            $prevEap = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            Push-Location $ad
            try {
                & cmd.exe /c "gradlew.bat $($tasks -join ' ') 2>&1" | Tee-Object -FilePath $log
                $code = $LASTEXITCODE
            } finally { $ErrorActionPreference = $prevEap; Pop-Location }
            $content = Get-Content $log -Raw -ErrorAction SilentlyContinue
            $flaky = ($content -match "KotlinIllegalArgumentExceptionWithAttachments")
            if ($code -eq 0) { Write-JrxInfo "验证通过"; exit 0 }
            if (-not $flaky -or $try -eq $maxTry) {
                Write-JrxErr "验证失败（$code），日志 $log"
                exit $code
            }
            Write-JrxInfo "命中已知 lint 偶发崩溃，重跑一次"
        }
        exit 2
    }
    default {
        Write-JrxErr "未知命令：$cmd"
        Show-Help
        exit 2
    }
}
