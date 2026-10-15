param([string]$Executable = "$PSScriptRoot\..\..\..\target\debug\webview-crate.exe")
$ErrorActionPreference = 'Stop'
$sidecar = [Diagnostics.Process]::new()
$sidecar.StartInfo.FileName = [IO.Path]::GetFullPath($Executable)
$sidecar.StartInfo.Arguments = '--port 0'
$sidecar.StartInfo.UseShellExecute = $false
$sidecar.StartInfo.CreateNoWindow = $true
$sidecar.StartInfo.RedirectStandardOutput = $true
$sidecar.StartInfo.RedirectStandardError = $true
$baseUrl = $null
try {
    if (-not $sidecar.Start()) { throw 'Could not start the sidecar' }
    $handshake = $sidecar.StandardOutput.ReadLineAsync()
    if (-not $handshake.Wait(15000)) { throw 'Sidecar startup timed out' }
    if ($handshake.Result -notmatch '^SWASWA_WEBVIEW_PORT=(\d+)$') {
        throw "Invalid sidecar handshake: $($handshake.Result)"
    }
    $baseUrl = "http://127.0.0.1:$($Matches[1])"
    $spec = @{
        html = '<button id="sample" style="color:rgb(1,2,3);background-color:rgb(4,5,6)">Sample</button><script>fetch("data:text/plain,headless-smoke");document.getElementById("sample").click();document.getElementById("sample").dispatchEvent(new MouseEvent("pointerover",{bubbles:true}));</script>'
        headless = $true
    } | ConvertTo-Json
    $opened = Invoke-RestMethod "$baseUrl/webview" -Method Post -ContentType 'application/json' -Body $spec -TimeoutSec 15
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $events = Invoke-RestMethod "$baseUrl/api-calls" -TimeoutSec 5
        $captured = $events.calls | Where-Object { $_.viewId -eq $opened.id }
        if ($captured) {
            if ($captured[0].status -ne 200 -or $captured[0].responseBody -ne 'headless-smoke') {
                throw 'Hidden webview returned unexpected capture data'
            }
            $dom = Invoke-RestMethod "$baseUrl/dom-events" -TimeoutSec 5
            if (-not ($dom.events | Where-Object { $_.type -eq 'click' -and $_.element.id -eq 'sample' })) { throw 'Missing click event' }
            if (-not ($dom.events | Where-Object { $_.type -eq 'hover' })) { throw 'Missing hover event' }
            $queryBody = @{ script = 'window.__SWASWA_DOM__.query("#sample",true)' } | ConvertTo-Json
            $query = Invoke-RestMethod "$baseUrl/webview/$($opened.id)/eval" -Method Post -Body $queryBody -ContentType 'application/json' -TimeoutSec 5
            if ($query.result.elements[0].styles.color -ne 'rgb(1, 2, 3)') { throw 'Unexpected computed color' }
            Write-Output 'PASS: hidden native webview delivered API, click, hover, element query, and computed style results.'
            return
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Hidden webview produced no captured API call'
} finally {
    if ($baseUrl) {
        try { Invoke-RestMethod "$baseUrl/shutdown" -Method Post -Body '{}' -ContentType 'application/json' -TimeoutSec 3 | Out-Null } catch {}
    }
    if (-not $sidecar.HasExited) {
        if (-not $sidecar.WaitForExit(3000)) { $sidecar.Kill() }
    }
    $sidecar.Dispose()
}
