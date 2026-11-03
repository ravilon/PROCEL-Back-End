$ErrorActionPreference = "Stop"

function Assert-FileContains {
  param(
    [string] $Path,
    [string] $Pattern,
    [string] $Message
  )
  $content = Get-Content -Raw -Path $Path
  if ($content -notmatch $Pattern) {
    throw $Message
  }
}

function Assert-FileNotContains {
  param(
    [string] $Path,
    [string] $Pattern,
    [string] $Message
  )
  $content = Get-Content -Raw -Path $Path
  if ($content -match $Pattern) {
    throw $Message
  }
}

Assert-FileContains "compose.yaml" "context:\s+\./Proben-API" "Root compose must build Proben-API from its own context."
Assert-FileContains "compose.yaml" "context:\s+\./Proben-Telemetry" "Root compose must build Proben-Telemetry from its own context."
Assert-FileContains "compose.yaml" "context:\s+\./Proben-Admin" "Root compose must build Proben-Admin from its own context."
Assert-FileContains "Proben-Admin/docker-entrypoint.d/40-runtime-config.sh" "TELEMETRY_API_URL" "Admin entrypoint must inject TELEMETRY_API_URL."
Assert-FileContains "Proben-Admin/public/config.js" "TELEMETRY_API_URL" "Admin public runtime config must expose TELEMETRY_API_URL."
Assert-FileContains "Proben-Telemetry/src/main/resources/application.yml" 'PROBEN_TELEMETRY_MQTT_ENABLED:false' "Telemetry MQTT must be disabled by default."
Assert-FileContains "Proben-Telemetry/src/main/resources/application.yml" 'PROBEN_TELEMETRY_CANONICAL_WORKER_ENABLED:false' "Telemetry canonical worker must be disabled by default."
Assert-FileContains "Proben-API/src/main/resources/application.yml" 'PROBEN_ANALYTICS_AGGREGATION_WORKER_ENABLED:false' "API analytics worker must be disabled by default."
Assert-FileNotContains "Proben-API/src/main/resources/application.yml" "Proben-Telemetry|MongoDB|MQTT|proben-telemetry|mongo|mqtt" "API application config must not depend on Telemetry, MongoDB or MQTT."
Assert-FileContains "Proben-API/Dockerfile" "HEALTHCHECK" "Proben-API Dockerfile must declare a healthcheck."
Assert-FileContains "Proben-Telemetry/Dockerfile" "HEALTHCHECK" "Proben-Telemetry Dockerfile must declare a healthcheck."
Assert-FileContains "Proben-Admin/Dockerfile" "HEALTHCHECK" "Proben-Admin Dockerfile must declare a healthcheck."

Write-Host "Deploy configuration checks passed."
