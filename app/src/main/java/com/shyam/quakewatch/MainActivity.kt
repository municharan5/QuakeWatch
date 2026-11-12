package com.shyam.quakewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.shyam.quakewatch.ui.theme.QuakeWatchTheme

/**
 * Main Activity serving as single entry point for QuakeWatch.
 * Manages Auth navigation state (AuthScreen vs QuakeScreen).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuakeWatchTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

    // Listen for Firebase Auth state changes so user session persists seamlessly
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = auth.currentUser
            }
        )
    } else {
        val quakeViewModel: QuakeViewModel = viewModel()
        QuakeScreen(
            viewModel = quakeViewModel,
            onLogoutClick = {
                auth.signOut()
            }
        )
    }
}
