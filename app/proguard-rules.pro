# Reglas específicas de la aplicación. Las librerías AndroidX/Media3 incluyen sus reglas.

# ffmpeg-kit referencia la librería opcional "smart-exception" para construir
# trazas; no se distribuye en el AAR y la app no la necesita.
-dontwarn com.arthenica.smartexception.**

# NanoHTTPD intenta registrar un shutdown hook con sun.misc.Signal (no existe
# en Android); es un camino opcional protegido por reflexión.
-dontwarn sun.misc.Signal

# En release se eliminan las trazas d/v: varias registran URLs de proveedor
# que llevan credenciales Xtream embebidas en el path. Las w/e que quedan
# redactan la URL (ver UrlLogRedact.kt).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
