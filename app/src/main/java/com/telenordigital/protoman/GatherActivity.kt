package com.telenordigital.protoman

import android.content.Intent
import android.os.Bundle
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.telenor.possumauth.PossumAuth
import org.json.JSONObject
import java.util.*

class GatherActivity : AppCompatActivity() {


    @TargetApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val usesPossum = getSharedPreferences(getString(R.string.preference_id), Context.MODE_PRIVATE)
                .getBoolean(getString(R.string.is_possum_enabled), false)
        if (!usesPossum) {
            finish()
        }
        setContentView(R.layout.activity_gather)
        val duration = 3000L
        runAwesomePossum(duration)
    }

    private fun runAwesomePossum(duration: Long) {
        //TODO: make some unique user ID here instead of a static string
        val userId = "ProtoMan"
        val threshold = 0.5
        val possumAuth = PossumAuth.getInstance(this, userId, resources.getString(R.string.api_url), UUID.randomUUID().toString())
        possumAuth.addAuthListener { message: String?, _: String?, e: Exception? ->
            var score = 0.0
            if (e == null) {
                val json = JSONObject(message)
                score = (json.get("trustscore") as JSONObject).get("score") as Double
            }
            if (score > threshold) {
                Toast.makeText(applicationContext, "Recognized! score was " + score, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Failed to automatically recognize user. Score was " + score, Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            finish()
        }
        possumAuth.authenticate(duration)
    }

}
