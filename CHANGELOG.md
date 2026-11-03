# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Cada entrada describe el cambio orientado a usuario, no el detalle técnico.

## [Unreleased]

### Añadido
- Botón de pistas (audio, subtítulos y calidad de vídeo) en la barra de
  controles del reproductor en TV: antes sólo era accesible con la tecla
  MENU, que muchos mandos de Android TV no tienen; ahora se alcanza con OK.
- Línea temporal arrastrable en el mando del móvil: con una TV vinculada y
  contenido bajo demanda en reproducción, la barra de progreso permite
  saltar a cualquier punto arrastrando, con hora actual y duración total.

### Corregido
- En la ficha de una serie en TV el mando no bajaba a la lista de
  episodios: las zonas enfocables van ahora en bloques separados y filas y
  chips muestran borde y escala al recibir el foco, igual que el resto del
  catálogo.
- Fluidez en TV: al mantener una flecha del mando durante la reproducción
  la pantalla se recomponía por cada pulsación; las pestañas ocultas del
  menú se seguían midiendo aunque no se vieran; y cada imagen nueva del
  catálogo lanzaba un fundido. Los tres costes desaparecen — la navegación
  con mando es más fluida en televisores modestos.
- Con el diálogo de pistas abierto en TV, las flechas del mando se
  enrutaban a zapping en vez de a la lista: el diálogo no se podía
  navegar y las secciones de audio/subtítulos quedaban fuera de vista.
  Las teclas pasan ahora al diálogo y cada fila muestra borde de foco.

### Añadido
- "Instalar la app en la TV" en la pantalla de Dispositivos: abre la
  ficha de Google Play para usar «Instalar en más dispositivos» y enviar
  la app a la Android TV que comparte la misma cuenta de Google. El
  acceso sólo aparece en el móvil y cuando la app se instaló desde Play.
- Intro de marca al abrir la app: la ventana de arranque ya muestra el
  icono sobre el fondo de cine y una animación breve del logo funde hacia
  la primera pantalla, en lugar del indicador de carga genérico.
- Vinculación móvil ↔ TV en la red local: la TV se anuncia sola y el móvil
  se empareja con un código de 6 dígitos (o por IP manual). Desde el móvil
  se ve "qué suena" en la TV con su posición y estado sincronizados, y se
  controla la reproducción: zapping, recall, play/pausa, seek, volumen y
  temporizador. Handoff en ambas direcciones: "traer al móvil" reanuda en
  el teléfono lo que veía la TV (con su posición) y "ver en la TV" envía
  lo que sonaba en el móvil; desde el catálogo, la pulsación larga sobre un
  canal ofrece "Ver en la TV" para lanzarlo directamente en el televisor.
  El enlace se mantiene activo con la app en segundo plano (un servicio en
  primer plano protege el proceso) y los canales se resuelven por su clave
  de proveedor, así que el handoff funciona aunque móvil y TV tengan el
  catálogo importado en orden distinto. Funciona también en Fire TV (no
  usa servicios de Google). Acceso desde Ajustes → Dispositivos o el botón
  flotante.
- Catálogo remoto desde el móvil: con la TV vinculada, "Catálogo de la TV"
  en la pantalla de dispositivos permite navegar por todo el catálogo del
  televisor —Directo, Películas y Series— con búsqueda, filtros por
  categoría y favoritos, y lanzar cualquier canal directamente en la TV
  con un toque; la entrada sonando queda marcada.
- Gestión de fuentes de la TV desde el móvil: "Fuentes de la TV" lista las
  fuentes del televisor (activa, número de canales, última sync), permite
  añadir listas M3U por URL y cuentas Xtream con progreso en vivo,
  resincronizarlas, activarlas, y "Sincronizar mi lista" permite elegir
  qué fuentes del móvil (listas M3U por URL y cuentas Xtream) copiar a la
  TV.
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
- La app en TV abre en el último canal visto en vez de en el primero de
  la lista; si el canal ya no existe, cae al primer canal disponible.
- Desde el menú del reproductor en TV se accede al catálogo (En directo,
  Películas, Series, Guía y Ajustes) con un riel de navegación lateral;
  al elegir contenido se vuelve al vídeo sin abrir otro reproductor.
- Las pantallas de catálogo muestran borde y escala al enfocar con el
  mando y ofrecen un botón de actualizar en la cabecera (en TV no existe
  el gesto de arrastrar para refrescar).
- La tecla "último canal" del mando (LAST_CHANNEL/RECALL) alterna con el
  canal anterior; el zap ya actualiza el canal guardado del arranque.
- En TV la barra de controles ya no desaparece sola mientras un botón
  tiene el foco, y la lista de canales muestra un mensaje cuando un
  filtro no tiene resultados en vez de un spinner eterno.
- Selector de calidad de vídeo en el diálogo "Pistas": junto a audio y
  subtítulos hay una sección Vídeo con cada variante del stream
  (resolución, códec y bitrate), una opción "Automática" para volver a
  la selección por defecto, y las variantes que el dispositivo no puede
  decodificar marcadas como "no compatible".

### Corregido
- Al abrir una película o episodio tras zapear desde un canal en directo
  no se guardaba el punto de reproducción: ahora se registra igual que
  al abrirlo directamente.
- Vinculación en TV: el código de 6 dígitos aparecía como un texto más
  de la lista y pasaba desapercibido; ahora se muestra en un diálogo
  centrado y con dígitos grandes. Además la propia TV ya no se lista a
  sí misma entre los dispositivos encontrados (al tocarla pedía su
  propio código y el teclado saltaba solo), la sección "Este dispositivo"
  va primero en televisores, y todos los controles de la pantalla de
  Dispositivos muestran un borde y escala claros al moverse con el mando.
- En Android TV, bajar con el mando hasta los campos de la cuenta (alta
  de lista o Xtream) abría el teclado sin pulsar nada: ahora los campos
  son filas enfocables que sólo abren un diálogo de edición al pulsar OK.
- El logo de arranque asomaba detrás de pantallas sin fondo propio (se
  notaba en Ajustes): el fondo de ventana permanente es ahora negro liso
  y Ajustes pinta su propio fondo, con margen de overscan en TV y borde
  de foco visible en todas sus filas y botones.
- Pulsar atrás viendo un canal en TV cerraba la app en lugar de volver
  al menú: ahora lleva a la pantalla con el riel, y desde ahí sí sale.
- Al enviar el código de vinculación en el móvil podía no pasar nada si
  el enlace caía en ese momento: el código se reenvía solo al recuperar
  la conexión, y el descubrimiento reconoce a la TV aunque su nombre de
  servicio llegue con el dominio de red pegado.
- El botón "Vincular" (y todos los botones del mando: zapping, pausa,
  volumen, traer aquí) no hacía nada en absoluto: el envío por el socket
  se hacía en el hilo de la interfaz y Android lo bloqueaba; ahora todos
  los envíos van por el hilo de red.
- "Transmitir" en el reproductor del móvil iba siempre por Google Cast:
  con una TV vinculada activa ahora ofrece primero mandar a la app de la
  TV (mismo canal y posición) o, si se prefiere, un Chromecast normal.
- Ajustes en TV salía con los textos negros sobre el fondo negro (sólo se
  veían los subtítulos grises y los interruptores): el marco de la app en
  televisor ya fija el color de contenido como hace el Scaffold del móvil
  y toda la pantalla se lee con normalidad.

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
- El mensaje de error por códec no admitido hablaba de "tu teléfono"
  también en Android TV: ahora dice "este dispositivo".

### Mejorado
- Menos pérdida de calidad al emitir playlists con varias calidades sin
  bitrate declarado: se estima a partir de la resolución para que el
  Chromecast distinga las variantes en vez de quedarse en la primera.
- Una conexión menos al proveedor al emitir canales Xtream: cuando la
  cuenta declara el formato del stream se evita un sondeo extra.
- En streams con varias calidades (HLS/DASH) el reproductor elige ahora
  la variante de mayor calidad que el dispositivo puede decodificar, en
  vez de conformarse con una inferior según el ancho de banda medido:
  los canales 4K se ven a la resolución real que el aparato soporta.
  Además, si el decodificador elegido falla al arrancar se prueba el
  siguiente disponible antes de dar el canal por incompatible.

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
