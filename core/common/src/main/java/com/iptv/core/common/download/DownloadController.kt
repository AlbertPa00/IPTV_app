package com.iptv.core.common.download

/**
 * Contrato para lanzar/cancelar descargas desde cualquier feature sin que
 * un slice dependa de otro. La implementación (WorkManager + OkHttp) vive
 * en :feature:downloads y se vincula por DI.
 */
interface DownloadController {

    /**
     * Encola la descarga del contenido [channelId] (película o episodio
     * materializado). [section] es "movie" o "episode" y decide en qué
     * carrusel de Descargados aparece.
     */
    suspend fun enqueue(channelId: Long, section: String)

    /** Cancela la descarga en curso/encolada de [channelId] y borra el parcial. */
    suspend fun cancel(channelId: Long)

    /** Elimina la descarga: cancela el trabajo, borra el fichero y la fila. */
    suspend fun delete(downloadId: Long)

    /** Limpieza al borrar una fuente: elimina sus descargas y ficheros. */
    suspend fun removeForSource(sourceId: Long)

    /** Borra todas las descargas: cancela trabajos, ficheros y filas. */
    suspend fun removeAll()
}
