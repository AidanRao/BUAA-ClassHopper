package top.aidanrao.buaa_classhopper

import android.app.Application
import javax.inject.Inject
import top.aidanrao.buaa_classhopper.data.repository.IclassAccessPolicyRepository
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AppApplication : Application() {
    @Inject lateinit var accessPolicyRepository: IclassAccessPolicyRepository

    companion object {
        lateinit var instance: AppApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        accessPolicyRepository.start()
        NavigationManager.init(this)
    }
}
