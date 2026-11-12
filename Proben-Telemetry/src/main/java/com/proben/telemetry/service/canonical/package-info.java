/**
 * Entrega de eventos brutos elegiveis para ingestao canonica na Proben-API.
 *
 * <p>O worker coordena claim atomico, lease, selecao de perfil e retry. Erros
 * permanentes e transitorios produzem estados distintos no documento bruto.</p>
 */
package com.proben.telemetry.service.canonical;
