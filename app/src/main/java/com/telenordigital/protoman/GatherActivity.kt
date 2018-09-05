package com.telenordigital.protoman

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.ViewPropertyAnimator
import android.widget.ImageView
import com.telenor.possumgather.PossumGather

class GatherActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gather)
        //TODO: make some unique user ID here instead of a static string
        val possumGather = PossumGather(this,"ProtoMan")
        val startAction = Runnable {
            possumGather.startListening()
        }
        val endAction = Runnable {
            possumGather.stopListening()
            //TODO: upload data and figure out if you get authenticated or not

            val intent = Intent(this, SignedInActivity::class.java)
            startActivity(intent)
            finish()
        }
        if(possumGather.hasMissingPermissions(this)){
            possumGather.requestNeededPermissions(this)
        }
        createAnimation(startAction, endAction).start()

    }

    private fun createAnimation(startAction:Runnable, endAction:Runnable):ViewPropertyAnimator {
        val view = findViewById<ImageView>(R.id.gatherAnimation)

        val animation = view.animate().rotation(360f)
        animation.startDelay = 300
        animation.duration = 3000
        animation.withStartAction(startAction)
        animation.withEndAction(endAction)
        return animation
    }
}
