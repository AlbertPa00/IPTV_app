# Guía del repositorio

## Arquitectura

La aplicación usa Vertical Slice Architecture como monolito modular.

- `:app`: composition root, navegación y ensamblado del APK.
- `:feature:source`: alta M3U/Xtream y gestión de fuentes.
- `:feature:catalog`: categorías, búsqueda, Paging y favoritos.
- `:feature:player`: reproducción Media3. El `CastPlayer` vive en
  `CastPlayerRuntime` (ámbito de proceso): `CastPlayer.release()` cierra la
  sesión de Chromecast (`endCurrentSession`), así que nunca debe liberarse al
  salir de la pantalla del reproductor.
- `:core:*`: infraestructura compartida sin lógica de negocio.

Un feature no debe depender de otro feature. Los slices nuevos se integran desde `:app`.

## Entorno

- JDK 17+ (el `java` por defecto del sistema es JRE 8 y rompe el build;
  usar `JAVA_HOME=C:\Users\alpal\.jdks\openjdk-19.0.2`).
- Android SDK API 37 y Build Tools 36.0.0 (AGP 9.4, Gradle Wrapper 9.7.1).
- AGP 9 lleva Kotlin integrado: NO se aplica el plugin
  `org.jetbrains.kotlin.android` en los módulos. La versión de Kotlin la
  fija el catálogo (plugins compose/serialization, hoy 2.3.21 + KSP 2.3.12).
- SDK local de Windows: `C:\Users\alpal\AppData\Local\Android\Sdk`.
- `ANDROID_HOME` y `ANDROID_SDK_ROOT` están configurados como variables del usuario.

Si el SDK no se detecta en una terminal ya abierta, reiniciarla o crear `local.properties` (no se versiona):

```properties
sdk.dir=C:\\Users\\alpal\\AppData\\Local\\Android\\Sdk
```

## Verificación

En Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

En Linux/macOS:

```bash
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

Los mismos comandos se ejecutan en `.github/workflows/ci.yml`.
