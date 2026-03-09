package com.example.hello.di

import android.content.Context
import com.example.hello.data.repository.AnnouncementRepository
import com.example.hello.data.repository.AuthRepository
import com.example.hello.data.repository.CourseRepository
import com.example.hello.data.repository.TokenManager
import com.example.hello.data.repository.UserRepository
import com.example.hello.data.api.AnnouncementApi
import com.example.hello.data.api.AuthApi
import com.example.hello.data.api.FallbackApi
import com.example.hello.data.api.IclassApi
import com.example.hello.data.api.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(tokenManager: TokenManager): AuthRepository {
        return AuthRepository(tokenManager)
    }

    @Provides
    @Singleton
    fun provideUserRepository(userApi: UserApi, tokenManager: TokenManager): UserRepository {
        return UserRepository(userApi, tokenManager)
    }

    @Provides
    @Singleton
    fun provideAnnouncementRepository(announcementApi: AnnouncementApi, tokenManager: TokenManager): AnnouncementRepository {
        return AnnouncementRepository(announcementApi, tokenManager)
    }

    @Provides
    @Singleton
    fun provideCourseRepository(
        iclassApi: IclassApi,
        fallbackApi: FallbackApi,
        tokenManager: TokenManager,
        @ApplicationContext context: Context
    ): CourseRepository {
        return CourseRepository(iclassApi, fallbackApi, tokenManager, context)
    }
}
