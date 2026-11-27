Write-Host "Starting Banking System Demo (Backend: 8081 | Frontend: 5173)..." -ForegroundColor Cyan

$root = "C:\Users\Tanvi Juneja\Downloads\banking-system\banking-system"
$frontend = "$root\frontend"

# 1. Clean any old processes on 8081 and 5173
8081, 5173 | ForEach-Object {
    Get-NetTCPConnection -LocalPort $_ -ErrorAction SilentlyContinue | ForEach-Object {
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

# 2. Start Spring Boot Backend (Port 8081) in background
Start-Process powershell -WindowStyle Minimized -ArgumentList "-NoExit", "-Command", "Set-Location '$root'; .\mvnw.cmd spring-boot:run"

# 3. Start React Frontend (Port 5173) in background
Start-Process powershell -WindowStyle Minimized -ArgumentList "-NoExit", "-Command", "Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force; `$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User') + ';C:\Program Files\nodejs'; Set-Location '$frontend'; npm run dev -- --host"

Write-Host "Waiting 10 seconds for servers to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# 4. Open Frontend URL automatically
Start-Process "http://localhost:5173/"
Write-Host "Demo Ready! Frontend: http://localhost:5173 | Backend API: http://localhost:8081" -ForegroundColor Green