# PROCEL API Smoke Test Report

- Status: PASSED
- Target: prod
- Base URL: https://procel.servehttp.com
- HTTP transport: Node
- Started: 2026-09-17T21:24:14.0473501-03:00
- Finished: 2026-09-17T21:25:25.6549622-03:00
- Duration: 71.6 seconds
- Passed: 112
- Warnings: 5
- Failed: 0

## Steps

| Status | Step | Duration | Detail |
| --- | --- | ---: | --- |
| OK | POST /api/auth/login (admin bootstrap) | 482 ms |  |
| WARN | POST /api/rooms/sync | 45197 ms | Node HTTP transport failed: AbortError This operation was aborted |
| OK | POST /api/rooms/aulas/sync (all rooms, async) | 343 ms |  |
| OK | Classroom schedules sync returned a job | 0 ms |  |
| WARN | GET /api/rooms/aulas/sync/7885448a-28a0-40d9-9147-e8df232cf6b0 | 317 ms | HTTP_STATUS:401 {"message":"Token ausente ou invalido","error":"UNAUTHORIZED","timestamp":1789691100.787396436} |
| WARN | Classroom schedules job persisted | 0 ms | skipped because job status endpoint failed |
| OK | POST /api/sensors/seed/from-resource | 318 ms |  |
| OK | GET configured sensor including hidden | 430 ms |  |
| OK | Configured sensor belongs to expected room and type | 0 ms |  |
| OK | Configured smoke sensor is ready | 0 ms |  |
| OK | DELETE /api/sensor-admin/sensors/SII-002 (soft delete) | 390 ms |  |
| OK | GET room sensors without hidden sensor | 308 ms |  |
| OK | Soft-deleted sensor is hidden by default | 0 ms |  |
| OK | GET room sensors including hidden sensor | 340 ms |  |
| OK | Soft-deleted sensor is available as hidden | 0 ms |  |
| OK | POST /api/sensor-admin/sensors/SII-002/restore | 390 ms |  |
| OK | Sensor restore returns active sensor | 0 ms |  |
| WARN | POST /api/pessoas (create test user - may conflict) | 316 ms | HTTP_STATUS:409 {"message":"userId already in use: api-test-user","error":"CONFLICT","timestamp":"2026-09-18T00:25:03.344183038Z"} |
| OK | GET /api/pessoas/api-test-user | 303 ms |  |
| OK | PUT /api/pessoas/api-test-user (update) | 323 ms |  |
| OK | POST /api/auth/login (test user) | 419 ms |  |
| OK | POST /api/pessoas (disposable delete test) | 369 ms |  |
| OK | Disposable user was created | 0 ms |  |
| OK | DELETE /api/pessoas/api-delete-31f382bc | 307 ms |  |
| OK | GET deleted disposable user returns not found | 305 ms | failed as expected (HTTP 404) |
| OK | POST /api/missoes | 354 ms |  |
| OK | POST /api/missoes returns active mission | 0 ms |  |
| OK | POST /api/missoes returns Individual type | 0 ms |  |
| OK | POST /api/missoes returns numeric XP value | 0 ms |  |
| OK | POST /api/missoes (inactive) | 308 ms |  |
| OK | POST /api/missoes inactive returns Individual type | 0 ms |  |
| OK | POST /api/missoes inactive returns numeric XP value | 0 ms |  |
| OK | GET /api/missoes?ativo=true | 365 ms |  |
| OK | GET /api/missoes?ativo=true includes created mission | 0 ms |  |
| OK | GET /api/missoes?ativo=false | 327 ms |  |
| OK | GET /api/missoes?ativo=false includes inactive mission | 0 ms |  |
| OK | GET /api/missoes | 316 ms |  |
| OK | GET /api/missoes includes active and inactive missions | 0 ms |  |
| OK | GET /api/missoes/8dafbc1e-2499-4354-9cf1-a9245be4fa9e | 295 ms |  |
| OK | GET /api/missoes/{missaoId} returns requested mission | 0 ms |  |
| OK | GET /api/missoes/{missaoId} returns Individual type | 0 ms |  |
| OK | GET /api/missoes/{missaoId} returns numeric XP value | 0 ms |  |
| OK | PUT /api/missoes/8dafbc1e-2499-4354-9cf1-a9245be4fa9e | 305 ms |  |
| OK | PUT /api/missoes/{missaoId} updates title | 0 ms |  |
| OK | PUT /api/missoes/{missaoId} keeps Individual type | 0 ms |  |
| OK | PUT /api/missoes/{missaoId} updates numeric XP value | 0 ms |  |
| OK | POST /api/pessoas/api-test-user/atividades rejects inactive missao | 314 ms | failed as expected (HTTP 409) |
| OK | POST /api/pessoas/api-test-user/atividades | 344 ms |  |
| OK | POST /api/pessoas/{pessoaId}/atividades returns PENDENTE activity | 0 ms |  |
| OK | POST /api/pessoas/api-test-user/atividades rejects duplicate missao | 312 ms | failed as expected (HTTP 409) |
| OK | GET /api/pessoas/api-test-user/atividades | 312 ms |  |
| OK | GET /api/pessoas/{pessoaId}/atividades includes created activity | 0 ms |  |
| OK | GET /api/pessoas/api-test-user/atividades?status=PENDENTE | 315 ms |  |
| OK | GET /api/pessoas/{pessoaId}/atividades?status=PENDENTE includes created activity | 0 ms |  |
| OK | GET /api/pessoas/api-test-user/atividades/e8a67d76-3ac6-4be3-bd1f-53ec0f29331d | 310 ms |  |
| OK | GET /api/pessoas/{pessoaId}/atividades/{atividadeId} returns requested activity | 0 ms |  |
| OK | GET /api/pessoas/api-test-user/atividades as own USUARIO | 322 ms |  |
| OK | USUARIO can list own atividades | 0 ms |  |
| OK | GET /api/pessoas/api-test-user/atividades/e8a67d76-3ac6-4be3-bd1f-53ec0f29331d as own USUARIO | 280 ms |  |
| OK | USUARIO can get own atividade | 0 ms |  |
| OK | GET /api/pessoas/api-test-user/atividades/resumo as own USUARIO | 345 ms |  |
| OK | GET /api/pessoas/admin/atividades rejects another pessoa for USUARIO | 308 ms | failed as expected (HTTP 403) |
| OK | PUT /api/pessoas/api-test-user/atividades/e8a67d76-3ac6-4be3-bd1f-53ec0f29331d (EM_ANDAMENTO) | 309 ms |  |
| OK | PUT atividade sets EM_ANDAMENTO | 0 ms |  |
| OK | PUT /api/pessoas/api-test-user/atividades/e8a67d76-3ac6-4be3-bd1f-53ec0f29331d | 299 ms |  |
| OK | PUT atividade sets CONCLUIDA | 0 ms |  |
| OK | GET /api/pessoas/api-test-user/atividades?status=CONCLUIDA | 333 ms |  |
| OK | GET /api/pessoas/{pessoaId}/atividades?status=CONCLUIDA includes completed activity | 0 ms |  |
| OK | POST /api/missoes (expirable activity) | 290 ms |  |
| OK | POST /api/pessoas/api-test-user/atividades (to expire) | 307 ms |  |
| OK | DELETE /api/pessoas/api-test-user/atividades/defd326a-a578-4200-8d96-d7ca495c57d0 (expires) | 316 ms |  |
| OK | GET expired atividade still exists | 314 ms |  |
| OK | DELETE atividade marks EXPIRADA | 0 ms |  |
| OK | GET /api/pessoas/api-test-user/atividades?status=EXPIRADA | 292 ms |  |
| OK | GET /api/pessoas/{pessoaId}/atividades?status=EXPIRADA includes expired activity | 0 ms |  |
| OK | GET /api/pessoas/api-test-user/atividades/resumo | 274 ms |  |
| OK | Atividades resumo counts CONCLUIDA and EXPIRADA | 0 ms |  |
| OK | DELETE /api/missoes/e80114e1-c1a1-47bb-ab6f-60c919b49d2d | 289 ms |  |
| OK | GET deprecated inactive missao still exists | 305 ms |  |
| OK | DELETE inactive missao keeps row and sets ativo=false | 0 ms |  |
| OK | DELETE /api/missoes/efe5d91a-18c9-46a9-ac0e-f5700d9862f9 | 363 ms |  |
| OK | GET deprecated expirable missao still exists | 316 ms |  |
| OK | DELETE expirable missao keeps row and sets ativo=false | 0 ms |  |
| OK | DELETE /api/missoes/8dafbc1e-2499-4354-9cf1-a9245be4fa9e | 310 ms |  |
| OK | GET deprecated missao still exists | 272 ms |  |
| OK | DELETE missao keeps row and sets ativo=false | 0 ms |  |
| WARN | POST /api/presencas/checkout/by-pessoa (cleanup from previous run) | 290 ms | HTTP_STATUS:404 {"message":"No open presence session for pessoaId=api-test-user","error":"NOT_FOUND","timestamp":"2026-09-18T00:25:15.944844020Z"} |
| OK | POST /api/presencas/checkin | 326 ms |  |
| OK | GET /api/rules/parameter-defs?tipoNome=SII_SMART | 316 ms |  |
| OK | DELETE /api/sensor-admin/parameters/1a6e7046-92f5-482b-9f6a-0da2ae52754f (soft delete) | 304 ms |  |
| OK | GET sensor types without hidden parameters | 316 ms |  |
| OK | Soft-deleted parameter is hidden by default | 0 ms |  |
| OK | GET sensor types including hidden parameters | 305 ms |  |
| OK | Soft-deleted parameter is available as hidden | 0 ms |  |
| OK | POST /api/sensor-admin/parameters/1a6e7046-92f5-482b-9f6a-0da2ae52754f/restore | 301 ms |  |
| OK | Parameter restore returns active parameter | 0 ms |  |
| OK | GET /api/rules/groups | 286 ms |  |
| OK | GET /api/rules/groups/eab28697-4d4a-455b-b8d5-cde5b3792757/rules | 289 ms |  |
| OK | PUT /api/rules/groups/eab28697-4d4a-455b-b8d5-cde5b3792757/rules/bbf56c5c-afd0-45e0-ae1e-489f1b66a447 | 304 ms |  |
| OK | Rule update keeps id and changes name | 0 ms |  |
| OK | DELETE /api/rules/groups/eab28697-4d4a-455b-b8d5-cde5b3792757/rules/bbf56c5c-afd0-45e0-ae1e-489f1b66a447 | 290 ms |  |
| OK | GET rules after logical delete | 314 ms |  |
| OK | Rule delete preserves inactive rule | 0 ms |  |
| OK | PUT rule to reactivate it | 297 ms |  |
| OK | Rule can be reactivated by update | 0 ms |  |
| OK | GET /api/rules/sensors/SII-002/groups | 291 ms |  |
| OK | POST /api/sensors/ingest/mock | 1938 ms |  |
| OK | GET /api/sensors/SII-002/medicoes/latest | 330 ms |  |
| OK | GET /api/sensors/SII-002/medicoes?from&to&limit | 500 ms |  |
| OK | Sensor measurement list includes mock ingest | 0 ms |  |
| OK | GET /api/rooms/393/medicoes/latest | 343 ms |  |
| OK | GET /api/rooms/393/medicoes?from&to&limit | 386 ms |  |
| OK | Room measurement list includes mock ingest | 0 ms |  |
| OK | GET /api/presencas/ocupacao/compartimentos/393 | 296 ms |  |
| OK | GET /api/presencas/abertas/compartimentos/393 | 311 ms |  |
| OK | POST /api/presencas/checkout/by-pessoa (optional) | 328 ms |  |
| OK | GET /api/presencas/ocupacao/compartimentos/393 (after checkout) | 305 ms |  |
