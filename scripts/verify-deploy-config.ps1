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

Assert-FileContains "compose.yaml" "context:\s+\./Procel-API" "Root compose must build Procel-API from its own context."
Assert-FileContains "compose.yaml" "context:\s+\./Procel-Telemetry" "Root compose must build Procel-Telemetry from its own context."
Assert-FileContains "compose.yaml" "context:\s+\./Procel-Admin" "Root compose must build Procel-Admin from its own context."
Assert-FileContains "Procel-Admin/docker-entrypoint.d/40-runtime-config.sh" "TELEMETRY_API_URL" "Admin entrypoint must inject TELEMETRY_API_URL."
Assert-FileContains "Procel-Admin/public/config.js" "TELEMETRY_API_URL" "Admin public runtime config must expose TELEMETRY_API_URL."
Assert-FileContains "Procel-Telemetry/src/main/resources/application.yml" 'PROCEL_TELEMETRY_MQTT_ENABLED:false' "Telemetry MQTT must be disabled by default."
Assert-FileContains "Procel-Telemetry/src/main/resources/application.yml" 'PROCEL_TELEMETRY_CANONICAL_WORKER_ENABLED:false' "Telemetry canonical worker must be disabled by default."
Assert-FileContains "Procel-API/src/main/resources/application.yml" 'PROCEL_ANALYTICS_AGGREGATION_WORKER_ENABLED:false' "API analytics worker must be disabled by default."
Assert-FileNotContains "Procel-API/src/main/resources/application.yml" "Procel-Telemetry|MongoDB|MQTT|procel-telemetry|mongo|mqtt" "API application config must not depend on Telemetry, MongoDB or MQTT."
Assert-FileContains "Procel-API/Dockerfile" "HEALTHCHECK" "Procel-API Dockerfile must declare a healthcheck."
Assert-FileContains "Procel-Telemetry/Dockerfile" "HEALTHCHECK" "Procel-Telemetry Dockerfile must declare a healthcheck."
Assert-FileContains "Procel-Admin/Dockerfile" "HEALTHCHECK" "Procel-Admin Dockerfile must declare a healthcheck."

Write-Host "Deploy configuration checks passed."
