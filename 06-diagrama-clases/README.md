# Diagrama de clases del agente RPA – Agendamiento de citas IPS

Adaptación del diagrama de clases base del agente RPA al proceso automatizado de asignación de citas médicas en una IPS de consulta externa ([proceso TO-BE](../01-bpmn/tobe.svg)), incorporando las entidades del dominio **`Paciente`**, **`Solicitud`** y **`Cita`**.

## Diagrama de clases

![Diagrama de clases del agente RPA](diagrama-clases-rpa.svg)

> Código fuente editable en Mermaid: [diagrama-clases-rpa.mmd](diagrama-clases-rpa.mmd)

<details>
<summary>Ver código Mermaid</summary>

```mermaid
classDiagram
    direction TB

    class Paciente {
        -String idPaciente
        -String tipoDocumento
        -String numeroDocumento
        -String nombreCompleto
        -String telefono
        -String correo
        -String eps
        -Boolean afiliacionActiva
        -Boolean consentimientoDatos
        +estaActivo() Boolean
        +actualizarEstadoEPS(activo: Boolean) void
        +tieneConsentimiento() Boolean
    }

    class Solicitud {
        -String idSolicitud
        -Paciente paciente
        -Cita citaAsignada
        -String canalOrigen
        -String mensajeOriginal
        -LocalDateTime fechaRecepcion
        -String especialidad
        -String urgencia
        -String codigoAutorizacion
        -Boolean autorizacionVigente
        -EstadoSolicitud estado
        -String motivoExcepcion
        +extraerDatosSolicitud(llm: ProveedorLLM) void
        +datosCompletos() Boolean
        +esAgendable() Boolean
        +registrarVerificacionEPS(autorizado: Boolean) void
        +escalarAAuxiliar(motivo: String) void
    }

    class Cita {
        -String idCita
        -Paciente paciente
        -Solicitud solicitudOrigen
        -LocalDateTime fechaHora
        -String especialidad
        -String medicoAsignado
        -String consultorio
        -EstadoCita estado
        -Boolean confirmacionEnviada
        -Boolean recordatorioEnviado
        +confirmar() void
        +reprogramar(nuevaFechaHora: LocalDateTime) void
        +cancelar(motivo: String) void
        +marcarRecordatorioEnviado() void
    }

    class AgenteRPA {
        -String idAgente
        -EstadoAgente estado
        -String urlPortalEPS
        -String urlAgendaIPS
        -PlanificadorTareas planificador
        -EjecutorAcciones ejecutor
        -ContextoNavegador navegador
        -List~ResultadoPaso~ historial
        +verificarPacienteEPS(paciente: Paciente, solicitud: Solicitud) Boolean
        +buscarEspacioAgenda(solicitud: Solicitud) List~LocalDateTime~
        +registrarCita(solicitud: Solicitud, fechaHora: LocalDateTime) Cita
        +reprogramarCita(cita: Cita, nuevaFechaHora: LocalDateTime) Cita
        +cancelarCita(cita: Cita) Boolean
        +ejecutarPrompt(prompt: String) ReporteEjecucion
        -ejecutarCiclo(meta: String) ReporteEjecucion
    }

    class ProveedorLLM {
        <<interface>>
        +generarRespuesta(mensajes: List~Mensaje~, esquema: EsquemaJSON) RespuestaLLM
        +extraerDatos(contenido: String, esquema: EsquemaJSON) Map~String, Any~
    }

    class PlanificadorTareas {
        -ProveedorLLM llm
        -PlantillaPrompt plantilla
        +crearSiguientePaso(meta: String, historial: List~ResultadoPaso~, estado: EstadoDOM) PasoAccion
        +metaCumplida(meta: String, historial: List~ResultadoPaso~, estado: EstadoDOM) Boolean
    }

    class ContextoNavegador {
        -Page paginaActiva
        -MotorDriver motor
        +navegar(url: String) void
        +capturarEstado() EstadoDOM
        +tomarCaptura() Byte[]
        +obtenerPagina() Page
        +cerrar() void
    }

    class EstadoDOM {
        +String url
        +String titulo
        +List~NodoElemento~ elementosInteractivos
        +Byte[] capturaPantalla
        +aJSON() String
    }

    class EjecutorAcciones {
        -ContextoNavegador navegador
        +ejecutar(paso: PasoAccion) ResultadoPaso
    }

    class PasoAccion {
        <<abstract>>
        +String idPaso
        +String razonamiento
    }

    class AccionNavegar {
        +String url
    }

    class AccionClic {
        +String selector
        +Punto coordenadas
    }

    class AccionEscribir {
        +String selector
        +String texto
        +Boolean estaEncriptado
    }

    class AccionExtraer {
        +String definicionEsquema
        +Map~String, Any~ datosExtraidos
    }

    class ResultadoPaso {
        +String idPaso
        +Boolean exito
        +String mensajeError
        +Duration duracion
        +EstadoDOM estadoPosterior
    }

    %% Relaciones del dominio (Caso IPS)
    Paciente "1" <-- "1..*" Solicitud : pertenece a
    Solicitud "1" <--> "0..1" Cita : genera
    Paciente "1" <-- "0..*" Cita : asignada a
    Solicitud ..> ProveedorLLM : extrae datos con

    %% Relaciones del Agente RPA con el dominio
    AgenteRPA ..> Solicitud : procesa
    AgenteRPA ..> Paciente : verifica en EPS
    AgenteRPA ..> Cita : agenda y gestiona

    %% Relaciones internas del Agente RPA
    AgenteRPA *-- PlanificadorTareas : posee
    AgenteRPA *-- EjecutorAcciones : posee
    AgenteRPA *-- ContextoNavegador : gestiona
    AgenteRPA o-- ResultadoPaso : registra historial
    PlanificadorTareas --> ProveedorLLM : consulta
    PlanificadorTareas ..> EstadoDOM : analiza
    PlanificadorTareas ..> PasoAccion : resuelve
    EjecutorAcciones --> ContextoNavegador : controla
    EjecutorAcciones ..> PasoAccion : interpreta
    EjecutorAcciones ..> ResultadoPaso : retorna
    ContextoNavegador ..> EstadoDOM : genera
    ResultadoPaso --> EstadoDOM : referencia

    PasoAccion <|-- AccionNavegar
    PasoAccion <|-- AccionClic
    PasoAccion <|-- AccionEscribir
    PasoAccion <|-- AccionExtraer
```

</details>

## Clases del dominio incorporadas

| Clase | Responsabilidad en el caso | Atributos y validaciones clave |
| --- | --- | --- |
| **`Paciente`** | Representa al usuario afiliado a una EPS que solicita atención en la IPS de consulta externa. | Identificación (`tipoDocumento`, `numeroDocumento`), canales de contacto (`telefono`, `correo`), `eps`, estado `afiliacionActiva` consultado en el portal de la EPS y `consentimientoDatos` para cumplir la **Ley 1581 de 2012**. |
| **`Solicitud`** | Modela cada requerimiento recibido por WhatsApp, correo o llamada (~300/día) y su ciclo de validación. | Almacena `canalOrigen`, `mensajeOriginal`, `especialidad`, `urgencia`, `codigoAutorizacion` y `autorizacionVigente`. Usa `ProveedorLLM` (`extraerDatosSolicitud`) para estructurar el mensaje, valida si `esAgendable()` y registra el `motivoExcepcion` cuando se escala al auxiliar. |
| **`Cita`** | Representa el espacio médico efectivamente asignado en el sistema de agendamiento de la IPS. | Vincula al `Paciente` y la `Solicitud` de origen con `fechaHora`, `especialidad`, `medicoAsignado`, `consultorio`, `estado` y banderas de trazabilidad (`confirmacionEnviada`, `recordatorioEnviado` 24 h antes) para soportar confirmación, reprogramación y cancelación. |

### Relaciones y multiplicidades del dominio

- **`Solicitud` (`1..*`) → `Paciente` (`1`) — `pertenece a`:** un paciente puede realizar múltiples solicitudes de cita a lo largo del tiempo; toda solicitud está asociada a un único paciente.
- **`Solicitud` (`1`) ↔ `Cita` (`0..1`) — `genera`:** una solicitud válida y con disponibilidad origina una cita; si el paciente está inactivo, carece de autorización o no hay cupo, la solicitud pasa a excepción sin generar cita automática (`0`).
- **`Cita` (`0..*`) → `Paciente` (`1`) — `asignada a`:** un paciente puede tener cero o varias citas registradas (históricas o futuras), cada una asignada a un único paciente.
- **`Solicitud` ⇢ `ProveedorLLM` — `extrae datos con`:** conecta la tarea de IA (*Extraer datos de la solicitud*) con el método `extraerDatos` del proveedor LLM para obtener documento, especialidad y urgencia a partir de texto no estructurado.

## Adaptación del Agente RPA al proceso TO-BE

Como ni el portal de las EPS ni el sistema de agendamiento de la IPS cuentan con API, `AgenteRPA` incorpora las direcciones objetivo (`urlPortalEPS`, `urlAgendaIPS`) y expone operaciones de dominio que encapsulan el ciclo autónomo (`ejecutarCiclo`) sobre la interfaz web:

| Operación en `AgenteRPA` | Tarea correspondiente en el BPMN TO-BE | Uso de los componentes internos del agente |
| --- | --- | --- |
| `verificarPacienteEPS(paciente, solicitud)` | *Verificar paciente en portal EPS* | Navega a `urlPortalEPS` (`AccionNavegar`), ingresa credenciales y documento con `AccionEscribir` (`estaEncriptado = true` por Ley 1581), extrae estado de afiliación y autorización (`AccionExtraer`) y actualiza `Paciente` y `Solicitud`. |
| `buscarEspacioAgenda(solicitud)` | *Buscar espacio en la agenda* | Navega a `urlAgendaIPS`, filtra por `especialidad` y `urgencia` (`AccionClic` / `AccionEscribir`) y extrae los horarios disponibles desde el `EstadoDOM` (`AccionExtraer`). |
| `registrarCita(solicitud, fechaHora)` | *Registrar cita y guardar en base de datos* | Selecciona el cupo en el sistema de agendamiento, diligencia los datos del `Paciente` sin doble digitación manual y retorna la instancia de `Cita` creada. |
| `reprogramarCita(cita, nuevaFechaHora)` | Flujo *Reprograma* tras recordatorio | Busca el nuevo cupo disponible, actualiza la reserva en `urlAgendaIPS` y sincroniza el estado de la `Cita`. |
| `cancelarCita(cita)` | *Cancelar cita y liberar espacio* | Ubica la reserva activa en `urlAgendaIPS`, ejecuta la cancelación por pantalla liberando el cupo médico y marca la `Cita` como cancelada. |
