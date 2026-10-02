# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Cada entrada describe el cambio orientado a usuario, no el detalle técnico.

## [Unreleased]

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
