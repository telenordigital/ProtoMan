package com.telenordigital.protoman

import android.content.Intent
import android.os.Bundle
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.telenor.possumgather.PossumGather
import java.util.*

class GatherActivity : AppCompatActivity() {


    @TargetApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, SignedInActivity::class.java)
        val usesPossum = getSharedPreferences(getString(R.string.preference_id), Context.MODE_PRIVATE)
                .getBoolean(getString(R.string.is_possum_enabled), false)
        if (!usesPossum) {
            startActivity(intent)
            return
        }
        setContentView(R.layout.activity_gather)
        val delay = 300L
        val duration = 3000L
        runAwesomePossum(intent, delay, duration)
    }

    private fun runAwesomePossum(intent: Intent, delay: Long, duration: Long) {
        //TODO: make some unique user ID here instead of a static string
        val possumGather = PossumGather(this, "ProtoMan")
        val startAction = Runnable {
            possumGather.startListening()
        }
        val endAction = getEndAction(possumGather, intent)
        val handler = Handler()
        handler.postDelayed(startAction, delay)
        handler.postDelayed(endAction, duration + delay)
    }

    private fun getEndAction(possumGather: PossumGather, intent: Intent): Runnable {
        return Runnable {
            try {
                possumGather.stopListening()
            } catch (e: Exception) {
                Log.w("ProtoMan", "Unable to stop listening for user data." +
                        " Exception was: " + e.toString())
            }

            //TODO: upload data and figure out if you get authenticated or not
            val authenticated = Random().nextBoolean()
            if (authenticated) {
                Toast.makeText(applicationContext, "Recognized!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Failed to automatically recognize user", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            finish()

        }
    }
}
