param([string]$FeedName, [string]$FeedUrl)
$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial = 'emulator-5554'

function Get-Dump {
    & $adb -s $serial shell uiautomator dump /sdcard/ui.xml | Out-Null
    & $adb -s $serial exec-out cat /sdcard/ui.xml
}

function Get-Center([string]$boundsStr) {
    if ($boundsStr -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $x = ([int]$Matches[1] + [int]$Matches[3]) / 2
        $y = ([int]$Matches[2] + [int]$Matches[4]) / 2
        return @([int]$x, [int]$y)
    }
    throw "Bad bounds: $boundsStr"
}

# Bounds of the Nth EditText (0-based) in the current screen
function Get-EditTextCenter([int]$index) {
    $dump = Get-Dump
    $nodes = [regex]::Matches($dump, 'class="android\.widget\.EditText"[^>]*bounds="(\[[\d,\[\]]+\])"')
    if ($nodes.Count -le $index) { throw "EditText #$index not found ($($nodes.Count) present)" }
    Get-Center $nodes[$index].Groups[1].Value
}

function Get-TextCenter([string]$text) {
    $dump = Get-Dump
    $m = [regex]::Match($dump, ('text="{0}"[^>]*bounds="(\[[\d,\[\]]+\])"' -f [regex]::Escape($text)))
    if (-not $m.Success) { throw "Node with text '$text' not found" }
    Get-Center $m.Groups[1].Value
}

function Tap($xy) { & $adb -s $serial shell input tap $xy[0] $xy[1]; Start-Sleep -Milliseconds 800 }

# 1. Open dialog
Tap (Get-TextCenter '+ Add ICS feed')
# 2. Name field: tap (re-resolve after keyboard appears), then type
Tap (Get-EditTextCenter 0)
& $adb -s $serial shell input text ($FeedName -replace ' ','%s'); Start-Sleep -Milliseconds 500
# 3. URL field: re-resolve bounds (keyboard may have shifted the dialog)
Tap (Get-EditTextCenter 1)
& $adb -s $serial shell input text $FeedUrl; Start-Sleep -Milliseconds 500
# 4. Hide keyboard so the Add button is visible, then re-resolve and tap it
& $adb -s $serial shell input keyevent 111; Start-Sleep -Milliseconds 800
Tap (Get-TextCenter 'Add')
Write-Output "Added feed: $FeedName"
