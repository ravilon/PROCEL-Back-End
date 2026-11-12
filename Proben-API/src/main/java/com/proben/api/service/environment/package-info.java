/**
 * Resolucao e execucao de regras ambientais por campus ou compartimento.
 *
 * <p>Execucoes agendadas e manuais sao auditadas no PostgreSQL, usam chave de
 * idempotencia e podem ser retomadas por lease e retry. A resolucao de regra
 * considera escopos especificos e regras padrao.</p>
 */
package com.proben.api.service.environment;
