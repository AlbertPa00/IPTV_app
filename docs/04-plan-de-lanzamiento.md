# 04 · Plan de lanzamiento (presupuesto 0 €)

Objetivo: maximizar descargas orgánicas de la v1.0.0 sin gasto en publicidad.

## 1. Posicionamiento

### Competencia directa (Android)

| App | Precio | Anuncios | Fuerte en | Débil en |
|---|---|---|---|---|
| TiviMate | Freemium (~10 €/año) | No | Android TV, EPG | Móvil táctil; funciones clave de pago |
| IPTV Smarters Pro | Gratis | **Sí** | Multiplataforma | Publicidad, retirada de Play varias veces |
| XCIPTV | Gratis | Algunos | Genérico | UI anticuada |
| Televizo | Freemium (~10 € única) | No | Ligero | Backup/ordenación de pago |
| OTT Navigator | Freemium | No | Power users | Complejo para no técnicos |
| Kodi | Gratis | No | Todo | Curva de aprendizaje enorme |

### Hueco de mercado

**"El reproductor IPTV 100 % gratis, sin anuncios, sin cuentas y privado, que funciona
igual en el móvil y en la TV (Cast integrado)".**

Diferenciadores reales de esta app frente a la tabla anterior:

1. Gratis de verdad: sin paywall, sin anuncios, sin versión "pro".
2. Privacidad medible: sin analytics por defecto (Crashlytics es opt-in), sin cuentas,
   credenciales cifradas con Keystore. Ningún competidor grande puede decir esto.
3. Móvil + Chromecast en una app sencilla: TiviMate es el rey de TV pero es malo en
   táctil; Smarters tiene anuncios; aquí el mensaje es "de abrir la app a ver un canal
   en ≤ 3 toques".
4. Soporte Android TV ya declarado en el manifest (`leanback` + `banner`): permite
   competir también en el nicho Fire TV / Google TV.

### Mensaje y línea roja

La app es **un reproductor, no una fuente de contenido**. Este mensaje va en la
descripción de Play, la landing, el primer arranque y todas las comunicaciones:

- Nunca promocionar la app junto a listas, proveedores o "canales gratis".
- Nunca publicar en comunidades centradas en compartir listas piratas: asocia la app a
  piratería y aumenta el riesgo de retirada (ver §6).
- Keywords y copy siempre sobre *reproducción* (M3U, Xtream, EPG, Cast), nunca sobre
  contenido concreto (fútbol, películas, canales de pago).

---

## 2. Fase 0 · Preparación (semanas -3 a -1)

### 2.1 Requisitos bloqueantes de Google Play

| Requisito | Estado | Acción |
|---|---|---|
| AAB (no APK) | Pendiente | `.\gradlew.bat :app:bundleRelease` — ya hay signing config release |
| Cuenta de desarrollador | Pendiente | 25 $ única vez (único coste inevitable). **Si la cuenta personal es posterior a nov-2023: exige 12 testers × 14 días antes de producción** → empezar la beta cerrada lo antes posible |
| URL de política de privacidad pública | **Bloqueado** | El repo `AlbertPa00/IPTV_app` es privado → alojar `PRIVACY_POLICY.md` en GitHub Pages, o hacer público el repo. URL HTTPS pública obligatoria |
| Ficha "Data safety" | Pendiente | Fácil: no se recogen datos en servidor → marcar "no data collected" (Crashlytics opt-in se declara aparte) |
| Clasificación de contenido | Pendiente | Cuestionario IARC en Play Console |
| Verificación de identidad | Pendiente | Documento de identidad en Play Console (tarda días, hacerlo ya) |
| targetSdk 35 | ✅ Hecho | `targetSdk = 35` en `app/build.gradle.kts` |
| Declaración de permisos sensibles | Pendiente | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` exige declaración + vídeo demo del uso en Play Console |
| Cast SDK Terms | Pendiente | Registrar App ID en Cast Developer Console (ya en docs §6) |

### 2.2 Assets de la ficha (ASO)

El ~65 % de las descargas Android llegan por búsqueda en Play: la ficha **es** el
marketing. A preparar antes de publicar:

- **Título** (máx. 30 car., es el campo con más peso en el algoritmo):
  marca + keyword. Hoy `app_name` es "IPTV" (genérico = bien para ASO, mal para marca).
  Opciones: `IPTV: Reproductor M3U Xtream` (29 car.) o una marca corta propia +
  keywords (`Kast TV: IPTV Player M3U`). Decisión a tomar antes de publicar: el
  `applicationId` es inmutable, el nombre no.
- **Descripción corta** (80 car.): `Reproductor IPTV gratis y sin anuncios: listas M3U,
  Xtream, guía EPG y Chromecast.`
- **Descripción larga**: keywords repetidas de forma natural 3-5 veces en el primer
  párrafo y cuerpo. Keywords objetivo (ES+EN):
  `iptv player`, `reproductor iptv`, `m3u player`, `lista m3u`, `xtream codes`,
  `epg`, `guía de tv`, `chromecast`, `iptv gratis`, `sin anuncios`.
  Redactar primero en EN (mercado mayor) y localizar a ES.
- **Icono** 512×512 + **feature graphic** 1024×500.
- **Screenshots**: mín. 4 en teléfono con texto explicativo (beneficio, no sólo UI):
  "Carga tu lista en 30 s", "Guía EPG completa", "Envía a tu TV con un toque",
  "Sin anuncios, sin cuentas". Al soportar Android TV: añadir también 1+ captura de TV
  y el banner (ya existe `@drawable/banner`).
- **Vídeo** YouTube opcional (30 s screen-record basta).
- **Categoría**: Reproductores y editores de vídeo. Tags: iptv, m3u, video player.
- **Localización de la ficha**: ES + EN desde el día 1 (strings ya existen). PT-BR es
  el siguiente mercado IPTV más grande del mundo → traducir ficha a portugués aunque la
  app aún no tenga strings en PT.

Herramientas ASO gratuitas: autocompletado de la barra de búsqueda de Play, AppFollow
(free tier), Keyword Tool for Google Play, análisis manual de las fichas de
TiviMate/Smarters/Televizo.

### 2.3 Canales paralelos de distribución (todos gratis)

| Canal | Por qué | Requisito |
|---|---|---|
| **Google Play** | Canal principal | Todo lo de §2.1 |
| **GitHub Releases** | APK firmado oficial; los usuarios IPTV están acostumbrados a sideloading; red de seguridad si Play retira la app | Repo público o releases públicos; subir `app-release.apk` firmado + SHA-256 |
| **Amazon Appstore** | Fire TV/Fire Stick es EL ecosistema IPTV por excelencia; cuenta de dev gratis; acepta APK | Revisión propia; conviene probar en un Fire Stick |
| **APKMirror / APKPure / Aptoide** | Donde buscan APKs los usuarios de este nicho | Envío manual del APK firmado; mantener firma consistente |
| **F-Droid** | Usuarios pro-privacidad, encaja con el posicionamiento | **Sólo apps FOSS**: exige añadir licencia (GPL-3.0/MIT) y repo público. Decisión de producto |
| **Obtainium** | Los usuarios apuntan a GitHub Releases directamente | Gratis, cero trabajo extra si hay releases |

### 2.4 Landing page (GitHub Pages, 0 €)

`albertpa00.github.io/iptv` con: qué es (y qué NO es), capturas, descarga APK,
enlace a Play, política de privacidad (resuelve §2.1), FAQ, changelog. Sirve de
destino para todos los posts de promoción y da SEO ("iptv player m3u xtream chromecast").

---

## 3. Fase 1 · Beta cerrada (14 días)

Si la cuenta de Play es personal reciente, esta fase es **obligatoria** (12 testers
× 14 días continuos antes de poder solicitar producción). Aunque no lo fuera, conviene
hacerla igual:

1. Reclutar **15-20 testers** (margen sobre los 12: si alguien abandona, el contador
   no se reinicia). Fuentes gratuitas: familia/amigos, comunidades de Android
   (r/androidbeta, foros XDA, grupos Telegram de desarrollo Android en español,
   r/SideProject), compañeros.
2. Track cerrado en Play Console + APK en GitHub Releases para quien no quiera Play.
3. Objetivos de la beta:
   - Bug bash en dispositivos reales (matriz de docs §3: gama baja, Xiaomi, tablet,
     Chromecast). Prioridad: primer arranque, importación M3U, login Xtream,
     reproducción, Cast.
   - Validar el mensaje "reproductor sin contenido" en la ficha (R3 de docs §4).
   - Conseguir las primeras reseñas honestas y medir activación (% testers que añaden
     fuente y reproducen — objetivo > 85 %).
4. Feedback estructurado: Google Forms gratis (dispositivo, versión, qué falló).
5. Últimos 3 días de beta: pedir a testers satisfechos que dejen reseña el día del
   lanzamiento (las primeras 10-20 reseñas 5★ disparan la conversión de la ficha).

---

## 4. Fase 2 · Lanzamiento (semana 0)

### Día 0-1

- Play: solicitar acceso a producción → al aprobarse, **staged rollout 20 % → 50 % →
  100 %** en 3-4 días (halt si crash-free < 99 %, ya definido en docs §7).
- Publicar `v1.0.0` en GitHub Releases con APK + SHA-256 + changelog.
- Enviar a Amazon Appstore (la revisión tarda ~1 semana; el APK ya está validado).

### Día del lanzamiento público

Orden de acciones (todo gratis):

1. **Product Hunt** (martes-miércoles-jueves, publicar 00:01-00:30 PST): aunque IPTV
   es nicho, da backlink de autoridad y tracción inicial. Tagline:
   "Free, private, ad-free IPTV player — M3U, Xtream, EPG & Chromecast".
2. **Reddit** (posts nativos, no copy-paste; leer normas de cada sub antes):
   - r/androidapps, r/android_beta — "I built a free ad-free IPTV player…".
   - r/SideProject, r/IndieDev — ángulo de desarrollo (Compose, Media3, VSA).
   - r/cordcutters, r/AndroidTV, r/Chromecast — ángulo de usuario.
   - ⚠️ r/IPTV está baneado y los subs activos de IPTV giran en torno a proveedores:
     evitar autopromoción allí (riesgo de asociación con piratería y ban).
3. **Show HN** en Hacker News — el ángulo técnico (Media3, VSA, privacidad sin backend)
   funciona bien allí.
4. **Indie Hackers / XDA Forums / comunidades Discord de Android** — post de lanzamiento
   con historia, no anuncio.
5. **Pitch a medios del nicho** (email, gratis):
   - TROYPOINT y similares publican rankings "best IPTV players" que mueven **miles**
     de descargas: enviar APK + press kit + ofrecer entrevista.
   - AndroidPolice, Android Authority, XDA, El Androide Libre / Xataka Android (ES).
   - YouTubers de nicho IPTV/cord-cutting (los "top 5 IPTV players" de YouTube son la
     fuente nº1 de descubrimiento en este mercado).
6. **Listados de directorio**: AlternativeTo (frente a TiviMate/Smarters), AppAgg,
   Betalist, Launching Next.

### Semana 1

- Responder a TODAS las reseñas y comentarios (velocidad de respuesta = señal de calidad).
- Monitorizar Play Console: conversión de la ficha, crashes, ANRs.
- Hotfix si aparece bug con > 1 % de sesiones afectadas.

---

## 5. Fase 3 · Crecimiento orgánico (semanas 1-8)

1. **Iteración ASO** (una variable cada 2 semanas): medir conversión de la ficha en
   Play Console; probar icono, primera captura, descripción corta. Añadir fichas
   localizadas: PT-BR, FR, DE, IT (mercados IPTV grandes; traducir ficha es gratis).
2. **Prompt de reseña in-app** (Play In-App Review API): tras la primera reproducción
   exitosa, no al abrir. Las reseñas son el factor nº1 de conversión.
3. **SEO en la landing**: artículos "Cómo cargar una lista M3U en Android",
   "Cómo usar Xtream Codes", "Enviar IPTV a Chromecast" — capturan búsqueda de
   long-tail que deriva a la app. Los posts de Reddit donde se menciona la app también
   rankean en Google ("best free iptv player reddit").
4. **Releases frecuentes**: cada update re-sitúa la app en "recently updated";
   mantener changelog visible en Play y GitHub.
5. **Comunidad propia**: grupo Telegram/Discord de soporte → feedback directo,
   testers para futuras betas, y prueba social ("comunidad activa").
6. **Empuje Android TV/Fire TV** una vez estable en móvil: el nicho TV es donde más
   dinero/atención hay en IPTV; el manifest ya lo soporta.
7. **Solicitar inclusión en listas**: "Downloader codes" de TROYPOINT, wikis de
   comunidades cord-cutting, Awesome lists en GitHub.

---

## 6. Riesgos del lanzamiento

| Riesgo | Prob. | Mitigación |
|---|---|---|
| Rechazo/retirada de Play por denuncias DMCA (le pasó a Smarters 3 veces) | Media | Disclaimer "reproductor sin contenido" en ficha + app; nada de listas precargadas; documentar apelación; APK siempre disponible en GitHub/Amazon |
| Asociación con piratería por promoción en canales equivocados | Media | Nunca promocionar en grupos de listas/proveedores; copy siempre sobre reproducción |
| Cuenta Play bloqueada por regla 12×14 | Alta si cuenta nueva | Empezar beta cerrada YA; reclutar 15-20 testers |
| Rechazo por permiso FOREGROUND_SERVICE | Media | Grabar vídeo-demo de Cast/reproducción en background para la declaración |
| Baja conversión de la ficha | Media | Iteración ASO (§5.1); los primeros 1.000 installs desbloquean visibilidad algorítmica |
| Reseñas negativas por streams que fallan (problema del proveedor, no de la app) | Alta | Mensajes de error claros en app; FAQ en la ficha; responder reseñas explicando |

---

## 7. Métricas y objetivos

| Métrica | Herramienta (gratis) | Objetivo |
|---|---|---|
| Instalaciones/semana | Play Console | 1.000 primer mes → 10.000 a 3 meses |
| Conversión de ficha (visitas→installs) | Play Console | > 25 % |
| Crash-free sessions | Play Console | > 99,5 % (ya es RNF-05) |
| Rating medio | Play Console | ≥ 4,2 ★ |
| Retención D7 | Play Console | > 40 % |
| Posición keyword "iptv player" | AppFollow free | Top 20 en 3 meses |

## 8. Checklist ejecutivo

```
SEMANA -3:  □ Cuenta Play Dev + verificación identidad   □ Privacy policy en URL pública
            □ Decisión nombre/marca                       □ Icono + feature graphic + screenshots
SEMANA -2:  □ AAB release firmado                         □ Ficha completa ES+EN con keywords
            □ Track cerrado + reclutar 15-20 testers      □ GitHub Pages (landing + policy)
            □ Reclutar testers (r/androidbeta, Telegram, conocidos)
SEMANA -1:  □ Bug bash beta + hotfix                      □ Declaración foreground service + vídeo
            □ Preparar posts PH/Reddit/HN + press kit     □ Amazon Appstore submission
DÍA 0:      □ Solicitar producción → staged rollout       □ GitHub Release v1.0.0
            □ Product Hunt + Reddit + Show HN + pitches a medios
SEMANA +1:  □ Responder reseñas                           □ Monitor crashes/conversión
SEMANAS +2-8: □ Prompt reseña in-app  □ ASO iterativo + idiomas  □ SEO landing
              □ Outreach YouTubers nicho  □ Siguiente release con mejoras
```

Único coste económico inevitable: **25 $** de la cuenta Google Play Developer
(Amazon Appstore, GitHub, F-Droid, PH, Reddit y GitHub Pages son gratuitos).
