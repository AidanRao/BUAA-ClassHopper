package top.aidanrao.buaa_classhopper.di

import android.content.Context
import top.aidanrao.buaa_classhopper.data.repository.AnnouncementRepository
import top.aidanrao.buaa_classhopper.data.repository.AuthRepository
import top.aidanrao.buaa_classhopper.data.repository.CourseRepository
import top.aidanrao.buaa_classhopper.data.repository.TokenManager
import top.aidanrao.buaa_classhopper.data.repository.UserRepository
import top.aidanrao.buaa_classhopper.data.api.AnnouncementApi
import top.aidanrao.buaa_classhopper.data.api.FallbackApi
import top.aidanrao.buaa_classhopper.data.api.IclassApi
import top.aidanrao.buaa_classhopper.data.api.UserApi
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
