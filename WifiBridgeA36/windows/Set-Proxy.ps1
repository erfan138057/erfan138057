$reg = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'
$old = Get-ItemProperty -Path $reg
[pscustomobject]@{ ProxyEnable=$old.ProxyEnable; ProxyServer=$old.ProxyServer; ProxyOverride=$old.ProxyOverride } | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'previous-proxy.json')
Set-ItemProperty -Path $reg -Name ProxyEnable -Value 1
Set-ItemProperty -Path $reg -Name ProxyServer -Value '127.0.0.1:8899'
Set-ItemProperty -Path $reg -Name ProxyOverride -Value '<local>'
Write-Host 'Proxy set to 127.0.0.1:8899 for WinINET-aware applications only.'
