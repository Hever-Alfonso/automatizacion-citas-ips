# Requerimientos – Automatización de la asignación de citas médicas

Los requerimientos están escritos como historias de usuario: **Como** [rol], **quiero** [algo], **para** [beneficio]. Cada uno tiene sus criterios de aceptación, el componente que lo resuelve y su prioridad.

## Resumen

| ID | Requerimiento | Componente | Prioridad |
|---|---|---|---|
| RF-01 | Recibir solicitudes por varios canales | n8n | Alta |
| RF-02 | Extraer los datos de la solicitud | IA | Alta |
| RF-03 | Verificar al paciente en el portal de la EPS | RPA | Alta |
| RF-04 | Buscar espacio en la agenda | RPA | Alta |
| RF-05 | Registrar la cita | RPA | Alta |
| RF-06 | Enviar la confirmación | n8n | Alta |
| RF-07 | Enviar el recordatorio | n8n | Alta |
| RF-08 | Confirmar, cancelar o reprogramar la cita | n8n + RPA | Media |
| RF-09 | Pasar las excepciones al auxiliar | n8n | Alta |
| RNF-01 | Protección de datos (Ley 1581 de 2012) | Todos | Alta |
| RNF-02 | Disponibilidad | Todos | Alta |
| RNF-03 | Compatibilidad con los sistemas actuales | RPA | Alta |
| RNF-04 | Tiempo de respuesta | Todos | Alta |
| RNF-05 | Trazabilidad | n8n | Media |

## Requerimientos funcionales

### RF-01 · Recibir solicitudes por varios canales
**Historia:** Como paciente, quiero pedir una cita por WhatsApp, correo o llamada, para usar el canal que me quede más fácil.

**Criterios de aceptación:**
- Las solicitudes de los tres canales llegan a un mismo flujo en n8n.
- Las llamadas se convierten a texto antes de procesarse.
- Cada solicitud queda registrada con su canal, fecha y hora.

**Componente:** n8n · **Prioridad:** Alta

### RF-02 · Extraer los datos de la solicitud
**Historia:** Como auxiliar, quiero que la IA lea la solicitud y extraiga los datos del paciente, la especialidad y la urgencia, para no tener que leer y digitar cada mensaje.

**Criterios de aceptación:**
- Extrae el nombre, el tipo y número de documento, la EPS, la especialidad y la urgencia.
- Entrega los datos en un formato estructurado que el resto del flujo puede usar.

**Componente:** IA · **Prioridad:** Alta

### RF-03 · Verificar al paciente en el portal de la EPS
**Historia:** Como IPS, quiero que el bot verifique en el portal de la EPS si el paciente está activo y si la especialidad requiere autorización, para no agendar citas que luego haya que cancelar.

**Criterios de aceptación:**
- El bot consulta el portal con el documento del paciente.
- Indica si el paciente está activo y si tiene la autorización (cuando aplica).
- Si el paciente está inactivo o sin autorización, el caso pasa al auxiliar como excepción.

**Componente:** RPA · **Prioridad:** Alta

### RF-04 · Buscar espacio en la agenda
**Historia:** Como paciente, quiero que el sistema encuentre un espacio libre con un médico de la especialidad, para no esperar uno o dos días por una respuesta.

**Criterios de aceptación:**
- El bot busca el espacio disponible más cercano en el sistema de agendamiento de la IPS, dando prioridad a las solicitudes urgentes.
- Si no hay espacio, el caso pasa al auxiliar como excepción.

**Componente:** RPA · **Prioridad:** Alta

### RF-05 · Registrar la cita
**Historia:** Como auxiliar, quiero que el bot registre la cita en el sistema de agendamiento, para evitar errores de digitación y citas duplicadas o cruzadas.

**Criterios de aceptación:**
- Registra el paciente, el médico, la especialidad, la fecha y la hora.
- Antes de guardar, verifica que el espacio siga libre y que el paciente no tenga ya esa cita.
- La cita queda guardada en la base de datos del sistema de agendamiento.

**Componente:** RPA · **Prioridad:** Alta

### RF-06 · Enviar la confirmación
**Historia:** Como paciente, quiero recibir la confirmación de la cita por el mismo canal en el que la pedí, para saber cuándo y con quién es.

**Criterios de aceptación:**
- Se envía apenas queda registrada la cita.
- Incluye la fecha, la hora, el médico, la especialidad y el lugar.

**Componente:** n8n · **Prioridad:** Alta

### RF-07 · Enviar el recordatorio
**Historia:** Como IPS, quiero enviar un recordatorio 24 horas antes de la cita, para reducir la inasistencia.

**Criterios de aceptación:**
- Se envía 24 horas antes de la cita por el canal del paciente.
- Le permite al paciente responder si confirma, cancela o reprograma.

**Componente:** n8n · **Prioridad:** Alta

### RF-08 · Confirmar, cancelar o reprogramar la cita
**Historia:** Como paciente, quiero responder el recordatorio para confirmar, cancelar o reprogramar mi cita, para que el espacio no quede vacío si no puedo ir.

**Criterios de aceptación:**
- Si confirma, la cita queda confirmada.
- Si cancela, el bot cancela la cita y libera el espacio en la agenda.
- Si reprograma, el bot vuelve a buscar un espacio (RF-04).

**Componente:** n8n + RPA · **Prioridad:** Media

### RF-09 · Pasar las excepciones al auxiliar
**Historia:** Como auxiliar, quiero recibir solo los casos que el sistema no puede resolver, para dedicar mi tiempo a esos pacientes.

**Criterios de aceptación:**
- Pasan al auxiliar los casos con paciente inactivo o sin autorización, o sin espacio en la agenda.
- El auxiliar recibe el caso con los datos ya extraídos y el motivo de la excepción.

**Componente:** n8n · **Prioridad:** Alta

## Requerimientos no funcionales

### RNF-01 · Protección de datos (Ley 1581 de 2012)
**Historia:** Como IPS, quiero que los datos de salud de los pacientes estén protegidos, para cumplir la Ley 1581 de 2012.

**Criterios de aceptación:**
- Los datos viajan y se guardan cifrados.
- Solo el personal autorizado puede ver los datos de los pacientes.
- Las credenciales del portal de la EPS no quedan escritas en el código.

**Componente:** Todos · **Prioridad:** Alta

### RNF-02 · Disponibilidad
**Historia:** Como IPS, quiero que el servicio esté disponible durante todo el horario de atención, para que las solicitudes no se represen.

**Criterios de aceptación:**
- El flujo funciona durante todo el horario de atención.
- Si un componente falla, la solicitud no se pierde y pasa al auxiliar.

**Componente:** Todos · **Prioridad:** Alta

### RNF-03 · Compatibilidad con los sistemas actuales
**Historia:** Como IPS, quiero que la solución funcione con el portal de la EPS y el sistema de agendamiento actuales, porque no se pueden cambiar ni reemplazar.

**Criterios de aceptación:**
- El bot usa el portal de la EPS por pantalla, como lo haría una persona, porque no tiene API.
- La solución no modifica el portal ni reemplaza el sistema de agendamiento.

**Componente:** RPA · **Prioridad:** Alta

### RNF-04 · Tiempo de respuesta
**Historia:** Como paciente, quiero recibir respuesta a mi solicitud en minutos, para no esperar uno o dos días.

**Criterios de aceptación:**
- En promedio, una solicitud se resuelve en menos de 10 minutos (en la simulación el promedio fue de 7,3 minutos).
- El sistema atiende las 300 solicitudes diarias sin represarse.

**Componente:** Todos · **Prioridad:** Alta

### RNF-05 · Trazabilidad
**Historia:** Como IPS, quiero que cada paso del proceso quede registrado, para saber qué pasó con cada solicitud.

**Criterios de aceptación:**
- Cada solicitud tiene un registro con su estado, la fecha y quién actuó (IA, bot, n8n o auxiliar).
- Se puede consultar en qué estado está cualquier solicitud.

**Componente:** n8n · **Prioridad:** Media
