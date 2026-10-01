# Gera dist\FocaEstudo-Windows.zip: FocaEstudo.exe + Java embutido (quem baixa não precisa instalar nada).
# Requer JDK 17+ (com jpackage) no PATH.  Uso:  powershell -ExecutionPolicy Bypass -File empacotamento\empacotar.ps1 [-Versao 1.0.1]
param([string]$Versao = "1.0.0")

$ErrorActionPreference = "Stop"
$raiz  = Split-Path -Parent $PSScriptRoot
$build = Join-Path $raiz "dist\build"
$saida = Join-Path $raiz "dist\FocaEstudo-Windows.zip"

function Executar($exe, [string[]]$argumentos) {
    & $exe @argumentos
    if ($LASTEXITCODE -ne 0) { throw "$exe falhou (código $LASTEXITCODE)" }
}

if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force "$build\classes", "$build\input" | Out-Null

Write-Host "Compilando..."
$fontes = Get-ChildItem "$raiz\src\*.java" | ForEach-Object { $_.FullName }
Executar javac (@("-encoding", "UTF-8", "--release", "17", "-Xlint:none", "-d", "$build\classes") + $fontes)

Write-Host "Gerando .jar e ícone..."
Set-Content "$build\manifest.txt" "Main-Class: StudyTracker" -Encoding ascii
Executar jar @("cfm", "$build\input\FocaEstudo.jar", "$build\manifest.txt", "-C", "$build\classes", ".")
Executar java @("-cp", "$build\classes", "$PSScriptRoot\MakeIco.java", "$build\FocaEstudo.ico")

Write-Host "Empacotando com Java embutido (jpackage)..."
Executar jpackage @(
    "--type", "app-image", "--name", "FocaEstudo", "--app-version", $Versao,
    "--vendor", "Erick Draber", "--description", "Rastreador de estudos",
    "--input", "$build\input", "--main-jar", "FocaEstudo.jar", "--main-class", "StudyTracker",
    "--icon", "$build\FocaEstudo.ico",
    "--add-modules", "java.base,java.desktop,jdk.localedata",
    "--jlink-options", "--strip-native-commands --strip-debug --no-man-pages --no-header-files --include-locales=en,pt",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--dest", "$build\out")
Copy-Item "$PSScriptRoot\LEIA-ME.txt" "$build\out\FocaEstudo\"

Write-Host "Compactando..."
if (Test-Path $saida) { Remove-Item $saida -Force }
# tar do Windows gera zip com "/" nos caminhos (Compress-Archive do PS 5.1 usa "\")
Executar "$env:SystemRoot\System32\tar.exe" @("-a", "-c", "-f", $saida, "-C", "$build\out", "FocaEstudo")

Remove-Item $build -Recurse -Force
Write-Host "Pronto: $saida ($([math]::Round((Get-Item $saida).Length / 1MB, 1)) MB)"
