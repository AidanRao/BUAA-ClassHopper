package top.aidanrao.buaa_classhopper.di

import android.content.Context
import top.aidanrao.buaa_classhopper.command.CommandDispatcher
import top.aidanrao.buaa_classhopper.command.GetScheduleCommandHandler
import top.aidanrao.buaa_classhopper.command.SignCourseCommandHandler
import top.aidanrao.buaa_classhopper.service.ChatWebSocketService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideCommandDispatcher(
        @ApplicationContext context: Context
    ): CommandDispatcher {
        val dispatcher = CommandDispatcher()
        dispatcher.registerHandler(GetScheduleCommandHandler(context))
        dispatcher.registerHandler(SignCourseCommandHandler(context))
        return dispatcher
    }

    @Provides
    @Singleton
    fun provideChatWebSocketService(): ChatWebSocketService {
        return ChatWebSocketService(allowInsecureForDebug = true)
    }
}
