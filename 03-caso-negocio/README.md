# Caso de negocio – Agendamiento de citas IPS

Evaluación financiera a **3 años** de la automatización del proceso de asignación de citas médicas mediante **n8n + IA + RPA**, alimentada con los datos operativos de la IPS y los resultados de la [simulación en BIMP](../02-simulacion/README.md).

## Archivo fuente

- [Caso de Negocio - Agendamiento de Citas.xlsx](Caso%20de%20Negocio%20-%20Agendamiento%20de%20Citas.xlsx): libro de Excel con las hojas **`Entradas Agendamiento`** (supuestos operativos, salariales y de calidad) y **`BPMS`** (estructura de costos, ahorros, flujo de caja mensual del año 1, proyección anual a 3 años e indicadores financieros).

---

## Supuestos operativos de entrada

| Parámetro | Valor base (AS-IS) | Con la solución (TO-BE) |
|---|---|---|
| Solicitudes recibidas | 300 / día · 22 días hábiles = **6 600 / mes** | **6 600 / mes** |
| Citas asignadas efectivamente (81 %) | **5 346 citas / mes** | **5 346 citas / mes** |
| Auxiliares dedicados al proceso | **4 auxiliares** (ocupación 97 %) | **2 auxiliares** para excepciones (ocupación 27 %) |
| Salario mensual + factor prestacional (54 %) | 1 800 000 COP × 1,54 = **2 772 000 COP / auxiliar** | Costo hora auxiliar: **15 750 COP / h** |
| Tiempo promedio de ciclo por solicitud | **3,3 h** (8 min de trabajo manual directo) | **7,3 min** |
| Cancelaciones por error / sin autorización | **10 %** | **3 %** (se reocupa el 50 % de los cupos recuperados) |
| Inasistencia de pacientes | **20 %** (sin recordatorio) | **15 %** (con recordatorio 24 h antes) |
| Valor promedio y margen por consulta | **35 000 COP** · margen **70 %** (24 500 COP) | Igual tarifa y margen |

---

## Estructura de inversión, costos y beneficios

### Inversión inicial (CAPEX) y costos recurrentes (OPEX)

| Rubro | Tipo | Valor (COP) | Esquema de desembolso |
|---|---|---:|---|
| Desarrollo de la solución (bots RPA, flujos n8n e IA) | Pago único | 45 000 000 | 50 % en mes 0 y 50 % en mes 1 |
| Implementación (Ley 1581, capacitación, transición de personal) | Pago único | 13 000 000 | 50 % en mes 0 y 50 % en mes 1 |
| **Total inversión inicial (CAPEX)** | — | **58 000 000** | **29 000 000 en mes 0 + 29 000 000 en mes 1** |
| Licenciamiento (API de IA, WhatsApp Business, telefonía) | Anual | 14 400 000 | 1 200 000 / mes desde el mes 2 (+ IPC anual 5 %) |
| Soporte, mantenimiento e infraestructura (servidor n8n en nube) | Anual | 27 600 000 | 2 300 000 / mes desde el mes 2 (+ IPC anual 5 %) |
| **Total operación anual base (OPEX)** | — | **42 000 000 / año** | **3 500 000 / mes** |

### Beneficios mensuales cuantificados

| Fuente de beneficio | Cálculo mensual | Valor mensual (COP) | Inicio |
|---|---|---:|---|
| **Liberación de capacidad operativa** | Paso de 4 a 2 auxiliares: `2 × 2 772 000` | **5 544 000** | Mes 1 |
| **Reducción de cancelaciones (10 % → 3 %)** | `5 346 × 7 % × 50 % reocupación × 35 000 × 70 %` | **4 584 195** | Mes 6 |
| **Reducción de inasistencia (20 % → 15 %)** | `5 346 × 5 % × 35 000 × 70 %` | **6 548 850** | Mes 6 |
| **Total beneficios en régimen estable** | Suma mensual a partir del mes 6 | **16 677 045** | Mes 6+ |

---

## Flujo de caja proyectado e indicadores financieros (IPC = 5 %)

| Periodo | Liberación de tiempo (COP) | Otros beneficios (COP) | Egresos (COP) | Flujo de caja neto (COP) | Flujo acumulado (COP) |
|---|---:|---:|---:|---:|---:|
| **Inversión inicial (Mes 0)** | 0 | 0 | 29 000 000 | -29 000 000 | -29 000 000 |
| **Total Año 1 (Meses 1–12)** | 66 528 000 | 77 931 315 | 67 500 000 | 76 959 315 | 47 959 315 |
| **Total Año 2** | 69 854 400 | 140 276 367 | 44 100 000 | 166 030 767 | 213 990 082 |
| **Total Año 3** | 73 347 120 | 147 290 185 | 46 305 000 | 174 332 305 | 388 322 387 |

### Resumen de viabilidad financiera

| Indicador | Resultado |
|---|---|
| **Tiempo de recuperación (*Payback*)** | **Mes 9** (el flujo acumulado pasa de -4,75 M COP en el mes 8 a +8,43 M COP en el mes 9) |
| **Recursos máximos de apalancamiento** | **52 456 000 COP** (pico de caja negativo en el mes 1) |
| **Valor Presente Neto (VPN a 3 años, tasa 5 %)** | **345 484 186 COP** (~345,5 millones de pesos) |
| **Tasa Interna de Retorno (TIR)** | **331 %** |
