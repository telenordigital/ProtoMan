package com.telenordigital.protoman

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.telenor.possumauth.PossumAuth

class AgreeToUsePossumActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agree_to_use_possum)

        val yesButton = findViewById<Button>(R.id.AgreeToUsePossumYesButton)
        yesButton.setOnClickListener {
            if (PossumAuth.hasMissingPermissions(this)) {
                PossumAuth.requestNeededPermissions(this)
            } else {
                activate()
            }
        }

        val laterButton = findViewById<Button>(R.id.AgreeToUsePossumLaterButton)
        laterButton.setOnClickListener {
            val intent = Intent(this, EnrollActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (result in grantResults) {
            if (result != PERMISSION_GRANTED) {
                Toast.makeText(this, "Failed setting permissions. Unable to activate", Toast.LENGTH_SHORT).show()
                return
            }
        }
        activate()

    }

    private fun activate() {
        val editor = this.getSharedPreferences(getString(R.string.preference_id), Context.MODE_PRIVATE).edit()
        editor.putBoolean(getString(R.string.is_possum_enabled), true).apply()
        editor.putBoolean(getString(R.string.should_check_possum), true).apply()
        val intent = Intent(this, PossumInfoActivity::class.java)
        startActivity(intent)
        finish()
    }
}
