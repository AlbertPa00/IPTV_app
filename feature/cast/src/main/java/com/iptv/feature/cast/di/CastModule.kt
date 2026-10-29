package com.iptv.feature.cast.di

import com.iptv.core.common.cast.CastController
import com.iptv.feature.cast.CastPlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CastModule {
    /**
     * Sin scope: cada consumidor (p. ej. el ViewModel del player) recibe su
     * propia instancia ligada a su ciclo de vida vía attach/detach. La sesión
     * Cast y el proxy siguen siendo de proceso en los runtimes objeto.
     */
    @Binds
    abstract fun bindCastController(impl: CastPlaybackController): CastController
}
