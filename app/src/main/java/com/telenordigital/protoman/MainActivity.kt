package com.telenordigital.protoman

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.telenor.connect.ConnectSdk
import com.telenor.connect.ui.ConnectLoginButton
import com.telenor.connect.ConnectCallback


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectSdk.sdkInitialize(applicationContext)
        setContentView(R.layout.activity_main)
        val loginButton = findViewById<ConnectLoginButton>(R.id.login_button)
        loginButton.setLoginScopeTokens("profile openid")
        loginButton.setAcrValues("2")
        loginButton.addLoginParameters(hashMapOf("prompt" to "login"))

        ConnectSdk.handleRedirectUriCallIfPresent(intent, object : ConnectCallback {
            override fun onSuccess(successData: Any) {
                goToSignedInActivity()
            }

            override fun onError(errorData: Any) {
                Toast.makeText(this@MainActivity, "Failed to sign in", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun goToSignedInActivity() {
        val intent = Intent(applicationContext, SignedInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
