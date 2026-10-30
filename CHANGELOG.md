# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Cada entrada describe el cambio orientado a usuario, no el detalle técnico.

## [Unreleased]

### Añadido
- Android TV / Google TV / Fire TV: la app aparece en el launcher del
  televisor con su banner y abre directamente reproduciendo el primer
  canal en directo a pantalla completa.
- Mando a distancia: canal +/- con las flechas arriba/abajo (zapping),
  lista de canales con la flecha izquierda, tarjeta del canal con la
  derecha o la tecla INFO, controles de reproducción con OK o MENU y
  entrada directa de número de canal con las teclas numéricas.
- Guía de programación en TV: la tecla GUIDE abre un panel "ahora y a
  continuación" sobre el vídeo con todos los canales, su programa en
  curso y el siguiente; la tarjeta post-zap también muestra qué se emite
  ahora y qué viene después.
- Menú contextual en TV: la tecla MENU abre las acciones sobre el canal
  en curso —lista de canales, guía, favorito, pistas de audio/subtítulos,
  temporizador de apagado y salir— navegables con el mando.
- Filtros en la lista de canales de TV: sobre el rail hay chips para ver
  todos los canales, sólo favoritos o una categoría concreta.

### Corregido
- Canales que no llegaban a emitir al Chromecast en cuentas con límite de
  conexiones del proveedor: el móvil ahora suelta su conexión antes de que
  el televisor pida el stream, y si el envío falla la reproducción continúa
  donde estaba.
- Canales en directo con HLS de segmentos fMP4/CMAF que fallaban al emitir:
  se detectan al analizar el stream y se declaran al Chromecast con el
  formato correcto.
- Canales DASH (.mpd) que no se veían al emitir: el proxy reescribe ahora
  las rutas de sus segmentos.

### Mejorado
- Menos pérdida de calidad al emitir playlists con varias calidades sin
  bitrate declarado: se estima a partir de la resolución para que el
  Chromecast distinga las variantes en vez de quedarse en la primera.
- Una conexión menos al proveedor al emitir canales Xtream: cuando la
  cuenta declara el formato del stream se evita un sondeo extra.

## [1.3.0] - 2026-10-30

### Añadido
- Búsqueda dentro de la guía: al buscar en TV en directo aparece una sección
  "En la guía" con las emisiones en curso o futuras que coinciden con el
  título, su canal y cuándo empiezan ("Ahora", "Hoy · 21:30", "Mañana…").
  Si el programa se emite en varios canales se agrupa en una sola fila.
- Personalización de canales (pulsación larga en la lista de TV): renombrar
  (la guía sigue casando por el nombre del proveedor), ocultar el canal en
  toda la app —reversible desde Ajustes— y reordenar por drag & drop dentro
  de favoritos o de su categoría (el menú pasa a un botón ⋮ en esas vistas).
- Renombrar y reordenar categorías por drag & drop desde la gestión de
  categorías en Ajustes, con opción de restaurar el nombre original. Los
  renombres, ocultaciones y órdenes manuales sobreviven a las
  resincronizaciones del proveedor.
- Búsqueda dentro del ámbito activo en TV en directo: con una categoría,
  favoritos o idioma seleccionados, el buscador filtra sólo ese conjunto de
  canales (el hint del campo indica el ámbito, p. ej. "Buscar en Deportes").
- Chip "Todas las categorías ▾" al inicio del rail de TV (cuando hay más de
  10): abre un listado con buscador para encontrar la categoría sin recorrer
  el rail a ciegas.

### Mejorado
- El reproductor ahora admite streams DASH (.mpd) y RTSP (rtsp://), además de
  HLS, MPEG-TS y MP4.
- Menos consumo de batería al ver la tele: el audio estándar (AAC/MP3) vuelve
  a decodificarse por hardware y FFmpeg queda como respaldo sólo cuando el
  dispositivo no tiene códec nativo (AC3/EAC3/DTS; también cubre vídeo
  MPEG-2 por software).
- Zapping más rápido: el reproductor reutiliza la conexión de red al cambiar
  entre canales del mismo proveedor.
- Recuperación automática cuando el proveedor anuncia un formato distinto al
  real (p. ej. vídeo TS servido en una URL .m3u8): el reproductor lo detecta
  y reintenta una vez con el formato correcto en vez de mostrar error.
- Las descargas de vídeo en formato HLS (.m3u8) fallan ahora con un motivo
  claro en vez de guardar un fichero inservible que además seguía consumiendo
  red al reproducirse. Al pulsar descargar se muestra un aviso explicando el
  motivo, y la lista de descargas detalla la causa del error.
- Emitir al televisor tarda menos al volver a un canal ya enviado: el
  resultado del análisis del stream queda memorizado durante la sesión.
- Corregido el envío a Chromecast de canales TS en directo en teléfonos de
  32 bits, donde fallaba siempre (FFmpegKit sólo distribuye binarios de
  64 bits): ahora recurre al cortador de paquetes en memoria.
- Los canales que exigen cabeceras propias (User-Agent/Referer vía
  #EXTVLCOPT) las aplican también al cambiar de canal y al emitir: antes el
  zapping y el Cast reutilizaban las del primer canal abierto.
- Corregido un cierre inesperado relacionado con Chromecast
  (RemoteServiceException en el servicio de notificaciones del SDK): se
  desactiva la notificación duplicada de Cast del SDK al existir ya la
  propia con controles de reproducción.
- La notificación de control de Chromecast sobrevive al salir de la app:
  antes desaparecía al quitar la tarea y la TV seguía emitiendo sin forma
  de pararlo desde el móvil.

### Otras

- Configurado `debugSymbolLevel=FULL` en release: los símbolos nativos se
  empaquetan dentro del AAB cuando las librerías los incluyen (el aviso de
  Play sobre código nativo sin símbolos proviene de ffmpeg-kit, que ya
  distribuye sus `.so` despojados).

## [1.2.0] - 2026-02-10

### Añadido
- Pantalla completa automática al girar el teléfono a horizontal durante la
  reproducción; al volver a vertical se sale sola.
- En horizontal los controles del reproductor se pliegan en un menú "⋮"
  (lista de canales, pistas, descarga, aspecto, PiP) y el botón de pantalla
  completa pasa a la esquina inferior derecha, junto a la barra de progreso.
- El tutorial se recorre deslizando además de con "Siguiente"; "Saltar"
  permite explorar la app completa sin añadir fuente (alta posterior desde
  Ajustes) y ya no queda atrapado en un bucle tutorial ↔ alta.

### Mejorado
- Reproductor: reconexión automática ante cortes de señal, búfer
  configurable y mensajes claros de señal inestable.
- Cabeceras de catálogo y guía compactas en horizontal (título y búsqueda en
  una fila); los banners de las fichas se acotan al 55 % de la altura.
- Favoritos instantáneos: la estrella responde al toque sin esperar el
  refresco de la base de datos.
- Scroll más fluido en catálogos: pósters estáticos por defecto (los GIF
  animados quedan para los logos de canal) y join de la guía fuera del hilo
  de interfaz. Jank medido en la parrilla de Películas: ~40 % → ~1.4 %.

### Corregido
- El destacado de Series ya no toma episodios/películas descargados (que no
  tienen ficha y fallaban al abrirse).
- La app ya no fuerza la pantalla de alta de fuente en cada arranque cuando
  el tutorial se completó sin fuente.

## [1.1.0] - 2026-02-02

### Añadido
- Descargas offline de películas y episodios de series (WorkManager),
  accesibles desde Ajustes con gestión de espacio y borrado confirmado.
- Botón "Probar canales demo" en el alta de fuente (lista pública iptv-org).
- Precarga en segundo plano de Películas/Series al cargar TV y
  sincronización automática de la EPG al abrir la app o si está obsoleta.
- Rebrand a CeroCast: nuevo icono adaptativo, nombre e `applicationId`.

### Mejorado
- Migración a AGP 9 con sync por lotes vía tabla staging y búsqueda FTS:
  catálogos Xtream de cientos de miles de entradas sincronizan sin colgar
  la app ni perder secciones si el proveedor devuelve partes vacías.
- Cambio de pestañas sin fundido ni parpadeo a vacío al volver.
- Emitir a Cast ya no se corta al salir del reproductor.

### Corregido
- La sincronización de la EPG ya no se queda en bucle infinito.
- El reproductor muestra indicador de carga y error con reintento cuando el
  stream no arranca.

## [1.0.0] - 2026-01-26

Primera publicación en Google Play.

- Reproducción de canales en directo, películas y series desde listas M3U y
  cuentas Xtream.
- Guía electrónica de programas (EPG/XMLTV) con filtro por favoritos.
- Chromecast, PiP, gestos de brillo/volumen y selector de pistas/subtítulos.
- Control parental por PIN sobre categorías.
- Sincronización automática del catálogo y la guía, con descarga sólo por
  Wi-Fi configurable.
