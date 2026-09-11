# 03 · Calidad, riesgos y operación

## 1. Definition of Ready (DoR)

Una historia entra en sprint sólo si:
1. Tiene valor de usuario expresado en formato "Como / quiero / para".
2. Tiene criterios de aceptación verificables (Gherkin).
3. Tiene diseño o wireframe si toca UI.
4. Sus dependencias están resueltas o planificadas antes.
5. Está estimada por el equipo y cabe en un sprint (≤ 8 SP; si no, se parte).
6. Se conocen los casos de error y los estados vacíos.

## 2. Definition of Done (DoD)

1. Código revisado por al menos otra persona y mergeado en `main`.
2. Tests unitarios de la lógica nueva; test de UI de los flujos críticos.
3. `detekt`, `ktlint`, `lint` y la suite completa en verde en CI.
4. Sin regresión de rendimiento en los macrobenchmarks.
5. Cadenas externalizadas en `strings.xml` (ES y EN) y accesibilidad verificada.
6. Probado en al menos 2 dispositivos físicos y en un Chromecast si aplica.
7. Aceptada por el PO en la demo.
8. Documentación/ADR actualizados si cambia una decisión.

## 3. Estrategia de pruebas

| Nivel | Alcance | Herramientas | Objetivo |
|---|---|---|---|
| Unitario | Parsers M3U/XMLTV, mapeadores, casos de uso, ViewModels | JUnit5, MockK, Turbine | Cobertura ≥ 80 % en `:domain` y `:data:*` |
| Integración | DAOs, migraciones, cliente Xtream con MockWebServer | Room testing, OkHttp MockWebServer | Todos los DAOs y migraciones |
| UI | Pantallas y navegación | Compose UI Test, Robolectric | Flujos críticos |
| E2E | Alta de fuente → lista → reproducción | Maestro | 5 recorridos por release |
| Rendimiento | Arranque, scroll, apertura de reproductor | Macrobenchmark | En CI nocturna |
| Manual | Cast, PiP, red inestable, dispositivos reales | Checklist de regresión | Antes de cada release |

**Datos de prueba**: repositorio interno con listas M3U reales anonimizadas, un XMLTV de
200 MB, un servidor Xtream simulado (MockWebServer con respuestas grabadas) y un servidor
HLS local para pruebas sin depender de proveedores externos.

**Matriz de dispositivos mínima**: Pixel 6a (Android 14), Galaxy A54 (One UI), Xiaomi
gama baja Android 10, tablet 10", Chromecast 3ª gen y Chromecast con Google TV.

## 4. Riesgos

| ID | Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|---|
| R1 | El Chromecast no reproduce MPEG-TS/HEVC/AC3 desde el receptor por defecto | Alta | Alto | HU-31 avisa al usuario; Fase 2 con proxy y remux (HU-45/46); telemetría para dimensionar el problema |
| R2 | Listas M3U con formatos no estándar rompen el parser | Alta | Medio | Parser tolerante, suite de 20 listas reales, fuzzing (HU-42) |
| R3 | Rechazo en Google Play por políticas de contenido | Media | Alto | Ficha explícita de "reproductor sin contenido", sin listas precargadas, sin enlaces a fuentes; revisión legal antes de la beta |
| R4 | Rendimiento pobre con listas de 100k canales | Media | Alto | Streaming + batch + Paging + FTS medidos desde el Sprint 1 |
| R5 | Proveedores que limitan conexiones simultáneas provocan fallos confusos | Media | Medio | Detección del error y mensaje específico; una sola conexión activa a la vez |
| R6 | Tráfico HTTP en claro y credenciales en la URL | Alta | Medio | Cifrado en Keystore, redacción en logs, network security config por dominio |
| R7 | La transcodificación de Fase 2 quema batería o no cabe en el APK | Media | Medio | Prototipo técnico (spike) antes del Sprint 8; FFmpeg como módulo dinámico |
| R8 | Fragmentación de códecs entre fabricantes | Media | Medio | Fallback de decodificador software en ExoPlayer y lista de exclusiones por dispositivo |
| R9 | Dependencia de un único dev senior para el reproductor | Media | Alto | Pair programming en Sprint 3, ADRs escritos, rotación de tareas |

**Spikes planificados**: `SPK-1` (Sprint 2, 3 días) compatibilidad real del Default Media
Receiver con una muestra de 50 streams; `SPK-2` (Sprint 6, 5 días) viabilidad de remux con
FFmpeg en gama media.

## 5. Métricas de producto y de ingeniería

| Métrica | Objetivo |
|---|---|
| Activación: usuarios que añaden una fuente con éxito | > 85 % |
| Tiempo desde instalación hasta primer canal reproducido | < 3 min p50 |
| Tasa de errores de reproducción por sesión | < 5 % |
| Sesiones con Cast que terminan en error | < 10 % |
| Crash-free sessions | > 99,5 % |
| Retención D7 | > 40 % |
| Lead time de una historia (ready → producción) | < 10 días |
| Fallos en CI de `main` | < 5 % de builds |

## 6. Cumplimiento y aspectos legales

- La app **no** incluye, indexa ni sugiere listas de canales; el usuario aporta las suyas.
- Sin listas precargadas ni funcionalidad de búsqueda de listas en internet.
- Política de privacidad publicada: los datos (credenciales, listas, historial) permanecen
  en el dispositivo; la telemetría es opcional y anónima.
- Cumplimiento de "Data safety" de Google Play y del RGPD (no hay tratamiento en servidor).
- Licencias OSS listadas en "Acerca de" (Media3, Cast SDK, OkHttp, Coil, etc.).
- El uso del Cast SDK exige registrar la aplicación en la Cast Developer Console y respetar
  las Google Cast SDK Terms.

## 7. Entrega y ramificación

- **Trunk-based** con ramas cortas `feat/HU-xx-descripcion`; PRs < 400 líneas.
- Versionado semántico; `main` siempre desplegable a test interno.
- Tracks: interno (cada merge) → cerrado (fin de sprint) → abierto (fin de Sprint 7).
- Rollback mediante *staged rollout* al 10 % y halt si crash-free < 99 %.
