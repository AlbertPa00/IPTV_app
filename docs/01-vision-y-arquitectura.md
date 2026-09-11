# 01 · Visión, equipo y arquitectura

## 1. Visión de producto

**Para** usuarios que ya tienen una suscripción o lista IPTV propia,
**que** sufren apps sobrecargadas, lentas y con publicidad,
**IPTV App** es un reproductor Android
**que** carga la lista en menos de 30 segundos, permite zapear como en una TV normal y
enviar cualquier canal al televisor con un toque.
**A diferencia de** los reproductores genéricos, prioriza la simplicidad: tres pantallas
principales, búsqueda instantánea y Cast integrado en el reproductor.

### Principios de diseño

1. **Cero fricción de entrada**: de abrir la app a ver un canal en ≤ 3 toques.
2. **Una sola pantalla de reproducción**, idéntica en local y en Cast.
3. **Offline-first del catálogo**: la lista y la EPG se cachean; sin red se navega igual.
4. **Sin cuentas propias**: no hay backend de usuarios; los datos viven en el dispositivo.
5. **Accesible**: textos escalables, TalkBack, objetivos táctiles ≥ 48 dp.

### Personas

| Persona | Necesidad principal | Implicación de diseño |
|---|---|---|
| **Marta, 42** – contrató IPTV, poco técnica | Meter usuario/contraseña y ver la tele | Login Xtream con validación clara, sin jerga |
| **Luis, 30** – tiene un `.m3u` de su proveedor | Cargar el archivo y organizar favoritos | Importar desde fichero/URL, favoritos y orden |
| **Ana, 35** – ve en el salón | Mandar al televisor sin cables | Cast visible siempre, mini-controlador persistente |

### Fuera de alcance (MVP)

Descarga/grabación de contenido, catch-up/timeshift, Android TV, iOS, cuentas en la nube,
sincronización entre dispositivos, agregación de listas públicas.

---

## 2. Equipo (staff de ingeniería) y responsabilidades

Equipo de 6 personas, ceremonias Scrum, **sprints de 2 semanas**.

| Rol | FTE | Responsabilidad | Entregables clave |
|---|---|---|---|
| Product Owner | 0.5 | Backlog, prioridad, aceptación de historias | Backlog refinado, criterios de aceptación |
| Tech Lead / Arquitecto Android | 1.0 | Arquitectura, ADRs, revisión de PRs, reproductor | ADRs, módulo `player`, guía de estilo |
| Android Dev Sr. | 1.0 | Datos: parsers M3U/XMLTV, Xtream, Room, sync | Módulos `data-*`, repositorios |
| Android Dev Mid | 1.0 | UI Compose, design system, navegación | Módulo `designsystem`, features UI |
| QA / SDET | 0.5 | Plan de pruebas, automatización, matriz de dispositivos | Suites UI, informes de regresión |
| UX/UI Designer | 0.5 | Flujos, wireframes, tokens visuales, accesibilidad | Figma, especificaciones de componentes |
| DevOps (compartido) | 0.2 | CI/CD, firma, distribución Play | Pipeline, tracks internos/beta |

**Capacidad estimada**: ~34 story points por sprint una vez estabilizada la velocidad
(Sprint 1-2 se planifican al 70 % por incertidumbre).

### Ceremonias

- Planning (2 h, inicio de sprint) · Daily (15 min) · Refinement (1 h, mitad de sprint)
- Review con demo en dispositivo real y Chromecast (1 h) · Retro (45 min)

---

## 3. Arquitectura

### 3.1 Vertical Slice Architecture

La aplicación es un **monolito modular**: se distribuye como un único APK, pero el código se
organiza por capacidades de negocio. Cada `:feature:*` contiene de extremo a extremo la UI,
el estado, los casos de uso y los adaptadores específicos de su slice.

```
┌──────────────────────────── :app ─────────────────────────────┐
│ MainActivity · navegación raíz · composición de slices · DI   │
└───────┬──────────────────┬──────────────────┬─────────────────┘
        │                  │                  │
┌───────▼────────┐ ┌───────▼────────┐ ┌──────▼─────────┐
│ feature:source │ │feature:catalog │ │feature:player  │
│ UI/ViewModel   │ │ UI/ViewModel   │ │ UI/ViewModel   │
│ M3U + Xtream   │ │ Paging/filtros │ │ Media3 engine  │
│ Repository     │ │ Favoritos      │ │ Errores        │
└───────┬────────┘ └───────┬────────┘ └──────┬─────────┘
        └──────────────────┼─────────────────┘
                    ┌──────▼──────────────┐
                    │ core: infraestructura│
                    │ common · storage    │
                    │ network · design    │
                    └─────────────────────┘
```

Reglas de dependencia:

1. `:app` puede depender de todos los slices para componer la aplicación.
2. Un slice no depende de otro slice; la comunicación futura usa contratos mínimos o eventos.
3. Los slices pueden depender de `:core:*`, que no contiene lógica de negocio.
4. No existe un módulo `:domain` global: evita un modelo compartido que acople todo el sistema.
5. Los detalles privados de cada slice permanecen bajo paquetes `data`, `domain` y `ui` locales.

### 3.2 Módulos Gradle implementados

```
:app                  shell, navegación raíz y composición Hilt
:core:common          dispatchers y normalización de texto
:core:designsystem    tema Material 3 y estados comunes Compose
:core:network         OkHttp, JSON y configuración de red
:core:storage         Room, DAOs, entidades persistidas y cifrado Keystore
:feature:source       onboarding, alta M3U/Xtream y gestión de fuentes
:feature:catalog      categorías, búsqueda, Paging 3 y favoritos
:feature:player       reproducción Media3 y manejo de errores
```

Los futuros `:feature:epg` y `:feature:cast` se incorporan como slices independientes. Esta
división permite trabajo paralelo, tests por capacidad y builds incrementales sin convertir
cada capa técnica en un módulo transversal.

### 3.3 Stack técnico

| Área | Elección | Motivo |
|---|---|---|
| Lenguaje | Kotlin + Coroutines/Flow | Estándar Android |
| UI | Jetpack Compose + Material 3 | Velocidad de iteración, tema dinámico |
| Navegación | Navigation Compose (type-safe routes) | Sencillez |
| DI | Hilt | Integración con ViewModel/WorkManager |
| Reproducción | AndroidX **Media3** (ExoPlayer) | HLS, DASH, MPEG-TS progresivo, RTSP |
| Cast | Google Cast SDK v3 + `media3-cast` (`CastPlayer`) | Player común local/remoto |
| Persistencia | Room (+ FTS4 para búsqueda) + Paging 3 | Listas de 10k-100k canales |
| Preferencias | DataStore Proto | Tipado y asíncrono |
| Credenciales | AES-GCM con clave en Android Keystore | Contraseñas Xtream nunca en claro |
| Red | OkHttp + Retrofit + kotlinx.serialization | Estándar, interceptores de UA/timeout |
| Imágenes | Coil | Logos de canal, caché en disco |
| Trabajo en segundo plano | WorkManager | Refresco de lista y EPG |
| Tests | JUnit5, Turbine, MockK, Robolectric, Compose UI Test, Maestro | Pirámide completa |
| CI | GitHub Actions | Lint + detekt + tests + AAB firmado |

### 3.4 Modelo de datos (Room)

```
Source(id, type[M3U_URL|M3U_FILE|XTREAM], name, url, host, port, username,
       passwordRef, userAgent, lastSyncAt, status, epgUrl)

Category(id, sourceId, kind[LIVE|VOD|SERIES], externalId, name, order)

Channel(id, sourceId, categoryId, externalId, name, streamUrl, logoUrl,
        tvgId, groupTitle, kind, containerExt, order, isFavorite, lastPlayedAt)
  índices: (sourceId, categoryId), (tvgId); tabla FTS: ChannelFts(name)

Programme(id, sourceId, tvgId, title, description, startUtc, endUtc, category, iconUrl)
  índice: (tvgId, startUtc)

PlaybackHistory(channelId, positionMs, durationMs, updatedAt)
```

Regla de sincronización: borrado e inserción por `sourceId` dentro de una transacción,
preservando `isFavorite` y `lastPlayedAt` mediante `upsert` por `(sourceId, externalId)`.

### 3.5 Flujos principales

**Importar M3U**
```
UI → ImportPlaylistUseCase → descarga en streaming (OkHttp source)
   → M3uParser (línea a línea, sin cargar el fichero en memoria)
   → inserción por lotes de 500 en Room → progreso emitido por Flow → UI
```

**Login Xtream**
```
UI(host,user,pass) → LoginXtreamUseCase → GET player_api.php?username&password
   → valida user_info.auth == 1 y status == "Active"
   → guarda Source + credencial cifrada
   → sync: get_live_categories, get_live_streams, get_vod_*, get_series
   → EPG: xmltv.php (gzip) o get_short_epg por canal
URL de stream: {scheme}://{host}:{port}/live/{user}/{pass}/{streamId}.{ext}
```

**Reproducción y Cast**
```
CastContext.sessionState
   ├ sin sesión  → ExoPlayer local (MediaSessionService, PiP, audio focus)
   └ con sesión  → CastPlayer.setMediaItem(url + MediaMetadata) → Default Media Receiver
Cambio de sesión → transferencia de posición y estado entre players (interfaz Player común)
```

### 3.6 Mapa de pantallas

```
Onboarding ──► Añadir fuente (M3U URL | Archivo | Xtream)
                    │
                    ▼
        ┌──── Inicio (bottom bar) ─────┐
        │  Canales   Guía   Ajustes    │
        └──────────────┬───────────────┘
                       ▼
        Reproductor (fullscreen, gestos, Cast, PiP)
```

Máximo 3 destinos en la barra inferior; búsqueda global en la barra superior;
mini-controlador de Cast anclado sobre la barra inferior cuando hay sesión.

---

## 4. Decisiones de arquitectura (ADR resumidos)

### ADR-001 · Compose + Media3 sobre Views + libVLC
**Decisión**: Media3/ExoPlayer.
**Motivo**: integración nativa con Cast, MediaSession, PiP y descarga; APK ~15 MB frente a
los ~40 MB de libVLC con todos los ABIs.
**Coste**: menor tolerancia a streams mal formados y a códecs raros (AC3/EAC3 dependen del
dispositivo; MPEG-2 vídeo no está garantizado).
**Mitigación**: capa `PlayerEngine` con implementación alternativa libVLC evaluada en Fase 2
si la telemetría muestra > 3 % de fallos de reproducción.

### ADR-002 · Cast directo en el MVP, proxy tipo VLC en Fase 2
**Contexto**: VLC no "hace Cast" de forma nativa; levanta un servidor HTTP local, remuxea o
transcodifica el stream a un formato que entiende el receptor y le pasa esa URL.
**Decisión MVP**: enviar la URL original al Default Media Receiver (`CC1AD845`) vía Cast SDK.
Funciona con HLS (H.264/AAC), DASH y MP4, que cubre la mayoría de listas.
**Limitación conocida y explícita**: streams MPEG-TS progresivos, HEVC, MPEG-2 o pistas
AC3/EAC3 pueden fallar según el modelo de Chromecast. La app detectará el error del receptor
y mostrará un mensaje claro con opción de "reproducir en el móvil".
**Fase 2 (ADR-002b)**: `LocalHttpServer` (NanoHTTPD o Ktor) + remux con FFmpeg/`mobile-ffmpeg`
a fMP4/HLS, y transcodificación sólo si el códec no es compatible. Se documentan los costes:
batería, calor, CPU y tamaño del APK (+20 MB por FFmpeg).

### ADR-003 · Receptor Cast por defecto vs receptor propio
MVP con **Styled Media Receiver** (sólo requiere registrar un App ID y una hoja de estilos):
branding sin mantener una web app. Un receptor CAF propio queda para Fase 2 si se necesita
mostrar EPG o listas en pantalla.

### ADR-004 · Sin backend propio
Todo local. Ventajas: cero coste operativo, sin datos personales en servidores, sin
responsabilidad sobre contenidos de terceros. Consecuencia: no hay sincronización entre
dispositivos; la copia de seguridad se hace exportando un fichero de configuración cifrado.

### ADR-005 · Tráfico en claro
La mayoría de portales IPTV sirven por `http://` y sus dominios sólo se conocen en tiempo de
ejecución. Android no permite añadir dominios dinámicos al `network_security_config.xml`, por
lo que el MVP habilita tráfico claro globalmente para poder reproducirlos. No hay dominios ni
listas precargados; la fuente siempre la introduce el usuario. La UI debe avisar cuando una
fuente no usa TLS y ninguna URL o credencial se registra en logs.

### ADR-006 · Rendimiento con listas enormes
Parseo en streaming + inserción por lotes + Paging 3 + búsqueda normalizada indexada.
Objetivo: 50.000 canales importados en < 20 s y scroll a 60 fps en gama media
(Pixel 6a / Galaxy A54 como dispositivos de referencia).

### ADR-007 · Vertical Slice Architecture como monolito modular
**Decisión**: organizar el código por capacidades (`source`, `catalog`, `player`, después
`epg` y `cast`) y generar un único APK. Cada slice puede contener UI, aplicación y datos
específicos. Sólo la infraestructura verdaderamente transversal vive en `core`.

**Motivo**: una arquitectura horizontal por capas hace que cada historia modifique varios
módulos técnicos y aumenta la coordinación. Los slices concentran el cambio, tienen ownership
claro y pueden evolucionar o probarse de forma independiente.

**Restricciones**: los slices no se importan entre sí y `app` actúa como composition root. Si
dos slices necesitan datos comunes, se comparte el contrato mínimo; no se mueve lógica de
negocio a `core` por conveniencia.

---

## 5. Requisitos no funcionales

| ID | Requisito | Medida |
|---|---|---|
| RNF-01 | Arranque en frío | < 1,5 s hasta contenido en gama media |
| RNF-02 | Importar 10.000 canales | < 8 s |
| RNF-03 | Tiempo hasta primer fotograma | < 2,5 s p50, < 5 s p90 en HLS |
| RNF-04 | Zapping entre canales | < 2 s |
| RNF-05 | Tasa de crashes | < 0,5 % de sesiones |
| RNF-06 | Tamaño de descarga | < 20 MB (AAB, split por ABI) |
| RNF-07 | Accesibilidad | TalkBack completo, contraste AA, texto hasta 200 % |
| RNF-08 | minSdk / targetSdk | 24 / última estable |
| RNF-09 | Idiomas | ES y EN desde el MVP |
| RNF-10 | Privacidad | Credenciales cifradas; sin telemetría sin consentimiento |
