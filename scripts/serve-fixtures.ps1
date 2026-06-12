# Serves testdata/*.ics on http://localhost:8800/ for emulator self-testing.
# Use with: adb reverse tcp:8800 tcp:8800  (app then fetches http://localhost:8800/<name>.ics)
# HttpListener bound to localhost rejects other Host headers with 400 Invalid Hostname,
# so feeds must be added with localhost URLs, not 10.0.2.2.
param(
    [int]$Port = 8800,
    [string]$Root = (Join-Path (Split-Path $PSScriptRoot -Parent) 'testdata')
)

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:$Port/")
$listener.Start()
Write-Host "Serving $Root on http://localhost:$Port/ (Ctrl+C to stop)"

while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $name = [System.IO.Path]::GetFileName($ctx.Request.Url.AbsolutePath)
    $path = Join-Path $Root $name
    if ((Test-Path $path) -and $name -like '*.ics') {
        $bytes = [System.IO.File]::ReadAllBytes($path)
        $ctx.Response.ContentType = 'text/calendar'
        $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        Write-Host "200 $name"
    } else {
        $ctx.Response.StatusCode = 404
        Write-Host "404 $($ctx.Request.Url.AbsolutePath)"
    }
    $ctx.Response.Close()
}
