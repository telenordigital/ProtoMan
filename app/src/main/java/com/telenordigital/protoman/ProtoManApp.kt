package com.telenordigital.protoman

import android.app.Application
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.arch.lifecycle.ProcessLifecycleOwner
import android.content.Context
import android.content.Intent

class ProtoManApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            fun onStart() {
                val usesPossum = getSharedPreferences(getString(R.string.preference_id), Context.MODE_PRIVATE).getBoolean(getString(R.string.is_possum_enabled), false)
                val shouldCheck = getSharedPreferences(getString(R.string.preference_id), Context.MODE_PRIVATE).getBoolean(getString(R.string.should_check_possum), false)
                if (usesPossum && shouldCheck) {
                    val intent = Intent(applicationContext, GatherActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
            }
        })

    }
}