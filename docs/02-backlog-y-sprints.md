# 02 · Backlog, roadmap e historias de usuario

Formato de historia: `HU-xx` · título · **Como** rol / **quiero** / **para**.
Estimación en story points (escala Fibonacci, 1 SP ≈ media jornada de un dev).
Criterios de aceptación en Gherkin (Dado / Cuando / Entonces).

## Épicas

| ID | Épica | Descripción |
|---|---|---|
| E1 | Gestión de fuentes | Alta, validación y sincronización de listas M3U y cuentas Xtream |
| E2 | Navegación de catálogo | Categorías, canales, VOD, series, búsqueda, favoritos |
| E3 | Reproducción | Reproductor Media3, controles, pistas, calidad, PiP |
| E4 | Guía EPG | XMLTV / EPG Xtream, "ahora y después", parrilla |
| E5 | Chromecast | Cast SDK, mini-controlador, transferencia local↔remoto |
| E6 | Personalización | Favoritos, historial, control parental, ajustes, temas |
| E7 | Plataforma y calidad | Arquitectura, CI/CD, rendimiento, accesibilidad, publicación |
| E8 | Cast avanzado (Fase 2) | Proxy HTTP local con remux/transcodificación estilo VLC |

## Roadmap

| Sprint | Duración | Objetivo | SP |
|---|---|---|---|
| 0 | 1 sem | Cimientos técnicos y design system | 21 |
| 1 | 2 sem | Añadir lista M3U y ver el catálogo | 26 |
| 2 | 2 sem | Login Xtream Codes y catálogo completo | 31 |
| 3 | 2 sem | Reproductor sólido | 34 |
| 4 | 2 sem | Guía EPG | 29 |
| 5 | 2 sem | Chromecast | 31 |
| 6 | 2 sem | Favoritos, búsqueda, parental, ajustes | 29 |
| 7 | 2 sem | Endurecimiento, accesibilidad y beta pública | 26 |
| 8-9 | 4 sem | Fase 2: proxy Cast tipo VLC + Android TV | 55 |

**MVP publicable = fin de Sprint 7** (≈ 15 semanas desde el arranque).

---

# Sprint 0 · Cimientos (21 SP)

**Objetivo**: cualquier dev puede clonar, compilar y desplegar una app vacía pero con
arquitectura, CI y design system listos.

### HU-01 · Esqueleto multi-módulo (5 SP) · E7
**Como** desarrollador **quiero** un proyecto Gradle multi-módulo con Hilt y Compose
**para** empezar a implementar features sin discutir estructura.
- Dado el repo clonado, cuando ejecuto `./gradlew assembleDebug`, entonces compila en < 3 min.
- La estructura de módulos coincide con la del documento de arquitectura.
- Version catalog (`libs.versions.toml`) centraliza todas las dependencias.
- Convention plugins en `build-logic` evitan duplicar configuración.
- Existe un test unitario y uno instrumentado de humo que pasan.

### HU-02 · Pipeline de CI (3 SP) · E7
**Como** tech lead **quiero** validación automática en cada PR **para** proteger `main`.
- Dado un PR abierto, cuando se ejecuta el workflow, entonces corren `detekt`, `ktlint`,
  `lint`, tests unitarios y `assembleDebug`.
- El merge está bloqueado si algún job falla.
- El resultado de tests se publica como comentario en el PR.

### HU-03 · Design system base (5 SP) · E7
**Como** diseñadora **quiero** tokens y componentes reutilizables **para** que la UI sea
coherente desde el primer día.
- Tema Material 3 con modo claro/oscuro y color dinámico (Android 12+).
- Componentes: `AppTopBar`, `ChannelRow`, `ChannelGrid`, `EmptyState`, `ErrorState`,
  `LoadingState`, `PrimaryButton`, `AppTextField`, `PinDots`.
- Todos los componentes tienen `@Preview` en claro y oscuro.
- Contraste mínimo AA verificado con Accessibility Scanner.

### HU-04 · Navegación y esqueleto de pantallas (3 SP) · E7
- Bottom bar con Canales / Guía / Ajustes y pantallas placeholder.
- El estado de navegación sobrevive a rotación y a muerte de proceso.
- Rutas tipadas; deep link `iptvapp://channel/{id}` registrado.

### HU-05 · Base de datos y persistencia (5 SP) · E7
- Entidades y DAOs de `Source`, `Category`, `Channel`, `Programme`, `PlaybackHistory`.
- Migraciones activadas con esquemas exportados y test de migración 1→2.
- `DataStore Proto` para preferencias y almacén cifrado (AES-GCM + Keystore) con tests.

---

# Sprint 1 · Listas M3U (26 SP)

**Objetivo**: el usuario añade una lista M3U y navega sus canales. *Demo: importar una
lista de 5.000 canales y verla organizada por grupos.*

### HU-06 · Onboarding de bienvenida (3 SP) · E1
**Como** usuario nuevo **quiero** entender en 10 segundos qué necesito **para** no
abandonar en la primera pantalla.
- Dado que abro la app por primera vez, entonces veo 2 pantallas: qué es la app y
  "añade tu lista o tus datos de acceso".
- Aviso legal breve: la app no proporciona contenido.
- Botón "Empezar" lleva a la pantalla de añadir fuente; el onboarding no vuelve a mostrarse.
- Es navegable con TalkBack y se puede saltar.

### HU-07 · Añadir lista M3U por URL (8 SP) · E1
**Como** usuario **quiero** pegar la URL de mi lista **para** cargar mis canales.
- Dado el formulario, cuando pego una URL válida y pulso "Añadir", entonces veo progreso
  con número de canales procesados y al terminar la lista de canales.
- Cuando la URL es inválida o no responde (timeout 15 s), entonces veo un mensaje concreto
  ("No se pudo conectar", "La lista está vacía", "Formato no reconocido") y botón "Reintentar".
- Se soportan atributos `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`, `#EXTVLCOPT`
  (`http-user-agent`, `http-referrer`) y `#EXTGRP`.
- Se soporta gzip y respuestas sin `Content-Length`.
- El parseo es en streaming; una lista de 50 MB no provoca OOM (test con perfil de memoria).
- La contraseña embebida en la URL nunca aparece en logs.
- **Técnico**: `M3uParser` con 15 tests sobre ficheros reales de distintos proveedores.

### HU-08 · Añadir lista desde archivo local (3 SP) · E1
- Selector del sistema (SAF) filtrando `.m3u`, `.m3u8`, `*/*`.
- El fichero se copia al almacenamiento interno para poder recargarlo.
- Se detecta la codificación (UTF-8 con y sin BOM, Latin-1).

### HU-09 · Lista de canales con categorías (8 SP) · E2
**Como** usuario **quiero** ver mis canales agrupados **para** encontrarlos rápido.
- Chips horizontales de categorías (`group-title`), con "Todos" al principio.
- Cada fila muestra logo (placeholder si falla), nombre y número de canal.
- Paging 3 sobre Room: scroll a 60 fps con 20.000 canales (medido con Macrobenchmark).
- Selector local/grid persistido en preferencias.
- La posición de scroll y la categoría seleccionada se conservan al volver atrás.

### HU-10 · Gestión de fuentes (4 SP) · E1
- Pantalla con las fuentes añadidas: nombre, tipo, nº de canales, última sincronización.
- Acciones: actualizar, renombrar, eliminar (con confirmación) y marcar como activa.
- Al eliminar una fuente se borran sus canales y su EPG en una sola transacción.
- Se permiten varias fuentes simultáneas.

---

# Sprint 2 · Xtream Codes (31 SP)

**Objetivo**: login con usuario/contraseña y catálogo Live + VOD + Series.

### HU-11 · Login Xtream Codes (8 SP) · E1
**Como** usuario con suscripción **quiero** entrar con mi servidor, usuario y contraseña
**para** no tener que manejar URLs largas.
- Campos: servidor (acepta `http://host:puerto` o `host`), usuario, contraseña (con
  botón mostrar/ocultar) y nombre opcional del perfil.
- Cuando las credenciales son correctas, entonces se guarda la fuente y se sincroniza.
- Cuando `auth != 1` entonces "Usuario o contraseña incorrectos"; cuando
  `status != Active` entonces "Tu cuenta está caducada o suspendida".
- Se muestran fecha de caducidad y conexiones máximas/activas en el detalle de la fuente.
- La contraseña se guarda cifrada (Keystore) y nunca se escribe en logs ni en crash reports.
- Se detecta automáticamente el puerto si se omite (80/8080 y https).

### HU-12 · Sincronización del catálogo Xtream (8 SP) · E1
- Se descargan categorías y streams de Live, VOD y Series con `player_api.php`.
- La sincronización es incremental por `sourceId` y conserva favoritos e historial.
- Progreso por fases visible ("Canales 1.240 / 8.900").
- Si la sincronización falla a mitad, el catálogo anterior sigue intacto (transacción).
- `WorkManager` programa un refresco cada 24 h con red disponible y batería no baja.

### HU-13 · Pestañas TV / Películas / Series (5 SP) · E2
- Tres secciones dentro de Canales cuando la fuente es Xtream; sólo TV cuando es M3U plano.
- Películas y Series en grid con póster (Coil), título y año.
- Estado vacío específico por sección.

### HU-14 · Detalle de película y de serie (5 SP) · E2
- Detalle VOD: póster, sinopsis, duración, género, calidad, botón "Reproducir".
- Detalle de serie: temporadas plegables y episodios con miniatura y descripción.
- Los datos se piden bajo demanda (`get_vod_info`, `get_series_info`) y se cachean 7 días.

### HU-15 · Búsqueda global (5 SP) · E2
- Búsqueda desde la barra superior con resultados en < 200 ms sobre 50.000 elementos (FTS4).
- Resultados agrupados por tipo (TV / Películas / Series) con debounce de 250 ms.
- Historial de las 10 últimas búsquedas, borrable.
- Búsqueda insensible a acentos y mayúsculas.

---

# Sprint 3 · Reproductor (34 SP)

**Objetivo**: reproducción estable de HLS y MPEG-TS con controles completos.

### HU-16 · Reproducción de canal en directo (8 SP) · E3
**Como** usuario **quiero** tocar un canal y verlo **para** empezar a usar la app.
- Dado un canal, cuando lo pulso, entonces se abre el reproductor a pantalla completa en
  horizontal y comienza la reproducción en < 2,5 s (p50).
- Se soportan HLS (`.m3u8`), MPEG-TS progresivo (`.ts`) y MP4; RTSP se intenta y, si falla,
  se informa del formato no soportado.
- Se aplican el `User-Agent` y el `Referer` definidos en la fuente.
- El buffer se configura para directo (LiveConfiguration, target 3 s, sin rebobinado infinito).
- La reproducción sobrevive a rotación sin reiniciar (`MediaSessionService`).

### HU-17 · Controles del reproductor (5 SP) · E3
- Overlay con: título, nombre de la categoría, play/pausa, siguiente/anterior canal,
  botón de pantalla completa, ajustes y botón Cast.
- Auto-ocultado a los 4 s; reaparece al tocar.
- Gestos: deslizar vertical izquierda = brillo, derecha = volumen, doble toque en VOD = ±10 s.
- Barra de progreso sólo en VOD; indicador "EN DIRECTO" en Live.

### HU-18 · Zapping y lista lateral (5 SP) · E3
- Deslizar horizontal (o botones) cambia al canal anterior/siguiente de la categoría actual.
- Panel lateral desplegable con la lista de canales sin salir de la reproducción.
- El cambio de canal tarda < 2 s y no deja audio del canal anterior.

### HU-19 · Pistas de audio, subtítulos y calidad (5 SP) · E3
- Menú con pistas de audio, subtítulos (incluye "Desactivar") y calidad/bitrate en HLS.
- La selección de idioma preferido se recuerda entre sesiones.
- Estilo de subtítulos configurable (tamaño y fondo) respetando los ajustes del sistema.

### HU-20 · Manejo de errores de reproducción (5 SP) · E3
**Como** usuario **quiero** entender por qué no se ve un canal **para** no pensar que la app
está rota.
- Mensajes diferenciados: sin conexión, canal caído (404/403), formato no soportado,
  códec no soportado, límite de conexiones del proveedor alcanzado.
- Reintento automático con backoff exponencial (3 intentos) ante errores de red.
- Botón "Reintentar" y "Reportar canal caído" (marca local que ordena el canal al final).

### HU-21 · Reproducción en segundo plano y PiP (3 SP) · E3
- Modo Picture-in-Picture al pulsar inicio durante la reproducción (Android 8+).
- Notificación de media con controles y carátula; gestión correcta del foco de audio
  (pausa con llamada entrante, baja volumen con notificaciones).

### HU-22 · Continuar viendo (3 SP) · E6
- La posición de VOD y episodios se guarda cada 10 s y al salir.
- Carrusel "Continuar viendo" en la parte superior de Películas/Series.
- Un contenido visto al 95 % desaparece del carrusel.

---

# Sprint 4 · Guía EPG (29 SP)

### HU-23 · Importar EPG XMLTV (8 SP) · E4
- Se acepta una URL XMLTV por fuente (autodetección de `url-tvg` en la cabecera M3U y de
  `xmltv.php` en Xtream).
- Parseo en streaming con `XmlPullParser`, soporte de `.gz`, y de zonas horarias
  (`+0100`, `Z`) normalizando todo a UTC.
- Un XMLTV de 200 MB se procesa sin OOM y en < 60 s en gama media.
- Los programas anteriores a 24 h se purgan automáticamente.
- El emparejamiento canal↔EPG usa `tvg-id`, con fallback por nombre normalizado.

### HU-24 · "Ahora y después" en la lista de canales (5 SP) · E4
- Cada fila muestra el programa actual, su hora de fin y una barra de progreso.
- El siguiente programa aparece en texto secundario.
- Si no hay EPG para el canal, la fila no se rompe (sólo nombre).
- La información se actualiza sin recomponer toda la lista (Flow por minuto).

### HU-25 · Parrilla de programación (8 SP) · E4
- Vista de rejilla con canales en vertical y tiempo en horizontal, scroll sincronizado.
- Línea de "ahora" visible; navegación por días (-1 a +7).
- Toque en programa actual = reproducir; en futuro = detalle.
- Fluido con 500 canales visibles (LazyLayout con reciclado en ambos ejes).

### HU-26 · Detalle de programa y recordatorio (5 SP) · E4
- Detalle con título, horario, duración, descripción y categoría.
- Botón "Recordármelo": notificación 5 min antes (AlarmManager exacto con permiso pedido en
  contexto) y acción "Ver ahora".
- Lista de recordatorios gestionable desde Ajustes.

### HU-27 · Refresco automático de EPG (3 SP) · E4
- `WorkManager` diario, sólo con Wi-Fi si el usuario lo configura así.
- La fecha del último refresco es visible y hay botón de refresco manual.

---

# Sprint 5 · Chromecast (31 SP)

**Objetivo**: enviar cualquier canal compatible al televisor con un toque.

### HU-28 · Integración del Cast SDK (8 SP) · E5
**Como** usuario **quiero** ver el icono de Cast **para** saber que puedo enviar al televisor.
- `CastOptionsProvider` con Styled Media Receiver registrado en la Cast Developer Console.
- Botón Cast en la barra superior y en el reproductor; aparece sólo si hay dispositivos.
- El diálogo de selección lista los dispositivos de la red en < 5 s.
- Permisos de red local (Android 13+ `NEARBY_WIFI_DEVICES`) solicitados con explicación.

### HU-29 · Reproducir en el televisor (8 SP) · E5
- Dado un canal en reproducción, cuando conecto un Chromecast, entonces continúa en el
  televisor desde la misma posición y el móvil muestra la pantalla de "reproduciendo en X".
- Se envían metadatos: título, categoría, logo y tipo (`LIVE` vs `MOVIE`) al receptor.
- Al desconectar, la reproducción vuelve al móvil en la misma posición.
- **Técnico**: `CastPlayer` de `media3-cast` detrás de la misma interfaz `Player`, con
  `SessionAvailabilityListener` para conmutar.

### HU-30 · Mini-controlador y control remoto (5 SP) · E5
- Mini-controlador persistente sobre la barra inferior mientras hay sesión.
- Controles ampliados (`ExpandedControllerActivity`) con play/pausa, volumen, canal
  anterior/siguiente y desconectar.
- Controles disponibles desde la pantalla de bloqueo y la notificación.

### HU-31 · Diagnóstico de incompatibilidad en Cast (5 SP) · E5
**Como** usuario **quiero** saber por qué un canal no se ve en la tele **para** no culpar a
la app (limitación conocida del receptor por defecto).
- Cuando el receptor devuelve un error de carga o de códec, entonces se muestra
  "Este canal usa un formato que tu Chromecast no admite" con los botones
  "Ver en el móvil" y "Más información".
- El motivo se registra localmente (códec/contenedor) para priorizar el proxy de la Fase 2.
- Antes de enviar, se avisa si la URL es `.ts` progresivo o el `container_extension`
  declarado es notoriamente incompatible.

### HU-32 · Cola y zapping en Cast (5 SP) · E5
- Cambio de canal estando en Cast sin desconectar la sesión (< 3 s).
- La lista lateral de canales sigue operativa en modo Cast.
- Si la sesión se pierde (TV apagada), la app lo detecta y ofrece reanudar en el móvil.

---

# Sprint 6 · Personalización (29 SP)

### HU-33 · Favoritos (5 SP) · E6
- Marcar/desmarcar desde la lista, el detalle y el reproductor (icono corazón).
- Categoría "Favoritos" fija al principio de los chips.
- Reordenación por arrastre y persistencia del orden.
- Los favoritos sobreviven a la resincronización de la fuente.

### HU-34 · Ocultar y reordenar categorías (3 SP) · E6
- Pantalla de gestión con conmutadores y arrastre.
- Las categorías ocultas desaparecen de listas, búsqueda y guía.

### HU-35 · Control parental (8 SP) · E6
**Como** padre **quiero** proteger ciertos contenidos con un PIN **para** que mis hijos no
accedan a ellos.
- PIN de 4 dígitos configurable, almacenado con hash + salt (nunca en claro).
- Categorías o canales concretos marcables como "bloqueado"; se muestran difuminados y
  piden PIN para reproducir.
- Bloqueo automático opcional de categorías marcadas como adultas por el proveedor.
- Tras 5 intentos fallidos, espera de 60 s.
- Recuperación del PIN sólo reinstalando o borrando datos (advertido en la UI).

### HU-36 · Ajustes (5 SP) · E6
- Tema (sistema/claro/oscuro), idioma, calidad preferida, User-Agent personalizado,
  buffer, autoarranque del último canal, refresco automático, borrar caché.
- Exportar/importar configuración en un fichero cifrado con contraseña.
- Pantalla "Acerca de" con versión, licencias (OSS) y aviso legal.

### HU-37 · Historial de reproducción (3 SP) · E6
- Últimos 50 elementos con acceso rápido y opción de borrar todo.
- Desactivable desde ajustes.

### HU-38 · Widget y accesos directos (5 SP) · E6
- Accesos directos dinámicos (long-press en el icono) a los 4 canales más vistos.
- Widget 2x2 con favoritos que abre directamente el reproductor.

---

# Sprint 7 · Endurecimiento y lanzamiento (26 SP)

### HU-39 · Accesibilidad completa (5 SP) · E7
- Todos los elementos interactivos con `contentDescription` y objetivos ≥ 48 dp.
- Navegación completa con TalkBack y con teclado/D-pad.
- La UI no se rompe con tamaño de fuente al 200 % ni en pantalla dividida.
- Accessibility Scanner sin incidencias críticas.

### HU-40 · Rendimiento y baseline profiles (5 SP) · E7
- Macrobenchmarks de arranque, scroll de canales y apertura del reproductor en CI.
- Baseline Profile generado y empaquetado (objetivo: −20 % en arranque).
- Sin fugas de memoria detectadas por LeakCanary en un recorrido completo.

### HU-41 · Telemetría y crash reporting opcionales (3 SP) · E7
- Diálogo de consentimiento explícito en el primer arranque; desactivado por defecto.
- Eventos anónimos: fallo de reproducción (con códec/contenedor), error de Cast, tiempo
  hasta primer fotograma. Nunca URLs, credenciales ni nombres de canal.

### HU-42 · Robustez ante listas defectuosas (5 SP) · E7
- Suite de 20 listas M3U reales/corruptas: la app nunca crashea y siempre da un mensaje útil.
- Fuzzing básico del parser XMLTV.
- Timeouts y cancelación correcta de descargas al salir de la pantalla.

### HU-43 · Ficha de Play Store y beta cerrada (5 SP) · E7
- AAB firmado, R8 activo con reglas verificadas, política de privacidad publicada.
- Ficha con capturas, descripción y declaración de contenido (la app no aporta canales).
- Track de test interno con 20 testers y formulario de feedback.
- Declaración de "Data safety" coherente con la telemetría real.

### HU-44 · Documentación de release (3 SP) · E7
- `AGENTS.md`/`CONTRIBUTING.md` con comandos de build, test y release.
- Runbook de publicación y de rollback; versionado semántico y changelog.

---

# Fase 2 · Sprints 8-9 (55 SP)

### HU-45 · Servidor HTTP local para Cast (13 SP) · E8
**Como** usuario **quiero** que también lleguen al televisor los canales que el Chromecast
no entiende **para** no tener que ver nada en el móvil.
- Servidor HTTP embebido (Ktor) que escucha en la IP de la Wi-Fi, con token de sesión
  aleatorio en la ruta y sin exponerse fuera de la red local.
- El Chromecast recibe la URL del proxy en lugar de la original.
- Se libera el puerto y se detiene el servidor al cerrar la sesión de Cast.
- WakeLock parcial mientras hay retransmisión y aviso de consumo de batería.

### HU-46 · Remux y transcodificación bajo demanda (21 SP) · E8
- Se analiza el stream (FFprobe) y se decide: *passthrough* → *remux a fMP4/HLS* →
  *transcodificación* sólo si el códec es incompatible.
- Transcodificación con aceleración por hardware (MediaCodec) cuando esté disponible.
- Downmix de AC3/EAC3 a AAC estéreo cuando el receptor no soporte passthrough.
- Latencia añadida < 4 s y CPU media < 40 % en gama media durante remux.
- Indicador en la UI del modo activo (Directo / Remux / Transcodificando).

### HU-47 · Soporte Android TV (13 SP) · E7
- Leanback launcher, navegación por D-pad, filas de canales y buscador por voz.
- El reproductor y la parrilla funcionan sin pantalla táctil.

### HU-48 · Catch-up y timeshift (8 SP) · E4
- Si el proveedor lo expone (`tvg-rec`, API Xtream de timeshift), se permite retroceder en
  el directo y ver programas pasados desde la parrilla.

---

## Trazabilidad épica → historias

| Épica | Historias |
|---|---|
| E1 Fuentes | 06, 07, 08, 10, 11, 12 |
| E2 Catálogo | 09, 13, 14, 15 |
| E3 Reproducción | 16, 17, 18, 19, 20, 21 |
| E4 EPG | 23, 24, 25, 26, 27, 48 |
| E5 Cast | 28, 29, 30, 31, 32 |
| E6 Personalización | 22, 33, 34, 35, 36, 37, 38 |
| E7 Plataforma | 01-05, 39, 40, 41, 42, 43, 44, 47 |
| E8 Cast avanzado | 45, 46 |
