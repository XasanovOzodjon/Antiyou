package com.familyguard.parent

import android.app.Application
import com.familyguard.parent.data.SessionStore

class ParentApp : Application() {
    lateinit var session: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = SessionStore(this)
    }

    companion object {
        lateinit var instance: ParentApp
            private set
    }
}
