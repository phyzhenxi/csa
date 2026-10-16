# Run as Administrator to point www.longzoro.com to local gateway
$hosts = "C:\Windows\System32\drivers\etc\hosts"
$line = "127.0.0.1 www.longzoro.com"
if (-not (Select-String -Path $hosts -Pattern "www.longzoro.com" -ErrorAction SilentlyContinue)) {
  Add-Content -Path $hosts -Value $line
  Write-Host "Added: $line"
} else {
  Write-Host "Already present"
}
ipconfig /flushdns
Write-Host "Test: ping www.longzoro.com should show 127.0.0.1"
