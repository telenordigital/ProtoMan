package com.telenordigital.protoman

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.ViewPropertyAnimator
import android.widget.ImageView
import android.widget.Toast
import com.telenor.possumgather.PossumGather
import java.util.*

class GatherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_gather)
        //TODO: make some unique user ID here instead of a static string
        val possumGather = PossumGather(this, "ProtoMan")
        val startAction = Runnable {
            possumGather.startListening()
        }
        val endAction = Runnable {
            try {
                possumGather.stopListening()
            } catch (e: Exception) {
                Log.w("ProtoMan", "Unable to stop listening for user data. Exception was: " + e.toString())
            }

            //TODO: upload data and figure out if you get authenticated or not
            val authenticated = Random().nextBoolean()
            if (authenticated) {
                Toast.makeText(applicationContext, "Recognized!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Intruder alert!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            finish()

        }
        createAnimation(startAction, endAction).start()
    }


    private fun createAnimation(startAction: Runnable, endAction: Runnable): ViewPropertyAnimator {
        val view = findViewById<ImageView>(R.id.gatherAnimation)
        val animation = view.animate().rotation(view.rotation + 360f)
        animation.startDelay = 300
        animation.duration = 3000
        animation.withStartAction(startAction)
        animation.withEndAction(endAction)
        return animation
    }
}
