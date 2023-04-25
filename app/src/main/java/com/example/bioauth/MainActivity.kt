package com.example.bioauth

import android.content.Intent
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.example.bioauth.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.security.auth.login.LoginException

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

//        val navController = findNavController(R.id.nav_host_fragment_content_main)
//        appBarConfiguration = AppBarConfiguration(navController.graph)
//        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.fab)
                .setAction("Action", null).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bioAuth()
        } else {
            fingerPrint()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private fun fingerPrint() {
        Log.e(TAG, "fingerPrint")
        val fingerprintManager = getSystemService(FingerprintManager::class.java)
        if (fingerprintManager.isHardwareDetected && fingerprintManager.hasEnrolledFingerprints()) {
            val cancellationSignal = android.os.CancellationSignal().apply {
                setOnCancelListener {
                    Log.e(TAG, "cancel")
                }
            }

            val cipher = getCipher()

            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                "test",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            val keyGenerator = generateSecretKey(keyGenParameterSpec)
            cipher.init(Cipher.ENCRYPT_MODE, keyGenerator.generateKey())

            val listener = object : FingerprintManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(this@MainActivity, "onAuthenticationSucceeded", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, "onAuthenticationError", Toast.LENGTH_SHORT).show()
                    findViewById<TextView>(R.id.tvTxt).text = errString
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "onAuthenticationFailed", Toast.LENGTH_SHORT).show()
                }
            }

            findViewById<Button>(R.id.biometric_login).setOnClickListener {
                fingerprintManager.authenticate(
                    FingerprintManager.CryptoObject(cipher),
                    cancellationSignal,
                    0,
                    listener,
                    null
                )
            }
        } else {
            Log.e(TAG, "인증없다")
        }
    }

    private fun bioAuth() {
        Log.e(TAG, "bioAuth")
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Toast.makeText(this@MainActivity, "Success", Toast.LENGTH_SHORT).show()
                Log.e("MY_APP_TAG", "App can authenticate using biometrics.")
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(this@MainActivity, "BIOMETRIC_ERROR_NO_HARDWARE", Toast.LENGTH_SHORT).show()
                Log.e("MY_APP_TAG", "No biometric features available on this device.")
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Toast.makeText(this@MainActivity, "BIOMETRIC_ERROR_HW_UNAVAILABLE", Toast.LENGTH_SHORT).show()
                Log.e("MY_APP_TAG", "Biometric features are currently unavailable.")
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Prompts the user to create credentials that your app accepts.
                Log.e("MY_APP_TAG", "BIOMETRIC_ERROR_NONE_ENROLLED")
                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                    )
                }
                startActivityForResult(enrollIntent, 1001)
            }
        }


        doBioAuth()
    }

    private fun doBioAuth() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(
                    applicationContext,
                    "Authentication error: $errString", Toast.LENGTH_SHORT
                )
                    .show()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Toast.makeText(
                    applicationContext,
                    "Authentication succeeded!", Toast.LENGTH_SHORT
                )
                    .show()

                findViewById<TextView>(R.id.tvTxt).text = "Success"
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(
                    applicationContext, "Authentication failed",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password")
            .build()

        findViewById<Button>(R.id.biometric_login).setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun getCipher(): Cipher {
        return Cipher.getInstance(
            KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7
        )
    }

    private fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec): KeyGenerator {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
        return keyGenerator
    }


    companion object {
        private val TAG = MainActivity::class.simpleName
    }
}