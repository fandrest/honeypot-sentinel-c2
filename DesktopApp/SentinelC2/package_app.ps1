# Script de empaquetado comercial SentinelC2 (jpackage)
# Requiere Java 14+ instalado y Maven (para generar el JAR primero)

Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "   SENTINEL C2 - GENERADOR COMERCIAL     " -ForegroundColor Cyan
Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan

# 1. Compilar proyecto con Maven
Write-Host "[1/3] Compilando binarios con Maven..." -ForegroundColor Yellow
mvn clean package

$targetJar = Get-ChildItem -Path "target\*.jar" | Select-Object -First 1
if (-not $targetJar) {
    Write-Host "ERROR: No se encontró el archivo JAR en \target\" -ForegroundColor Red
    exit
}

# 2. Definir parámetros
$version = "1.0.0"
$appName = "SentinelC2"
$vendor = "Sentinel Command"
$mainClass = "io.sentinel.command.sentinelc2.MainFrame" # Ajustar si la clase principal es distinta

# 3. Ejecutar jpackage (Windows)
Write-Host "[2/3] Generando ejecutable nativo (.exe) portátil..." -ForegroundColor Yellow
jpackage --type app-image `
         --input target/ `
         --dest dist/ `
         --name $appName `
         --main-jar $targetJar.Name `
         --main-class $mainClass `
         --win-menu `
         --win-shortcut `
         --win-dir-chooser `
         --vendor $vendor `
         --app-version $version `
         --description "Distributed Honeypot Command & Control Platform" `
         --icon src/main/resources/sentinel_icon.ico # Asegúrate de tener un .ico aquí

Write-Host "[3/3] Finalizado. Revisa la carpeta \dist\" -ForegroundColor Green
Write-Host "Tip: El contenido de dist\$appName es portable y listo para distribuir." -ForegroundColor Cyan
