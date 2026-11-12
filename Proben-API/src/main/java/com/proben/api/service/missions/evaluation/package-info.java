/**
 * Processadores e workers de avaliacao instantanea e temporal de eventos.
 *
 * <p>Janelas e requests persistidas permitem retomar processamento depois de
 * falhas. Lease, retry e evidencias evitam tratar uma repeticao de worker como
 * uma nova ocorrencia logica.</p>
 */
package com.proben.api.service.missions.evaluation;
