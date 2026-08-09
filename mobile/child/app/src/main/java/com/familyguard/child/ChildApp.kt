package com.familyguard.child

import android.app.Application
import com.familyguard.child.data.SessionStore

class ChildApp : Application() {
    lateinit var session: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = SessionStore(this)
    }

    companion object {
        lateinit var instance: ChildApp
            private set
    }
}
