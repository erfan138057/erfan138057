$reg = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'
$file = Join-Path $PSScriptRoot 'previous-proxy.json'
if (!(Test-Path $file)) { Write-Error 'previous-proxy.json not found; refusing to guess prior settings.'; exit 1 }
$old = Get-Content $file -Raw | ConvertFrom-Json
Set-ItemProperty -Path $reg -Name ProxyEnable -Value ([int]$old.ProxyEnable)
Set-ItemProperty -Path $reg -Name ProxyServer -Value ([string]$old.ProxyServer)
Set-ItemProperty -Path $reg -Name ProxyOverride -Value ([string]$old.ProxyOverride)
Write-Host 'Previous user proxy settings restored.'
