package com.aiimagestudio.di

import com.aiimagestudio.data.repository.GalleryRepositoryImpl
import com.aiimagestudio.data.repository.InferenceRepositoryImpl
import com.aiimagestudio.data.repository.ModelRepositoryImpl
import com.aiimagestudio.domain.repository.GalleryRepository
import com.aiimagestudio.domain.repository.InferenceRepository
import com.aiimagestudio.domain.repository.ModelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGalleryRepository(impl: GalleryRepositoryImpl): GalleryRepository

    @Binds
    @Singleton
    abstract fun bindModelRepository(impl: ModelRepositoryImpl): ModelRepository

    @Binds
    @Singleton
    abstract fun bindInferenceRepository(impl: InferenceRepositoryImpl): InferenceRepository
}
