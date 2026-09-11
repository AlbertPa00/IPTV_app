# Guía del repositorio

## Arquitectura

La aplicación usa Vertical Slice Architecture como monolito modular.

- `:app`: composition root, navegación y ensamblado del APK.
- `:feature:source`: alta M3U/Xtream y gestión de fuentes.
- `:feature:catalog`: categorías, búsqueda, Paging y favoritos.
- `:feature:player`: reproducción Media3.
- `:core:*`: infraestructura compartida sin lógica de negocio.

Un feature no debe depender de otro feature. Los slices nuevos se integran desde `:app`.

## Entorno

- JDK 17.
- Android SDK API 35 y Build Tools 35.0.0.
- Gradle Wrapper 8.9 incluido.
- SDK local de Windows: `C:\Users\apalomino\AppData\Local\Android\Sdk`.
- `ANDROID_HOME` y `ANDROID_SDK_ROOT` están configurados como variables del usuario.

Si el SDK no se detecta en una terminal ya abierta, reiniciarla o crear `local.properties` (no se versiona):

```properties
sdk.dir=C:\\ruta\\al\\Android\\Sdk
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
