/**
 * 04_condiciones_gateways.groovy
 * -----------------------------------------------------------------------------
 * Catálogo completo de expresiones Groovy para las transiciones condicionales
 * de los Exclusive Gateways (XOR-Split) y el evento temporizador en Bonita Studio.
 *
 * Nota: Las transiciones marcadas como Default Flow en el modelo BPMN no llevan
 * expresión en Bonita (actúan automáticamente como rama "Sino / Else").
 */

// 1. Gateway: GW_DatosIA ("¿Datos completos?")
//    - Flujo F_Datos_Si ("Sí" -> Task_VerificarEPS):
solicitudCita.datosCompletos == true && solicitudCita.confianzaIA >= 0.80d
//    - Flujo F_Datos_No ("No" -> GW_JoinExcepcion): [Default Flow]


// 2. Gateway: GW_EPS ("¿Activo y autorizado?")
//    - Flujo F_EPS_Si ("Sí" -> GW_JoinBusqueda):
solicitudCita.paciente.activoEPS == true && solicitudCita.autorizacionVigente == true
//    - Flujo F_EPS_No ("No" -> GW_JoinExcepcion): [Default Flow]


// 3. Gateway: GW_Espacio ("¿Hay cupo?")
//    - Flujo F_Espacio_Si ("Sí" -> Task_Registrar):
cupoDisponible == true
//    - Flujo F_Espacio_No ("No" -> GW_JoinExcepcion): [Default Flow]


// 4. Gateway: GW_ResolucionAuxiliar ("¿Agendada?")
//    - Flujo F_Aux_Agendada ("Sí" -> GW_JoinConfirmar):
citaMedica != null && citaMedica.estado == "AGENDADA"
//    - Flujo F_Aux_Cerrado ("No" -> End_Excepcion): [Default Flow]


// 5. Evento Intermedio Temporizador: Timer_24hAntes ("24 h antes de la cita")
//    Tipo de condición en Bonita: Fixed Date / Script que retorna java.util.Date
//    (Si faltan menos de 24 h para la cita urgente, dispara en 1 minuto para no bloquear el caso)
def instanteRecordatorio = citaMedica.fechaHoraCita.minusHours(24).toInstant()
def ahoraMasUnMin = java.time.Instant.now().plusSeconds(60)
return java.util.Date.from(instanteRecordatorio.isAfter(ahoraMasUnMin) ? instanteRecordatorio : ahoraMasUnMin)


// 6. Gateway: GW_Respuesta ("¿Respuesta?")
//    - Flujo F_Resp_Cancela ("Cancela" -> Task_Cancelar):
respuestaRecordatorio == "CANCELAR"
//    - Flujo F_Resp_Reprograma ("Reprograma" -> GW_JoinBusqueda):
respuestaRecordatorio == "REPROGRAMAR"
//    - Flujo F_Resp_Confirma ("Confirma" -> End_Confirmada): [Default Flow]
