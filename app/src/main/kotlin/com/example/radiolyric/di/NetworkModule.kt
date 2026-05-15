package com.example.radiolyric.di

import com.example.radiolyric.data.lyrics.LrcLibApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val LRCLIB_BASE_URL = "https://lrclib.net/"
    private const val USER_AGENT =
            "RadioLyric/0.1 (+https://github.com/ross-p-smith/radio-lyrics)"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val userAgent = Interceptor { chain ->
            val req = chain.request().newBuilder().header("User-Agent", USER_AGENT).build()
            chain.proceed(req)
        }
        return OkHttpClient.Builder()
                .addInterceptor(userAgent)
                .callTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
            Retrofit.Builder()
                    .baseUrl(LRCLIB_BASE_URL)
                    .client(client)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()

    @Provides
    @Singleton
    fun provideLrcLibApi(retrofit: Retrofit): LrcLibApi = retrofit.create(LrcLibApi::class.java)
}
