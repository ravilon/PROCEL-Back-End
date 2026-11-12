/**
 * Casos de uso de agregacao e consulta analitica.
 *
 * <p>Jobs particionam intervalos deterministas em janelas persistentes; workers
 * usam claim, lease e retry. Buckets numericos sao resultados versionados e as
 * consultas de summary consolidam somente buckets persistidos.</p>
 */
package com.proben.api.service.analytics;
