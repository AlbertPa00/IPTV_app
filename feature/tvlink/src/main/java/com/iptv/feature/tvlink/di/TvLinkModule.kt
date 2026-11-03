package com.iptv.feature.tvlink.di

import com.iptv.core.common.tvlink.TvLinkClient
import com.iptv.core.common.tvlink.TvLinkHost
import com.iptv.feature.tvlink.client.TvLinkClientImpl
import com.iptv.feature.tvlink.server.TvLinkServerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TvLinkModule {
    /** Ambos son singletons de proceso: el enlace sobrevive a las pantallas. */
    @Singleton
    @Binds
    abstract fun bindTvLinkHost(impl: TvLinkServerImpl): TvLinkHost

    @Singleton
    @Binds
    abstract fun bindTvLinkClient(impl: TvLinkClientImpl): TvLinkClient
}
