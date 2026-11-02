/**
 * 03_conector_rpa_eps.groovy
 * -----------------------------------------------------------------------------
 * Ubicación en Bonita Studio:
 *   Service Task: Task_VerificarEPS ("Verificar paciente en portal EPS (RPA)")
 *   Conector de Entrada (Connectors In): REST POST Connector -> Webhook Bot RPA
 *
 * Justificación arquitectónica:
 *   El portal de la EPS no cuenta con API pública. Bonita invoca vía webhook al
 *   Bot RPA (Playwright / UiPath / Python), el cual navega el portal de la EPS
 *   por pantalla y retorna el estado de afiliación y autorización vigente.
 */
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// =============================================================================
// PARTE A: Payload JSON enviado al Bot RPA (POST /api/v1/rpa/verificar-eps)
// =============================================================================
def construirPayloadRPA = { solicitudCita ->
    def p = solicitudCita.paciente
    return JsonOutput.toJson([
        radicado        : solicitudCita.codigoRadicado,
        tipoDocumento   : p.tipoDocumento,
        numeroDocumento : p.numeroDocumento,
        eps             : p.eps,
        especialidad    : solicitudCita.especialidadRequerida
    ])
}

// =============================================================================
// PARTE B: Output Operation de Task_VerificarEPS (Procesa bodyAsString del RPA)
// Target: solicitudCita | Operator: Takes value of (Script)
// =============================================================================
def rpaResp = new JsonSlurper().parseText(bodyAsString)

def p = solicitudCita.paciente
p.activoEPS          = Boolean.valueOf(rpaResp.activoEPS as boolean)
p.regimenAfiliacion  = rpaResp.regimen ?: p.regimenAfiliacion

// Medicina general u odontología básica pueden no requerir orden previa según convenio;
// el bot RPA retorna autorizacionVigente = true si aplica cobertura directa o tiene orden activa.
solicitudCita.autorizacionVigente   = Boolean.valueOf(rpaResp.autorizacionVigente as boolean)
solicitudCita.codigoAutorizacionEPS = rpaResp.codigoAutorizacion?.toString()

if (p.activoEPS && solicitudCita.autorizacionVigente) {
    solicitudCita.estadoSolicitud   = "VERIFICADA_EPS"
    solicitudCita.requiereExcepcion = false
    solicitudCita.motivoExcepcion   = null
} else {
    solicitudCita.estadoSolicitud   = "EN_EXCEPCION"
    solicitudCita.requiereExcepcion = true
    def detalle = !p.activoEPS ? "AFILIACION_INACTIVA_EPS" : "SIN_AUTORIZACION_VIGENTE_EPS"
    solicitudCita.motivoExcepcion   = "${detalle} (${p.eps})"
}

return solicitudCita
