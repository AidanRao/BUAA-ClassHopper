package com.example.hello.di

import android.content.Context
import com.example.hello.data.api.AnnouncementApi
import com.example.hello.data.api.AuthApi
import com.example.hello.data.api.FallbackApi
import com.example.hello.data.api.IclassApi
import com.example.hello.data.api.QRCodeApi
import com.example.hello.data.api.UserApi
import com.example.hello.data.api.interceptor.AuthInterceptor
import com.example.hello.data.api.interceptor.LoggingInterceptor
import com.example.hello.data.api.interceptor.SslTrustManager
import com.example.hello.data.repository.TokenManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    private const val BASE_URL = "http://39.105.96.112/api/"
    private const val ICLASS_BASE_URL = "https://iclass.buaa.edu.cn:8346/"
    private const val FALLBACK_BASE_URL = "https://101.42.43.228/"

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()
    }

    @Provides
    @Singleton
    @Named("authClient")
    fun provideAuthOkHttpClient(
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(SslTrustManager.getUnsafeSslSocketFactory(), SslTrustManager.getUnsafeTrustManager())
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(SslTrustManager.getUnsafeSslSocketFactory(), SslTrustManager.getUnsafeTrustManager())
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(gson: Gson, @Named("authClient") okHttpClient: OkHttpClient): AuthApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApi(gson: Gson, okHttpClient: OkHttpClient): UserApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAnnouncementApi(gson: Gson, okHttpClient: OkHttpClient): AnnouncementApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AnnouncementApi::class.java)
    }

    @Provides
    @Singleton
    fun provideIclassApi(gson: Gson, okHttpClient: OkHttpClient): IclassApi {
        return Retrofit.Builder()
            .baseUrl(ICLASS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(IclassApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFallbackApi(gson: Gson, okHttpClient: OkHttpClient): FallbackApi {
        return Retrofit.Builder()
            .baseUrl(FALLBACK_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(FallbackApi::class.java)
    }

    @Provides
    @Singleton
    fun provideQRCodeApi(gson: Gson, okHttpClient: OkHttpClient): QRCodeApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(QRCodeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): AuthInterceptor {
        return AuthInterceptor(tokenManager)
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): LoggingInterceptor {
        return LoggingInterceptor()
    }

    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context,
        authApi: AuthApi
    ): TokenManager {
        return TokenManager(context, authApi)
    }
}

class LocalDateTimeAdapter : com.google.gson.JsonSerializer<LocalDateTime>, com.google.gson.JsonDeserializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    override fun serialize(src: LocalDateTime?, typeOfSrc: java.lang.reflect.Type?, context: com.google.gson.JsonSerializationContext?): com.google.gson.JsonElement {
        return com.google.gson.JsonPrimitive(src?.format(formatter))
    }
    
    override fun deserialize(json: com.google.gson.JsonElement?, typeOfT: java.lang.reflect.Type?, context: com.google.gson.JsonDeserializationContext?): LocalDateTime? {
        return json?.asString?.let { LocalDateTime.parse(it, formatter) }
    }
}
