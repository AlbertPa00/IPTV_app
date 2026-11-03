package com.iptv.feature.source.di

import com.iptv.core.common.tvlink.RemoteSourceController
import com.iptv.feature.source.data.RemoteSourceControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SourceLinkModule {
    @Binds
    abstract fun bindRemoteSourceController(
        impl: RemoteSourceControllerImpl,
    ): RemoteSourceController
}
