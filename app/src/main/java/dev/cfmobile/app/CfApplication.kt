package dev.cfmobile.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class CfApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Tracks whole-app foreground/background transitions (not per-Activity) so app lock
        // triggers correctly across screen rotations, multi-window, and navigating within the
        // app - PRD §48's "lock on background option".
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.appLockState.onAppBackgrounded()
            }

            override fun onStart(owner: LifecycleOwner) {
                container.appLockState.onAppForegrounded()
            }
        })
    }
}
