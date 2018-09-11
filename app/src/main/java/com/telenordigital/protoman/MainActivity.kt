package com.telenordigital.protoman

import android.content.DialogInterface
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.AsyncTask
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.telenor.connect.ConnectSdk
import com.telenor.connect.ui.ConnectLoginButton
import com.telenor.connect.ConnectCallback
import moe.feng.support.biometricprompt.BiometricPromptCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectSdk.sdkInitialize(applicationContext)
        setContentView(R.layout.activity_main)
        val loginButton = findViewById<ConnectLoginButton>(R.id.login_button)
        loginButton.setLoginScopeTokens("profile openid")
        loginButton.setAcrValues("2")
        loginButton.addLoginParameters(hashMapOf("prompt" to "login"))

        if (!ConnectSdk.hasValidRedirectUrlCall(intent) && ConnectSdk.getAccessToken() != null) {
            promptForBiometrics()
        }

        ConnectSdk.handleRedirectUriCallIfPresent(intent, object : ConnectCallback {
            override fun onSuccess(successData: Any) {
                goToSignedInActivity()
            }

            override fun onError(errorData: Any) {
                Toast.makeText(this@MainActivity, "Failed to sign in", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun promptForBiometrics() {
        val authenticationCallback = object : BiometricPromptCompat.IAuthenticationCallback {
            override fun onAuthenticationSucceeded(result: BiometricPromptCompat.IAuthenticationResult) {
                if (ConnectSdk.getAccessToken() != null) {
                    goToSignedInActivity()
                }
            }

            override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
                // ignore, help text is showed in dialog automatically
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            }

            override fun onAuthenticationFailed() {
            }
        }
        BiometricPromptCompat.Builder(this)
                .setTitle(getString(R.string.biometric_auth_title))
                .setSubtitle(getString(R.string.biometric_auth_subtitle))
                .setNegativeButton(getString(R.string.biometric_auth_cancel)) { _, _ ->
                    findViewById<ConnectLoginButton>(R.id.login_button).performClick()
                }
                .build()
                .authenticate(authenticationCallback)
    }

    private fun goToSignedInActivity() {
        val intent = Intent(applicationContext, SignedInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
