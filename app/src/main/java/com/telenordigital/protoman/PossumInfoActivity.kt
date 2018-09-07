package com.telenordigital.protoman

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import java.util.*

class PossumInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_possum_info)

        val okButton = findViewById<Button>(R.id.PossumInfoOKButton)
        okButton.setOnClickListener {
            val intent = Intent(this, SignedInActivity::class.java)
            startActivity(intent)
            finish()
        }

        val optOutButton = findViewById<Button>(R.id.PossumInfoOptOutButton)
        optOutButton.setOnClickListener{
            val prefs = this.getSharedPreferences(getString(R.string.preference_id), Context.MODE_PRIVATE)
            prefs.edit().putBoolean(getString(R.string.is_possum_enabled),false).apply()
            val toast = Toast.makeText(this,"Successfully opted out", Toast.LENGTH_SHORT)
            toast.show()
            val intent = Intent(this,SignedInActivity::class.java)
            startActivity(intent)
            finish()
        }

        val progressBar = findViewById<ProgressBar>(R.id.PossumInfoProgressBar)
        //TODO: Actually get the progress from the backend
        val progress = Random().nextInt(101)
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N){
            progressBar.setProgress(progress,false)
        }else{
            progressBar.progress = progress
        }


    }
}
