package com.telenordigital.protoman

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import com.telenor.connect.ConnectSdk
import com.telenor.connect.id.ConnectTokensStateTracker

class SignedInActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectSdk.sdkInitialize(this)
        setContentView(R.layout.activity_signed_in)
        if (ConnectSdk.getAccessToken() == null) {
            goToLogin()
            return
        }

        val logoutButton = findViewById<Button>(R.id.logout_button)
        logoutButton.setOnClickListener { ConnectSdk.logout() }

        val userId = findViewById<TextView>(R.id.user_id)
        userId.text = ConnectSdk.getIdToken().subject

        object : ConnectTokensStateTracker() {
            override fun onTokenStateChanged(hasTokens: Boolean) {
                if (!hasTokens) {
                    goToLogin()
                }
            }
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
