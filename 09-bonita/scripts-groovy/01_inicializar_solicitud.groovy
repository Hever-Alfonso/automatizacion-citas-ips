/**
 * 01_inicializar_solicitud.groovy
 * -----------------------------------------------------------------------------
 * Ubicación en Bonita Studio:
 *   Pool (Process_AgendamientoCitasIPS) -> Data -> Business Variables ->
 *   Variable: solicitudCita (Tipo: co.edu.eafit.ips.model.SolicitudCita) -> Default Value
 *
 * Propósito:
 *   Inicializa la SolicitudCita a partir del Contrato de Instanciación (solicitudInput),
 *   verifica el consentimiento obligatorio de Habeas Data (Ley 1581 de 2012) y genera
 *   un número único de radicado trazable.
 */
import co.edu.eafit.ips.model.SolicitudCita
import co.edu.eafit.ips.model.Paciente
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

if (!solicitudInput?.autorizaTratamientoDatos) {
    throw new IllegalArgumentException(
        "Cumplimiento Ley 1581 de 2012: No se puede procesar la solicitud sin autorización expresa de tratamiento de datos de salud."
    )
}

def ahora = OffsetDateTime.now(ZoneOffset.ofHours(-5)) // Zona horaria Colombia (UTC-5)
def sufijo = String.format("%04d", (Math.abs(UUID.randomUUID().hashCode()) % 10000))
def radicado = "IPS-${ahora.format(DateTimeFormatter.ofPattern('yyyyMMdd'))}-${sufijo}"

// Buscar si el paciente ya existe en el BDM cuando el canal provee documento inicial
Paciente pacienteRef = null
if (solicitudInput?.tipoDocumento && solicitudInput?.numeroDocumento) {
    pacienteRef = pacienteDAO.findByDocumento(
        solicitudInput.tipoDocumento.trim().toUpperCase(),
        solicitudInput.numeroDocumento.trim()
    )
}

if (pacienteRef == null) {
    pacienteRef = new Paciente()
    pacienteRef.tipoDocumento = solicitudInput?.tipoDocumento ?: "PENDIENTE"
    pacienteRef.numeroDocumento = solicitudInput?.numeroDocumento ?: "TMP-${sufijo}"
    pacienteRef.nombreCompleto = solicitudInput?.nombreContacto ?: "Por identificar (IA)"
    pacienteRef.telefono = solicitudInput?.telefonoContacto ?: "No registrado"
    pacienteRef.correoElectronico = solicitudInput?.correoContacto
    pacienteRef.eps = solicitudInput?.epsInformada ?: "POR_EXTRAER"
    pacienteRef.activoEPS = false
    pacienteRef.autorizaTratamientoDatos = true
    pacienteRef.fechaConsentimiento = ahora
}

def solicitud = new SolicitudCita()
solicitud.codigoRadicado = radicado
solicitud.canalOrigen = (solicitudInput?.canalOrigen ?: "WHATSAPP").toUpperCase()
solicitud.mensajeOriginal = solicitudInput?.mensajeOriginal?.trim() ?: ""
solicitud.datosCompletos = false
solicitud.requiereExcepcion = false
solicitud.estadoSolicitud = "RECIBIDA"
solicitud.fechaRecepcion = ahora
solicitud.paciente = pacienteRef

return solicitud
