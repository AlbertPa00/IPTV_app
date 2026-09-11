# Reglas específicas de la aplicación. Las librerías AndroidX/Media3 incluyen sus reglas.

# ffmpeg-kit referencia la librería opcional "smart-exception" para construir
# trazas; no se distribuye en el AAR y la app no la necesita.
-dontwarn com.arthenica.smartexception.**

# NanoHTTPD intenta registrar un shutdown hook con sun.misc.Signal (no existe
# en Android); es un camino opcional protegido por reflexión.
-dontwarn sun.misc.Signal
