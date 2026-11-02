/**
 * 05_gestion_agenda_y_notificaciones.groovy
 * -----------------------------------------------------------------------------
 * Scripts de Operaciones (Operations) para gestión de cupos, prevención de citas
 * duplicadas/cruzadas, atención manual por el Auxiliar y cancelación con liberación.
 */
import co.edu.eafit.ips.model.CitaMedica
import co.edu.eafit.ips.model.CupoAgenda
import java.time.OffsetDateTime
import java.time.ZoneOffset

// =============================================================================
// 1. Task_BuscarEspacio: Buscar cupo libre evitando citas duplicadas o cruzadas
//    Variables de proceso actualizadas:
//      - cupoSeleccionado (BDM CupoAgenda)
//      - cupoDisponible (Boolean Process Variable)
// =============================================================================
def buscarCupoAgenda = { solicitudCita, citaExistente ->
    // Si viene de reprogramación, liberar primero el cupo anterior
    if (citaExistente != null && citaExistente.cupoAsignado != null) {
        citaExistente.cupoAsignado.disponible = true
        citaExistente.estado = "REPROGRAMADA"
        citaExistente.fechaActualizacion = OffsetDateTime.now(ZoneOffset.ofHours(-5))
    }

    // Consultar citas activas del paciente para impedir cruce horario o duplicidad de especialidad
    def citasActivas = citaMedicaDAO.findCitasActivasPorPaciente(solicitudCita.paciente, 0, 50)
    def horasOcupadas = citasActivas.collect { it.fechaHoraCita }.toSet()

    def candidatos = cupoAgendaDAO.findDisponiblesPorEspecialidad(
        solicitudCita.especialidadRequerida, 0, 20
    )

    CupoAgenda elegido = candidatos.find { c -> !horasOcupadas.contains(c.fechaHoraInicio) }
    if (elegido == null) {
        solicitudCita.requiereExcepcion = true
        solicitudCita.estadoSolicitud   = "EN_EXCEPCION"
        solicitudCita.motivoExcepcion   = "SIN_CUPO_AGENDA (${solicitudCita.especialidadRequerida})"
    }
    return elegido // cupoDisponible = (elegido != null)
}


// =============================================================================
// 2. Task_Registrar: Crear o actualizar CitaMedica y bloquear el CupoAgenda
//    Target: citaMedica (BDM CitaMedica)
// =============================================================================
def registrarCitaEnBDM = { solicitudCita, cupoSeleccionado, citaExistente ->
    def ahora = OffsetDateTime.now(ZoneOffset.ofHours(-5))
    cupoSeleccionado.disponible = false

    CitaMedica cita = (citaExistente != null) ? citaExistente : new CitaMedica()
    if (!cita.codigoCita) {
        cita.codigoCita = "CITA-${solicitudCita.codigoRadicado.replace('IPS-', '')}"
    }
    cita.fechaHoraCita       = cupoSeleccionado.fechaHoraInicio
    cita.especialidad        = cupoSeleccionado.especialidad
    cita.nombreMedico        = cupoSeleccionado.nombreMedico
    cita.consultorio         = cupoSeleccionado.consultorio
    cita.sedeIPS             = cupoSeleccionado.sedeIPS
    cita.estado              = "AGENDADA"
    cita.asignadaPorAuxiliar = false
    cita.confirmacionEnviada = false
    cita.recordatorioEnviado = false
    cita.fechaRegistro       = cita.fechaRegistro ?: ahora
    cita.fechaActualizacion  = ahora
    cita.paciente            = solicitudCita.paciente
    cita.solicitudOrigen     = solicitudCita
    cita.cupoAsignado        = cupoSeleccionado

    solicitudCita.estadoSolicitud = "AGENDADA"
    return cita
}


// =============================================================================
// 3. Task_Excepcion (User Task): Operación al enviar formulario del Auxiliar
//    Procesa el contrato `resolucionAuxiliarInput` diligenciado en Bonita Portal
// =============================================================================
def procesarResolucionAuxiliar = { solicitudCita, resolucionAuxiliarInput ->
    def ahora = OffsetDateTime.now(ZoneOffset.ofHours(-5))
    solicitudCita.observacionesAuxiliar = resolucionAuxiliarInput.observaciones

    // Actualizar datos del paciente si el auxiliar los completó/corrigió con el paciente
    if (resolucionAuxiliarInput.numeroDocumentoCorregido) {
        solicitudCita.paciente.numeroDocumento = resolucionAuxiliarInput.numeroDocumentoCorregido.trim()
    }
    if (resolucionAuxiliarInput.codigoAutorizacionManual) {
        solicitudCita.codigoAutorizacionEPS = resolucionAuxiliarInput.codigoAutorizacionManual.trim()
        solicitudCita.autorizacionVigente   = true
    }

    if (resolucionAuxiliarInput.logroAgendar == true) {
        solicitudCita.estadoSolicitud = "AGENDADA"
        def citaManual = new CitaMedica()
        citaManual.codigoCita          = "CITA-MAN-${solicitudCita.codigoRadicado.replace('IPS-', '')}"
        citaManual.fechaHoraCita       = resolucionAuxiliarInput.fechaHoraAsignada
        citaManual.especialidad        = resolucionAuxiliarInput.especialidadAsignada
        citaManual.nombreMedico        = resolucionAuxiliarInput.medicoAsignado
        citaManual.consultorio         = resolucionAuxiliarInput.consultorioAsignado ?: "101"
        citaManual.sedeIPS             = resolucionAuxiliarInput.sedeAsignada ?: "Sede Principal"
        citaManual.estado              = "AGENDADA"
        citaManual.asignadaPorAuxiliar = true
        citaManual.confirmacionEnviada = false
        citaManual.recordatorioEnviado = false
        citaManual.fechaRegistro       = ahora
        citaManual.paciente            = solicitudCita.paciente
        citaManual.solicitudOrigen     = solicitudCita
        return citaManual
    } else {
        solicitudCita.estadoSolicitud = "RECHAZADA"
        return null
    }
}


// =============================================================================
// 4. Task_Cancelar: Cancelar cita y liberar inmediatamente el cupo en la agenda
//    Target: citaMedica (BDM CitaMedica)
// =============================================================================
def cancelarCitaYLiberarCupo = { citaMedica, solicitudCita ->
    def ahora = OffsetDateTime.now(ZoneOffset.ofHours(-5))
    if (citaMedica.cupoAsignado != null) {
        citaMedica.cupoAsignado.disponible = true
    }
    citaMedica.estado                = "CANCELADA"
    citaMedica.respuestaRecordatorio = "CANCELAR"
    citaMedica.fechaActualizacion    = ahora
    solicitudCita.estadoSolicitud    = "CANCELADA"
    return citaMedica
}
