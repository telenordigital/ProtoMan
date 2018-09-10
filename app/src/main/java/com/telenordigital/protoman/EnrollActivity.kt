package com.telenordigital.protoman

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.*

class EnrollActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll)

        val readMoreLink = findViewById<TextView>(R.id.readMoreLink)
        readMoreLink.setOnClickListener {
            val intent = Intent(this, ReadMoreActivity::class.java)
            startActivity(intent)
        }

        val yesButton = findViewById<Button>(R.id.YesButton)
        yesButton.setOnClickListener {
            val intent = Intent(this, AgreeToUsePossumActivity::class.java)
            startActivity(intent)
            finish()
        }

        val noLink = findViewById<TextView>(R.id.NoLink)
        noLink.setOnClickListener {
            finish()
        }
    }
}
