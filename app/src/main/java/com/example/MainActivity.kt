package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.ui.MainFeatureScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("autopilot_session_prefs", Context.MODE_PRIVATE)
    }
    var localSessionUserId by remember {
        mutableStateOf(
            prefs.getString("local_session_uid", "local_email_ms_hossain6699_gmail_com")
        )
    }
    var localSessionEmail by remember {
        mutableStateOf(
            prefs.getString("local_session_email", "ms.hossain6699@gmail.com")
        )
    }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val activeUserId = currentUser?.uid ?: localSessionUserId

    if (activeUserId == null) {
        AuthScreen(
            onAuthSuccess = {},
            onLocalEmailLogin = { email ->
                val cleanEmail = email.trim().lowercase().ifBlank { "ms.hossain6699@gmail.com" }
                val uid = "local_email_" + cleanEmail.replace(Regex("[^a-z0-9]"), "_")
                prefs.edit()
                    .putString("local_session_uid", uid)
                    .putString("local_session_email", cleanEmail)
                    .apply()
                localSessionEmail = cleanEmail
                localSessionUserId = uid
            }
        )
    } else {
        key(activeUserId) {
            MainFeatureScreen(
                currentUserId = activeUserId,
                fallbackUserEmail = localSessionEmail,
                onSignOutLocal = {
                    prefs.edit()
                        .putString("local_session_uid", null)
                        .putString("local_session_email", null)
                        .apply()
                    localSessionEmail = null
                    localSessionUserId = null
                }
            )
        }
    }
}
