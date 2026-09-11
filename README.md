# IPTV App (Android)

Reproductor IPTV para Android: sencillo, amigable y fácil de navegar. Soporta listas
**M3U/M3U8** (URL o archivo) y **Xtream Codes** (host + usuario + contraseña), guía EPG
(XMLTV) y envío a **Chromecast**.

> La aplicación es únicamente un reproductor. No incluye, aloja ni distribuye contenido:
> el usuario aporta sus propias listas o credenciales de su proveedor.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/01-vision-y-arquitectura.md](docs/01-vision-y-arquitectura.md) | Visión de producto, equipo, arquitectura, stack, modelo de datos y ADRs |
| [docs/02-backlog-y-sprints.md](docs/02-backlog-y-sprints.md) | Épicas, roadmap, sprints e historias de usuario con criterios de aceptación |
| [docs/03-calidad-y-riesgos.md](docs/03-calidad-y-riesgos.md) | Estrategia de pruebas, DoR/DoD, riesgos, métricas y cumplimiento |

## Estado de desarrollo

Actualizado: 2026-09-08.

| Slice / capacidad | Estado | Alcance actual |
|---|---|---|
| Plataforma y app shell | Implementado | Gradle multi-módulo, Hilt, navegación, Material 3 y CI |
| Fuentes M3U | Implementado | URL y archivo local mediante SAF; copia privada para recarga |
| Fuentes Xtream | Implementado | Login y sincronización de TV, películas y series; VOD/series opcionales por proveedor |
| Catálogo | Implementado | UI audiovisual grafito/carmín, secciones TV/Películas/Series, pósteres, categorías, Paging, búsqueda y favoritos |
| Reproductor | Implementado | TV, películas y episodios con Media3 local, autodetección de formato, metadatos, headers y errores diferenciados |
| Series | Implementado | Detalle Xtream completo, temporadas, episodios y reproducción en Media3 |
| EPG | Implementado | Slice independiente XMLTV en streaming, gzip, asociación tvg-id/nombre, guía actual/siguiente y sincronización manual |
| Chromecast | MVP implementado | MIME inferido sólo para Cast, live/buffered correcto y traspaso seguro sin detener local antes de que el receptor esté listo |

Verificación local completada con Android SDK API 35:

- `testDebugUnitTest`: correcto.
- `assembleDebug`: correcto.
- `lintDebug`: correcto (0 errores; 68 avisos preexistentes de configuración, recursos y versiones disponibles).
- Firma debug APK Scheme v2: verificada.

### APK de validación

```text
app/build/outputs/apk/debug/app-debug.apk
```

Tamaño: 26.724.257 bytes. SHA-256:
`66222021D2E4C1324F116BF222ACA32B0CAE64B656737F789DD6026D58E27454`.

Correcciones de feedback incluidas:

- Reproducción HLS de TV: añadido `media3-exoplayer-hls`; ya no se produce el crash por
  `HlsMediaSource.Factory` ausente.
- El `MediaItem` local no fuerza MIME y conserva la autodetección de ExoPlayer; el MIME y su
  fallback live/VOD se aplican únicamente al convertir para Cast, con regresiones JVM.
- El traspaso a Cast mantiene local activo hasta que el receptor confirma estado listo; los
  fallos de inicialización o carga remota restauran la reproducción móvil.
- Catálogo y reproductor renovados con jerarquía audiovisual grafito/carmín, overlays legibles,
  pósteres y filas de TV más visuales, navegación táctil de 48 dp y textos ES/EN.
- El reproductor captura fallos de inicialización y muestra error en vez de cerrar la app.
- Las listas M3U clasifican VOD mediante `tvg-type`/`media-type`, rutas `/movie/`, `/series/`
  y nombres habituales de grupos. Para reclasificar una lista ya importada hay que pulsar
  **Actualizar** en Ajustes → Fuentes.

Para instalar con un teléfono conectado y depuración USB habilitada:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

## Arquitectura

**Vertical Slice Architecture como monolito modular**: un único APK, con cada capacidad de
negocio autocontenida en `:feature:source`, `:feature:catalog`, `:feature:series`, `:feature:player` y `:feature:epg`. `:app`
compone los slices y `:core:*` contiene sólo infraestructura realmente compartida.

## Decisiones ya tomadas

- **Stack**: Kotlin + Jetpack Compose + Media3 (ExoPlayer), VSA + MVVM por slice.
- **Fuentes**: M3U/M3U8 + XMLTV **y** Xtream Codes desde el MVP.
- **Cast**: Cast SDK con Default Media Receiver enviando la URL directa, título, logo y tipo
  live/buffered. Si el receptor rechaza el formato se puede volver a reproducción móvil.
  La disponibilidad real depende de que Chromecast pueda acceder a la URL y soporte el
  contenedor/códecs; headers privados, cookies, URLs locales y formatos IPTV no compatibles no se
  transforman. Proxy HTTP local, remux/transcodificación y receptor personalizado quedan en Fase 2.
