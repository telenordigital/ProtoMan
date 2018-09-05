package com.telenordigital.protoman

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class ReadMoreActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_more)

        val button = findViewById<Button>(R.id.ReadMorePopupButton)
        button.setOnClickListener {
            finish()
        }
    }
}
