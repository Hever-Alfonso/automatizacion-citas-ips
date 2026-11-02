/**
 * 02_conector_ia_extraccion.groovy
 * -----------------------------------------------------------------------------
 * Ubicación en Bonita Studio:
 *   Service Task: Task_IA ("Extraer datos de solicitud (IA)")
 *   Conector de Entrada (Connectors In): REST POST Connector -> LLM / Microservicio IA
 *
 * Incluye:
 *   1. Script de construcción del Body JSON para el Conector REST.
 *   2. Script de Operación de Salida (Output Operation) que actualiza solicitudCita y paciente.
 */
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// =============================================================================
// PARTE A: Payload Body del Conector REST (POST /api/v1/ia/extraer-solicitud)
// =============================================================================
def construirPayloadIA = { solicitudCita ->
    def payload = [
        radicado       : solicitudCita.codigoRadicado,
        canal          : solicitudCita.canalOrigen,
        mensajePaciente: solicitudCita.mensajeOriginal,
        esquemaSalida  : [
            "tipoDocumento",
            "numeroDocumento",
            "nombreCompleto",
            "eps",
            "especialidadRequerida",
            "nivelUrgencia",
            "jornadaPreferida",
            "confianza"
        ]
    ]
    return JsonOutput.toJson(payload)
}

// =============================================================================
// PARTE B: Script en Output Operation de Task_IA (Procesa bodyAsString del REST)
// Target: solicitudCita | Operator: Takes value of (Script)
// =============================================================================
def json = new JsonSlurper().parseText(bodyAsString)

def pac = solicitudCita.paciente
if (json.tipoDocumento)   pac.tipoDocumento   = json.tipoDocumento.toString().toUpperCase()
if (json.numeroDocumento) pac.numeroDocumento = json.numeroDocumento.toString().trim()
if (json.nombreCompleto)  pac.nombreCompleto  = json.nombreCompleto.toString().trim()
if (json.eps)             pac.eps             = json.eps.toString().trim().toUpperCase()

solicitudCita.especialidadRequerida = json.especialidadRequerida?.toString()?.toUpperCase()
solicitudCita.nivelUrgencia         = (json.nivelUrgencia ?: "MEDIA").toString().toUpperCase()
solicitudCita.jornadaPreferida      = (json.jornadaPreferida ?: "CUALQUIERA").toString().toUpperCase()
solicitudCita.confianzaIA           = (json.confianza != null) ? (json.confianza as Double) : 0.0d

boolean camposMinimos = (
    pac.tipoDocumento && pac.tipoDocumento != "PENDIENTE" &&
    pac.numeroDocumento && !pac.numeroDocumento.startsWith("TMP-") &&
    pac.eps && pac.eps != "POR_EXTRAER" &&
    solicitudCita.especialidadRequerida
)

solicitudCita.datosCompletos = camposMinimos
if (camposMinimos && solicitudCita.confianzaIA >= 0.80d) {
    solicitudCita.estadoSolicitud = "VALIDADA_IA"
    solicitudCita.requiereExcepcion = false
    solicitudCita.motivoExcepcion = null
} else {
    solicitudCita.estadoSolicitud = "EN_EXCEPCION"
    solicitudCita.requiereExcepcion = true
    solicitudCita.motivoExcepcion = "DATOS_INCOMPLETOS_O_BAJA_CONFIANZA_IA (confianza=${solicitudCita.confianzaIA})"
}

return solicitudCita
