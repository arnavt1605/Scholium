package com.example.scholium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Imports for your screens
import com.example.scholium.ui.screens.HomeScreen
import com.example.scholium.ui.screens.PaperAnalyzerScreen

// Import your custom theme
// (If this line turns red, delete it, click on ScholiumTheme below, and press Alt+Enter to re-import it)
import com.example.scholium.ui.theme.ScholiumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // THEME WRAPPER: If this is red, change it to match the name in your ui/theme/Theme.kt file
            ScholiumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // --- NAVIGATION SETUP ---
                    val navController = rememberNavController()

                    // NavHost acts as the Traffic Controller
                    NavHost(navController = navController, startDestination = "home") {

                        // Route 1: The Dashboard Menu
                        composable("home") {
                            HomeScreen(navController = navController)
                        }

                        // Route 2: Your awesome PDF Engine
                        composable("analyze_paper") {
                            PaperAnalyzerScreen()
                        }

                    }

                }
            }
        }
    }
}