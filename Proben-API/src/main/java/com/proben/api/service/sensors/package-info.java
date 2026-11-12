/**
 * Ingestao canonica de medicoes e resolucao de integracoes de sensores.
 *
 * <p>A ingestao preserva identidade idempotente e separa o payload externo de
 * valores canonicos validados. Rotas internas de Telemetry mantem metadados da
 * origem bruta sem conceder acesso direto ao PostgreSQL ao servico chamador.</p>
 */
package com.proben.api.service.sensors;
