# Chats de prueba del componente de IA – Agendamiento de citas IPS

Registro de pruebas del componente de extracción con IA (**RF-02**) integrado en el flujo de [la solución en n8n](../07-solucion/README.md). Se evalúa la capacidad de **Google Gemini (`models/gemini-2.5-flash`)** para transformar mensajes libres provenientes de **WhatsApp, correo electrónico y llamadas transcritas** (**RF-01**) en el esquema JSON estructurado que consumen el bot RPA y el orquestador, verificando también la interacción completa con el paciente (**RF-06** a **RF-09**).

## Configuración evaluada

- **Nodo en n8n:** `@n8n/n8n-nodes-langchain.informationExtractor` (`Extraer datos (IA)`) + `@n8n/n8n-nodes-langchain.lmChatGoogleGemini` (`Google Gemini`).
- **Modelo y temperatura:** `models/gemini-2.5-flash` con `temperature = 0` (respuestas deterministas) y hasta `3` reintentos automáticos (`RNF-02`).
- **System prompt:**
  > *«Eres el asistente de agendamiento de una IPS en Colombia. Extrae solo la información que aparece en el mensaje del paciente. Si un dato no aparece, deja el campo vacío.»*
- **Atributos solicitados al modelo:** `nombreCompleto`, `tipoDocumento` (`CC`, `TI`, `CE`, `PA`), `numeroDocumento` (solo dígitos), `eps`, `especialidad` (`medicina general`, `odontologia`, `pediatria`, `dermatologia`, `cardiologia`) y `urgencia` (`alta`, `normal`).
- **Post-procesamiento (`Unir datos extraídos`):** eliminación de tildes y paso a minúsculas en `especialidad` (por defecto `medicina general`), limpieza de signos de puntuación en `numeroDocumento`, `CC` por defecto en `tipoDocumento` y normalización binaria de `urgencia` (`alta` / `normal`).

## Resumen de casos de prueba

| ID | Canal | Caso evaluado | Documento | Especialidad | Urgencia | Resultado en el flujo | Validación |
|---|---|---|---|---|---|---|---|
| [CP-01](#cp-01--solicitud-urgente-por-whatsapp-paciente-activo) | WhatsApp | Prioridad alta por fiebre prolongada (caso base) | `CC 1037654321` | `medicina general` | `alta` | Cita agendada en el primer cupo hábil (07:00 a. m.) | Cumple |
| [CP-02](#cp-02--solicitud-formal-por-correo-sin-autorización-vigente) | Correo | Especialidad restringida sin orden autorizada | `CC 43210987` | `dermatologia` | `normal` | Excepción al auxiliar: sin autorización en EPS | Cumple |
| [CP-03](#cp-03--llamada-telefónica-transcrita-con-síntomas-de-alarma-y-sin-agenda) | Llamada (voz a texto) | Urgencia implícita por síntomas + documento con puntos | `CC 98765432` | `cardiologia` | `alta` | Excepción al auxiliar: sin cupos en 5 días hábiles | Cumple |
| [CP-04](#cp-04--mensaje-coloquial-con-abreviaturas-paciente-inactivo) | WhatsApp | Escritura informal sin signos de puntuación | `CC 11223344` | `odontologia` | `normal` | Excepción al auxiliar: paciente inactivo en EPS | Cumple |
| [CP-05](#cp-05--solicitud-pediátrica-enviada-por-acudiente-con-tarjeta-de-identidad) | WhatsApp | Distinción entre acudiente y paciente menor (`TI`) | `TI 1098765432` | `pediatria` | `normal` | Extrae datos de la menor y no del remitente | Cumple |
| [CP-06](#cp-06--solicitud-con-cédula-de-extranjería-sin-eps-ni-especialidad-explícita) | Correo | Valores por defecto y ausencia de alucinación | `CE 456789` | `medicina general` | `normal` | `eps` queda vacío `""`; asigna especialidad base | Cumple |
| [CP-07](#cp-07--mensaje-incompleto-sin-número-de-documento) | WhatsApp | Control de no invención de datos faltantes | *(vacío)* | `medicina general` | `alta` | `numeroDocumento: ""` y escalamiento limpio al auxiliar | Cumple |
| [CP-08](#cp-08--conversación-de-extremo-a-extremo-con-recordatorio-y-reprogramación) | WhatsApp | Ciclo completo: solicitud → confirmación → recordatorio → reprogramación | `CC 1037654321` | `dermatologia` | `normal` | Reasigna cita a nuevo cupo hábil y libera el previo | Cumple |

---

## Detalle de las pruebas

### CP-01 · Solicitud urgente por WhatsApp (paciente activo)

- **Objetivo:** validar el caso base incluido en el nodo `Ejemplo de solicitud`, comprobando extracción completa y asignación prioritaria del primer horario disponible (`RF-02`, `RF-04`, `RF-05`, `RF-06`).
- **Canal de entrada:** `WhatsApp` (`telefono: "3001234567"`)

**Mensaje del paciente:**
> «Hola, buenas tardes. Soy Ana Gómez, cédula 1037654321, de Sura. Necesito una cita de medicina general, es urgente porque tengo fiebre hace 3 días.»

**Salida extraída por Gemini (`Extraer datos (IA)`):**
```json
{
  "output": {
    "nombreCompleto": "Ana Gómez",
    "tipoDocumento": "CC",
    "numeroDocumento": "1037654321",
    "eps": "Sura",
    "especialidad": "medicina general",
    "urgencia": "alta"
  }
}
```

**Mensaje de confirmación generado (`Enviar confirmación`):**
> «Hola Ana Gómez, tu cita de medicina general quedó para el martes, 3 de noviembre de 2026, 7:00 a. m. con Dra. Laura Mejía, Consultorio 101, IPS Salud Integral. Código: CITA-1037654321-202611030700.»

**Notas de validación:**
- La palabra *«cédula»* se mapea correctamente a `CC`.
- Al detectar tanto la mención explícita (*«es urgente»*) como el cuadro febril de 3 días, `urgencia` se clasifica en `alta`, otorgándole el cupo reservado de las `07:00 a. m.` (una solicitud `normal` recibiría las `08:00 a. m.`).

---

### CP-02 · Solicitud formal por correo sin autorización vigente

- **Objetivo:** verificar la extracción sobre un correo electrónico formal con tildes y texto extenso, y validar el desvío al auxiliar cuando la especialidad exige autorización de la EPS (`RF-02`, `RF-03`, `RF-09`).
- **Canal de entrada:** `Correo` (`correo: "luisa.perez@correo.com"`)

**Mensaje del paciente:**
> «Asunto: Solicitud de cita por dermatología
> Cordial saludo. Mi nombre es Luisa Pérez, identificada con C.C. 43210987 y afiliada a EPS Sanitas. Quisiera agendar una consulta de dermatología para revisión de unas manchas en la piel cuando tengan disponibilidad. Muchas gracias.»

**Salida extraída por Gemini (`Extraer datos (IA)`):**
```json
{
  "output": {
    "nombreCompleto": "Luisa Pérez",
    "tipoDocumento": "CC",
    "numeroDocumento": "43210987",
    "eps": "Sanitas",
    "especialidad": "dermatologia",
    "urgencia": "normal"
  }
}
```

**Salida de excepción hacia el auxiliar (`Pasar excepción al auxiliar`):**
```json
{
  "estado": "Excepción",
  "especialidad": "dermatologia",
  "urgencia": "normal",
  "autorizacionVigente": false,
  "motivoExcepcion": "La especialidad requiere autorización y el paciente no la tiene",
  "asignadoA": "Auxiliar de agendamiento"
}
```

**Notas de validación:**
- Aunque en el correo aparece con tilde (*«dermatología»*) y abreviatura con puntos (*«C.C.»*), la salida y la normalización NFD entregan `"dermatologia"` y `"CC"` listos para las reglas del nodo RPA.
- Como es una revisión rutinaria sin síntomas agudos, se clasifica como `urgencia: "normal"`. El auxiliar recibe los datos ya estructurados sin tener que releer ni digitar el correo.

---

### CP-03 · Llamada telefónica transcrita con síntomas de alarma y sin agenda

- **Objetivo:** evaluar robustez ante muletillas propias de transcripción voz a texto (`RF-01`), números dictados con separadores de miles y detección de urgencia implícita por síntomas graves aunque el paciente no pronuncie la palabra *«urgente»*.
- **Canal de entrada:** `Llamada` (`telefono: "3119876543"`)

**Transcripción recibida:**
> «Aló, sí, buenos días... eh, mire señorita, le habla Carlos Ruiz, mi cédula es 98.765.432 y vengo por Nueva EPS. Llamo a pedir cita con el cardiólogo porque desde ayer siento una presión fuerte en el pecho y me mareo al caminar.»

**Salida extraída por Gemini (`Extraer datos (IA)`):**
```json
{
  "output": {
    "nombreCompleto": "Carlos Ruiz",
    "tipoDocumento": "CC",
    "numeroDocumento": "98765432",
    "eps": "Nueva EPS",
    "especialidad": "cardiologia",
    "urgencia": "alta"
  }
}
```

**Resultado tras `Verificar paciente en portal EPS` y `Buscar espacio en la agenda`:**
```json
{
  "estado": "Excepción",
  "codigoAutorizacion": "AUT-98765432",
  "autorizacionVigente": true,
  "hayEspacio": false,
  "motivoExcepcion": "No hay espacio en cardiologia en los próximos 5 días hábiles",
  "asignadoA": "Auxiliar de agendamiento"
}
```

**Notas de validación:**
- El modelo ignora las muletillas conversacionales (*«Aló, sí... eh, mire señorita»*), traduce *«cardiólogo»* al valor canónico `cardiologia`, limpia los puntos del documento (`98765432`) e infiere `urgencia: "alta"` a partir de *«presión fuerte en el pecho y me mareo»*.
- La EPS aprueba con código `AUT-98765432`, pero al no existir agenda de cardiología el caso pasa de inmediato al auxiliar marcado con urgencia `alta` para gestión prioritaria.

---

### CP-04 · Mensaje coloquial con abreviaturas (paciente inactivo)

- **Objetivo:** comprobar extracción en mensajes cortos de WhatsApp escritos en minúsculas, con abreviaturas coloquiales y sin signos de puntuación (`RF-02`, `RF-03`).
- **Canal de entrada:** `WhatsApp` (`telefono: "3152223344"`)

**Mensaje del paciente:**
> «buenas necesito cita de odontologia xfa para limpieza soy pedro diaz cc 11223344 de sura gracias»

**Salida extraída por Gemini (`Extraer datos (IA)`):**
```json
{
  "output": {
    "nombreCompleto": "Pedro Diaz",
    "tipoDocumento": "CC",
    "numeroDocumento": "11223344",
    "eps": "Sura",
    "especialidad": "odontologia",
    "urgencia": "normal"
  }
}
```

**Notas de validación:**
- Pese a las abreviaturas (*«xfa»*) y la falta de puntuación, el extractor identifica todos los campos con exactitud.
- En el paso `Verificar paciente en portal EPS (RPA)`, el sistema detecta `activo: false` y bloquea el agendamiento con motivo `"El paciente está inactivo en la EPS"`, evitando parte del 10 % de cancelaciones que ocurrían en el proceso AS-IS.

---

### CP-05 · Solicitud pediátrica enviada por acudiente con Tarjeta de Identidad

- **Objetivo:** validar que, cuando un padre o acudiente escribe en nombre de un menor de edad, la IA extraiga los datos **del paciente** (`nombreCompleto`, `TI`, `numeroDocumento`) y no los de la persona que redacta el mensaje.
- **Canal de entrada:** `WhatsApp` (`telefono: "3014455667"`)

**Mensaje del acudiente:**
> «Buen día, les escribe Marcela Gómez. Quiero pedir una cita de pediatría de control de crecimiento para mi hija Sofía Martínez Gómez, tarjeta de identidad 1098765432, afiliada a Sura.»

**Salida extraída por Gemini (`Extraer datos (IA)`):**
```json
{
  "output": {
    "nombreCompleto": "Sofía Martínez Gómez",
    "tipoDocumento": "TI",
    "numeroDocumento": "1098765432",
    "eps": "Sura",
    "especialidad": "pediatria",
    "urgencia": "normal"
  }
}
```

**Notas de validación:**
- El modelo distingue correctamente entre la remitente (*Marcela Gómez*) y la paciente (*Sofía Martínez Gómez*), asignando `tipoDocumento: "TI"` y `especialidad: "pediatria"`.

---

### CP-06 · Solicitud con Cédula de Extranjería sin EPS ni especialidad explícita

- **Objetivo:** verificar el cumplimiento de la instrucción anti-alucinación (*«Si un dato no aparece, deja el campo vacío»*) cuando el usuario omite la EPS y pide un chequeo sin nombrar la especialidad exacta.
- **Canal de entrada:** `Correo` (`correo: "jorge.ramirez@ejemplo.org"`)

**Mensaje del paciente:**
> «Buenas tardes, mi nombre es Jorge Ramírez, documento CE 456789. Quisiera apartar una cita médica esta semana para un chequeo de rutina y lectura de exámenes.»

**Salida extraída por Gemini (`Extraer datos (IA)`):**
```json
{
  "output": {
    "nombreCompleto": "Jorge Ramírez",
    "tipoDocumento": "CE",
    "numeroDocumento": "456789",
    "eps": "",
    "especialidad": "medicina general",
    "urgencia": "normal"
  }
}
```

**Notas de validación:**
- `eps` se devuelve vacío (`""`) sin inventar ninguna aseguradora.
- `tipoDocumento` respeta `CE` (no se sobreescribe con `CC` en la normalización porque no viene vacío) y la solicitud de chequeo se asigna a `medicina general`.

---

### CP-07 · Mensaje incompleto sin número de documento

- **Objetivo:** comprobar que ante la ausencia crítica del número de documento la IA no fabrique dígitos ficticios y el flujo escale limpiamente el caso al auxiliar conservando el resto de la información extraída (`RF-02`, `RF-09`).
- **Canal de entrada:** `WhatsApp` (`telefono: "3208889900"`)

**Mensaje del paciente:**
> «Buenas tardes, soy Camila Torres de Sanitas y necesito urgente una cita porque tengo un dolor muy fuerte en el oído desde anoche.»

**Salida extraída por Gemini (`Extraer datos (IA)`):**
```json
{
  "output": {
    "nombreCompleto": "Camila Torres",
    "tipoDocumento": "",
    "numeroDocumento": "",
    "eps": "Sanitas",
    "especialidad": "medicina general",
    "urgencia": "alta"
  }
}
```

**Resultado en el flujo:**
- `Unir datos extraídos` conserva `numeroDocumento: ""`.
- `Verificar paciente en portal EPS (RPA)` retorna `motivoExcepcion: "El documento no aparece en el portal de la EPS"` y transfiere el caso al auxiliar con el teléfono `3208889900`, el nombre `Camila Torres`, la EPS `Sanitas` y la marca `urgencia: "alta"`, para que el auxiliar solo solicite el número de documento.

---

### CP-08 · Conversación de extremo a extremo con recordatorio y reprogramación

- **Objetivo:** evidenciar la experiencia completa del paciente a través de WhatsApp desde la solicitud inicial con autorización vigente (`dermatologia`), pasando por la confirmación (`RF-06`), el recordatorio 24 h antes (`RF-07`) y la reprogramación automática (`RF-08`).

**Secuencia de mensajes en el canal WhatsApp:**

1. **Paciente → IPS (`Recibir solicitud`):**
   > «Hola, soy Ana Gómez, CC 1037654321 de Sura. Quiero agendar mi cita autorizada de dermatología.»
   - *Extracción IA (`RF-02`):* `nombreCompleto: "Ana Gómez"`, `tipoDocumento: "CC"`, `numeroDocumento: "1037654321"`, `eps: "Sura"`, `especialidad: "dermatologia"`, `urgencia: "normal"`.
   - *Verificación EPS (`RF-03`):* paciente activa con autorización `AUT-1037654321`.
   - *Búsqueda y registro (`RF-04`, `RF-05`):* como `dermatologia` tiene 1 cupo diario (`10:00 a. m.`) y el primero se reserva para urgencias, asigna el segundo día hábil (`miércoles 4 de noviembre, 10:00 a. m.`).

2. **IPS → Paciente (`Enviar confirmación` · RF-06):**
   > «Hola Ana Gómez, tu cita de dermatologia quedó para el miércoles, 4 de noviembre de 2026, 10:00 a. m. con Dr. Juan Ochoa, Consultorio 302, IPS Salud Integral. Código: CITA-1037654321-202611041000.»

3. **IPS → Paciente (`Enviar recordatorio` · 24 h antes · RF-07):**
   > «Te recordamos tu cita de dermatologia el miércoles, 4 de noviembre de 2026, 10:00 a. m. con Dr. Juan Ochoa. Responde CONFIRMO, CANCELO o REPROGRAMO.»

4. **Paciente → IPS (`Esperar respuesta del paciente` · RF-08):**
   > Respuesta recibida: `reprograma` (`?respuesta=reprograma`)
   - *Reprogramación (`Reprogramar` → `Buscar espacio` → `Registrar cita`):* filtra horarios posteriores a `2026-11-04T10:00:00-05:00`, asigna el siguiente cupo libre y libera el anterior en la bitácora (`"Cita movida del 2026-11-04T10:00:00-05:00 al 2026-11-06T10:00:00-05:00; cupo anterior liberado"`).

5. **IPS → Paciente (`Enviar confirmación` de nueva cita · RF-06):**
   > «Hola Ana Gómez, tu cita de dermatologia quedó para el viernes, 6 de noviembre de 2026, 10:00 a. m. con Dr. Juan Ochoa, Consultorio 302, IPS Salud Integral. Código: CITA-1037654321-202611061000.»

---

## Conclusiones de las pruebas del componente de IA

- **Cumplimiento estricto del esquema (RF-02):** en los 8 escenarios evaluados, `gemini-2.5-flash` con `temperature = 0` devolvió estructuras JSON válidas que empatan sin errores con el nodo de normalización y con los pasos de RPA.
- **Cero alucinaciones ante datos omitidos:** gracias a la instrucción explícita del *system prompt*, los campos ausentes (`eps` en CP-06 y `numeroDocumento` en CP-07) permanecen vacíos, permitiendo que las reglas de negocio escalen el caso al auxiliar sin contaminar la base de datos.
- **Tolerancia multicanal (RF-01):** un único prompt procesa tanto correos formales como mensajes abreviados de WhatsApp y transcripciones telefónicas con ruido verbal o puntuación en los números de documento.
- **Tolerancia a fallos (RNF-02):** ante cualquier indisponibilidad temporal del API de Gemini, n8n ejecuta hasta 3 intentos espaciados cada 3 s y, de persistir el fallo, dirige la solicitud original a `Pasar excepción al auxiliar` para garantizar que ningún paciente quede sin atención.
