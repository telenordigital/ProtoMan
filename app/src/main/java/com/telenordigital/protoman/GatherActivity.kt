package com.telenordigital.protoman

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.ViewPropertyAnimator
import android.widget.ImageView
import android.widget.Toast
import com.telenor.possumgather.PossumGather
import java.util.*

class GatherActivity : AppCompatActivity() {


    override fun onResume() {
        super.onResume()
        val intent = Intent(this, SignedInActivity::class.java)
        val usesPossum = getSharedPreferences(getString(R.string.preference_id), Context.MODE_PRIVATE).getBoolean(getString(R.string.is_possum_enabled),false)
        if(!usesPossum){
            startActivity(intent)
            return
        }
        setContentView(R.layout.activity_gather)
        //TODO: make some unique user ID here instead of a static string
        val possumGather = PossumGather(this,"ProtoMan")
        val startAction = Runnable {
            possumGather.startListening()
        }
        val endAction = Runnable {
            possumGather.stopListening()
            //TODO: upload data and figure out if you get authenticated or not
            val authenticated = Random().nextBoolean()
            if(authenticated){
                Toast.makeText(applicationContext, "Recognized!",Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(applicationContext, "Intruder alert!",Toast.LENGTH_SHORT).show()
            }
            intent.putExtra(getString(R.string.authenticated_by_possum),authenticated)
            startActivity(intent)
        }
        createAnimation(startAction, endAction).start()
    }


    private fun createAnimation(startAction:Runnable, endAction:Runnable):ViewPropertyAnimator {
        val view = findViewById<ImageView>(R.id.gatherAnimation)
        val animation = view.animate().rotation(view.rotation + 360f)
        animation.startDelay = 300
        animation.duration = 3000
        animation.withStartAction(startAction)
        animation.withEndAction(endAction)
        return animation
    }
}
