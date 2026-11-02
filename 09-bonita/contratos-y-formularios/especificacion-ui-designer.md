# Especificación de Formularios – Bonita UI Designer

Esta guía detalla la configuración exacta de los **3 formularios** en el **UI Designer de Bonita Studio**, vinculando el contexto BDM (`context`) con los contratos de entrada (`formInput` / `formOutput`).

---

## 1. Formulario de Instanciación: `FormSolicitudCitaIPS`
* **Ubicación:** `Pool (Process_AgendamientoCitasIPS) -> Execution -> Instantiation form`
* **Uso dual:** Puede ser diligenciado directamente en el Portal Web de Pacientes o invocado de forma desatendida vía Bonita REST API (`POST /bonita/API/bpm/process/:processId/instantiation`) desde el webhook de WhatsApp / n8n.

### Widgets del Formulario
| Orden | Etiqueta UI | Tipo de Widget Bonita | Binding (`Value`) | Regla / Configuración |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Canal de recepción | `Select` | `formInput.solicitudInput.canalOrigen` | Opciones: `WHATSAPP`, `CORREO`, `LLAMADA`, `PORTAL_WEB` (Requerido) |
| 2 | Nombre de contacto | `Input (Text)` | `formInput.solicitudInput.nombreContacto` | Opcional |
| 3 | Teléfono / Celular (WhatsApp) | `Input (Text)` | `formInput.solicitudInput.telefonoContacto` | Requerido (`+57...`) |
| 4 | Correo electrónico | `Input (Email)` | `formInput.solicitudInput.correoContacto` | Opcional |
| 5 | Solicitud del paciente | `Textarea` | `formInput.solicitudInput.mensajeOriginal` | Requerido (mínimo 5 caracteres). Permite lenguaje natural para extracción por IA |
| 6 | Autorización Habeas Data (Ley 1581 de 2012) | `Checkbox` | `formInput.solicitudInput.autorizaTratamientoDatos` | Requerido (`true` obligatorio para habilitar el botón Enviar) |
| 7 | Enviar solicitud | `Button (Submit task)` | `formOutput` | Acción: `Start process` |

---

## 2. Formulario de Tarea Humana: `FormBandejaExcepcionesAuxiliar`
* **Tarea BPMN:** `Task_Excepcion` (*Atender excepción con el paciente*)
* **Actor:** `Actor_Auxiliar` (`auxiliar1.ips` .. `auxiliar4.ips`)

### Variables del UI Designer (Data Panel)
| Nombre Variable | Tipo en UI Designer | Valor / URL |
| :--- | :--- | :--- |
| `context` | `External API` | `../API/bpm/userTask/{{taskId}}/context` |
| `solicitudBDM` | `External API` | `../{{context.solicitudCita_ref.link}}` |
| `pacienteBDM` | `External API` | `../{{solicitudBDM.paciente_ref.link}}` |
| `formOutput` | `Javascript expression` | `return { resolucionAuxiliarInput: $data.formInput.resolucionAuxiliarInput };` |

### Secciones y Widgets
1. **Panel Superior – Diagnóstico Automático (Solo lectura / Read-Only):**
   * **Alerta de Excepción (`Text` con estilo `bg-warning`):** Muestra `{{solicitudBDM.motivoExcepcion}}` (*Datos incompletos IA*, *Sin autorización EPS* o *Sin agenda disponible*).
   * **Radicado y Canal:** `{{solicitudBDM.codigoRadicado}}` — `{{solicitudBDM.canalOrigen}}`
   * **Mensaje Original del Paciente:** `{{solicitudBDM.mensajeOriginal}}`
   * **Datos Extraídos por IA:** Documento `{{pacienteBDM.tipoDocumento}} {{pacienteBDM.numeroDocumento}}`, EPS `{{pacienteBDM.eps}}` (Activo: `{{pacienteBDM.activoEPS}}`), Especialidad `{{solicitudBDM.especialidadRequerida}}`, Confianza IA `{{solicitudBDM.confianzaIA * 100}}%`, Teléfono `{{pacienteBDM.telefono}}`.

2. **Panel Inferior – Gestión y Resolución del Auxiliar (Editable):**
   * `Checkbox` **¿Se logró agendar la cita tras contactar al paciente?**: `formInput.resolucionAuxiliarInput.logroAgendar`
   * `Input` **Documento corregido (si aplica)**: `formInput.resolucionAuxiliarInput.numeroDocumentoCorregido`
   * `Input` **Número de autorización EPS validada manualmente**: `formInput.resolucionAuxiliarInput.codigoAutorizacionManual`
   * *Contenedor condicional (`Hidden` cuando `!formInput.resolucionAuxiliarInput.logroAgendar`):*
     * `Select` **Especialidad asignada**: `formInput.resolucionAuxiliarInput.especialidadAsignada`
     * `Input` **Médico especialista**: `formInput.resolucionAuxiliarInput.medicoAsignado`
     * `Input` **Sede IPS / Consultorio**: `sedeAsignada` / `consultorioAsignado`
     * `DateTime Picker` **Fecha y hora de la cita**: `formInput.resolucionAuxiliarInput.fechaHoraAsignada`
   * `Textarea` **Observaciones de gestión (Obligatorio)**: `formInput.resolucionAuxiliarInput.observaciones`
   * `Button` **Finalizar Gestión (`Submit task`)**: Si se agendó, el caso continúa automáticamente hacia `Task_Confirmar` y el ciclo de recordatorio 24 h antes.

---

## 3. Formulario de Tarea Humana: `FormRespuestaRecordatorio`
* **Tarea BPMN:** `Task_Responder` (*Responder recordatorio*)
* **Actor:** `Actor_Paciente` (o ejecutada vía API REST `POST /bonita/API/bpm/userTask/:taskId/execution` cuando el paciente responde el botón interactivo en WhatsApp).

### Variables del UI Designer
| Nombre Variable | Tipo en UI Designer | Valor / URL |
| :--- | :--- | :--- |
| `context` | `External API` | `../API/bpm/userTask/{{taskId}}/context` |
| `citaBDM` | `External API` | `../{{context.citaMedica_ref.link}}` |

### Widgets
* **Resumen de la Cita (Read-Only):** Muestra `{{citaBDM.especialidad}}` con `{{citaBDM.nombreMedico}}`, fecha `{{citaBDM.fechaHoraCita | uiDate}}`, sede `{{citaBDM.sedeIPS}}`, consultorio `{{citaBDM.consultorio}}`.
* **Radio Buttons (`respuestaInput.accionPaciente`):**
  * `CONFIRMAR` — *Confirmo mi asistencia a la cita*
  * `REPROGRAMAR` — *Necesito buscar una nueva fecha/hora*
  * `CANCELAR` — *Deseo cancelar mi cita y liberar el espacio*
* **Motivo o comentario opcional (`Input`)**: `formInput.respuestaInput.motivoOpcional`
* **Botón Enviar Respuesta (`Submit task`)**
