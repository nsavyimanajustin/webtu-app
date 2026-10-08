package com.example.webtumeals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.webtumeals.theme.WebTUMealsTheme
import com.example.webtumeals.trial.TrialExpiredScreen
import com.example.webtumeals.trial.TrialManager

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()

    TrialManager.initTrial(this)
    val isExpiredInitially = TrialManager.isExpired(this)
    if (isExpiredInitially) {
      TrialManager.performSelfDestruction(this)
    }

    setContent {
      WebTUMealsTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          var isExpired by remember { mutableStateOf(isExpiredInitially) }
          if (isExpired) {
            TrialExpiredScreen(
              onDeveloperUnlocked = {
                isExpired = false
              }
            )
          } else {
            MainNavigation()
          }
        }
      }
    }
  }
}
