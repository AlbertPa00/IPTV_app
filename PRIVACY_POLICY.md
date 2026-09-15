# CeroCast — Política de privacidad / Privacy Policy

Última actualización / Last updated: 2026-09-15

## Español

**CeroCast es un reproductor. No incluye, aloja ni distribuye contenido, listas
de canales ni suscripciones.** Toda la información descrita a continuación se
refiere exclusivamente a los datos que el propio usuario introduce en la app.

### Datos que almacena la app (sólo en el dispositivo)

- **Fuentes y credenciales**: URLs de listas M3U y credenciales de cuentas
  Xtream Codes que el usuario introduce voluntariamente. Las contraseñas se
  guardan cifradas con Android Keystore (AES-GCM).
- **Catálogo local**: canales, películas, series, favoritos, historial de
  reproducción y preferencias, almacenados en una base de datos local (Room).
- **Ajustes**: tema, PIN de control parental (almacenado como hash con sal),
  refresco automático y preferencias de uso.

### Datos que la app envía

- La app **no envía datos personales a servidores propios**: no hay analytics,
  publicidad ni cuentas de usuario. El informe de errores (Firebase
  Crashlytics) está desactivado por defecto y sólo se activa si el usuario lo
  habilita explícitamente en Ajustes.
- Las únicas peticiones de red son las que realiza el propio usuario hacia
  **sus** proveedores: descarga de listas, peticiones a APIs Xtream, guías
  XMLTV y el propio streaming. Las credenciales viajan únicamente hacia el
  servidor del proveedor que el usuario configuró, según el protocolo de ese
  proveedor (que puede ser HTTP sin cifrar — fuera del control de la app).
- Al emitir a Chromecast, el teléfono actúa como proxy local dentro de la red
  Wi-Fi del usuario para servir el stream al dispositivo Cast.

### Copias de seguridad

La base de datos (fuentes, credenciales y catálogo) y las listas copiadas al
dispositivo están **excluidas** de la copia de seguridad de Android. Tras una
restauración o cambio de dispositivo, el usuario vuelve a introducir sus
fuentes.

### Permisos

- `INTERNET`, `ACCESS_NETWORK_STATE`: streaming y descarga de catálogos.
- `POST_NOTIFICATIONS` (Android 13+): mostrar el control de emisión a
  Chromecast. Se solicita en contexto, sólo si se usa la función.
- `NEARBY_WIFI_DEVICES` (Android 13+): descubrir dispositivos Chromecast en la
  red local. Se solicita en contexto, sin usarlo para geolocalización.
- `WAKE_LOCK`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: mantener la reproducción y
  la emisión activas.

### Control parental

El PIN de control parental se almacena localmente como hash (SHA-256 con sal
aleatoria); nunca se transmite.

### Contacto

Para dudas sobre esta política, abre un issue en el repositorio del proyecto.

---

## English

**CeroCast is a player. It does not include, host, or distribute content,
channel lists, or subscriptions.** Everything below refers only to data the
user enters into the app.

### Data stored by the app (on-device only)

- **Sources and credentials**: M3U playlist URLs and Xtream Codes account
  credentials voluntarily entered by the user. Passwords are stored encrypted
  with Android Keystore (AES-GCM).
- **Local catalog**: channels, movies, series, favorites, playback history,
  and preferences in a local Room database.
- **Settings**: theme, parental-control PIN (stored as a salted hash),
  auto-refresh, and usage preferences.

### Data the app sends

- The app **sends no personal data to its own servers**: no analytics, no ads,
  no user accounts. Crash reporting (Firebase Crashlytics) is disabled by
  default and only runs if the user explicitly enables it in Settings.
- The only network requests are those the user's own providers receive:
  playlist downloads, Xtream API calls, XMLTV guides, and streaming.
  Credentials travel only to the provider's server as configured by the user
  (which may be unencrypted HTTP — outside the app's control).
- When casting to Chromecast, the phone acts as a local proxy on the user's
  Wi-Fi network to serve the stream to the Cast device.

### Backups

The database (sources, credentials, catalog) and playlists copied to the
device are **excluded** from Android backup. After a restore or device change,
the user re-adds their sources.

### Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`: streaming and catalog downloads.
- `POST_NOTIFICATIONS` (Android 13+): show the Chromecast casting control.
  Requested in context, only when the feature is used.
- `NEARBY_WIFI_DEVICES` (Android 13+): discover Chromecast devices on the
  local network. Requested in context; not used for geolocation.
- `WAKE_LOCK`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: keep playback and casting
  alive.

### Parental control

The parental PIN is stored locally as a hash (SHA-256 with a random salt) and
is never transmitted.

### Contact

For questions about this policy, open an issue in the project repository.
