package com.example.hello.di

import android.content.Context
import com.example.hello.command.CommandDispatcher
import com.example.hello.command.GetScheduleCommandHandler
import com.example.hello.command.SignCourseCommandHandler
import com.example.hello.service.ChatWebSocketService
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
