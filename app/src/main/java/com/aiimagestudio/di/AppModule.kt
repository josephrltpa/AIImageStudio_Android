package com.aiimagestudio.di

import android.content.Context
import com.aiimagestudio.data.local.db.AppDatabase
import com.aiimagestudio.data.local.db.GeneratedImageDao
import com.aiimagestudio.data.local.db.ModelDao
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideGeneratedImageDao(db: AppDatabase): GeneratedImageDao = db.generatedImageDao()

    @Provides
    fun provideModelDao(db: AppDatabase): ModelDao = db.modelDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            // A handful of CDNs (Hugging Face included) are stricter about
            // requests carrying no/unusual User-Agent headers. This makes
            // model downloads behave like an ordinary app/browser request.
            val requestWithUa = chain.request().newBuilder()
                .header("User-Agent", "AIImageStudio-Android/1.0 (+https://github.com)")
                .build()
            chain.proceed(requestWithUa)
        }
        .build()
}
