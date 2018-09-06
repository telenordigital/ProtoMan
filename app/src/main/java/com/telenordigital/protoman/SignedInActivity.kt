package com.telenordigital.protoman

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import com.telenor.connect.ConnectSdk
import com.telenor.connect.id.ConnectTokensStateTracker

class SignedInActivity : AppCompatActivity() {

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectSdk.sdkInitialize(this)
        setContentView(R.layout.activity_signed_in)
        val didAuthenticate = intent.extras?.getBoolean(getString(R.string.authenticated_by_possum))

        if (ConnectSdk.getAccessToken() == null || didAuthenticate == false) {
            goToLogin()
            return
        }
        val usesPossum = getSharedPreferences(getString(R.string.preference_id), Context.MODE_PRIVATE).getBoolean(getString(R.string.is_possum_enabled),false)
        val intent = Intent(this, EnrollActivity::class.java)
        if(!usesPossum){
            startActivity(intent)
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
