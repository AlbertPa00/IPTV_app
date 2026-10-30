package com.iptv.feature.player.di

import com.iptv.core.common.playback.PlaybackSession
import com.iptv.feature.player.session.PlaybackSessionImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlayerModule {
    /**
     * Singleton de proceso (a diferencia del CastController, que es por
     * consumidor): el vídeo y el zapping sobreviven a cualquier pantalla —
     * es lo que permite el modelo "player permanente + overlays" de TV.
     */
    @Singleton
    @Binds
    abstract fun bindPlaybackSession(impl: PlaybackSessionImpl): PlaybackSession
}
