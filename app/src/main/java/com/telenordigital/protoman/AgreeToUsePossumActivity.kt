package com.telenordigital.protoman

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class AgreeToUsePossumActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agree_to_use_possum)

        val yesButton = findViewById<Button>(R.id.AgreeToUsePossumYesButton)
        yesButton.setOnClickListener{
            val intent = Intent(this,SignedInActivity::class.java)
            startActivity(intent)
            finish()
        }

        val laterButton = findViewById<Button>(R.id.AgreeToUsePossumLaterButton)
        laterButton.setOnClickListener{
            finish()
        }
    }
}
