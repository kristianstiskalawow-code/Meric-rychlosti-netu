package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.SpeedTestRepository
import com.example.ui.SpeedTestScreen
import com.example.ui.SpeedTestViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = SpeedTestRepository(database.speedTestDao())

    setContent {
      MyApplicationTheme {
        val context = LocalContext.current
        val speedTestViewModel: SpeedTestViewModel = viewModel(
          factory = SpeedTestViewModel.Factory(
            application = context.applicationContext as Application,
            repository = repository
          )
        )
        
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = com.example.ui.theme.CyberDarkBg
        ) {
          SpeedTestScreen(viewModel = speedTestViewModel)
        }
      }
    }
  }
}

