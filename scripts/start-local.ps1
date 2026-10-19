$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$postgresBin = Join-Path $env:ProgramFiles 'PostgreSQL\17\bin'
$clusterDirectory = Join-Path $env:LOCALAPPDATA 'API Sentinel\postgres-data'
$pgCtl = Join-Path $postgresBin 'pg_ctl.exe'
$initDb = Join-Path $postgresBin 'initdb.exe'
$psql = Join-Path $postgresBin 'psql.exe'

if (-not (Test-Path $pgCtl) -or -not (Test-Path $initDb) -or -not (Test-Path $psql)) {
    throw 'PostgreSQL 17 is required. Install it or use Docker Compose instead.'
}

if (-not (Test-Path (Join-Path $clusterDirectory 'PG_VERSION'))) {
    New-Item -ItemType Directory -Force -Path $clusterDirectory | Out-Null
    & $initDb -D $clusterDirectory -U sentinel --auth-local=trust --auth-host=trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the local PostgreSQL cluster.' }
}

& $pgCtl -D $clusterDirectory status *> $null
if ($LASTEXITCODE -ne 0) {
    & $pgCtl -D $clusterDirectory -l (Join-Path $clusterDirectory 'server.log') -o '-p 5433 -h 127.0.0.1' -w start
    if ($LASTEXITCODE -ne 0) { throw 'Could not start local PostgreSQL on port 5433.' }
}

$databaseExists = & $psql -h 127.0.0.1 -p 5433 -U sentinel -d postgres -t -A -c "SELECT 1 FROM pg_database WHERE datname = 'sentinel'"
if ($LASTEXITCODE -ne 0) { throw 'Could not connect to local PostgreSQL on port 5433.' }
if ($databaseExists.Trim() -ne '1') {
    & (Join-Path $postgresBin 'createdb.exe') -h 127.0.0.1 -p 5433 -U sentinel sentinel
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the sentinel database.' }
}

$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5433/sentinel'
$env:DATABASE_USERNAME = 'sentinel'
$env:DATABASE_PASSWORD = ''
$env:PORT = '8082'
if ([string]::IsNullOrWhiteSpace($env:SMTP_HOST)) {
    Write-Warning 'SMTP_HOST is unset. The API will run, but email OTP delivery requires an SMTP server.'
}

Push-Location $repositoryRoot
try {
    & mvn -f (Join-Path $repositoryRoot 'backend\pom.xml') spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'The API backend stopped with an error.' }
} finally {
    Pop-Location
}