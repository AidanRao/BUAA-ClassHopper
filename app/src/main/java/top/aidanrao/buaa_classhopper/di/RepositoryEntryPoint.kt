package top.aidanrao.buaa_classhopper.di

import top.aidanrao.buaa_classhopper.data.repository.CourseRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun courseRepository(): CourseRepository
}
