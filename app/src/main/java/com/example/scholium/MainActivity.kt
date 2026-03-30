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

import com.example.scholium.ui.screens.HomeScreen
import com.example.scholium.ui.screens.PaperAnalyzerScreen
import com.example.scholium.ui.theme.ScholiumTheme
import com.example.scholium.data.local.AppDatabase
import androidx.navigation.navArgument
import androidx.navigation.NavType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val chatDao = database.chatDao()

        setContent {
            ScholiumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {

                        composable("home") {
                            HomeScreen(navController = navController)
                        }

                        composable("related_papers") {
                            com.example.scholium.ui.screens.RelatedPapersScreen(navController = navController)
                        }

                        composable(
                            route = "analyze_paper?sessionId={sessionId}",
                            arguments = listOf(navArgument("sessionId") {
                                defaultValue = -1L
                                type = NavType.LongType
                            })
                        ) { backStackEntry ->
                            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: -1L
                            // If it's -1, we are starting a fresh chat. Otherwise, pass the real ID.
                            val actualSessionId = if (sessionId == -1L) null else sessionId

                            PaperAnalyzerScreen(chatDao = chatDao, existingSessionId = actualSessionId)
                        }
                        composable("history") {
                            com.example.scholium.ui.screens.HistoryScreen(navController = navController, chatDao = chatDao)
                        }

                        composable("citation_generator") {
                            com.example.scholium.ui.screens.CitationScreen(navController = navController)
                        }

                        composable("open_access") {
                            com.example.scholium.ui.screens.OpenAccessScreen(navController = navController)
                        }

                        composable("abstract_summary") {
                            com.example.scholium.ui.screens.AbstractSummaryScreen(navController = navController)
                        }

                        composable("paper_reviewer") {
                            com.example.scholium.ui.screens.PaperReviewerScreen(navController = navController)
                        }
                        composable("latex_generator") {
                            com.example.scholium.ui.screens.LatexGeneratorScreen(navController = navController)
                        }

                        composable("lit_reviewer") {
                            com.example.scholium.ui.screens.LitReviewerScreen(navController = navController)
                        }

                        composable("claim_verifier") {
                            com.example.scholium.ui.screens.ClaimVerifierScreen(navController = navController)
                        }

                        composable("journal_matcher") {
                            com.example.scholium.ui.screens.JournalMatcherScreen(navController = navController)
                        }
                        composable("rebuttal_drafter") {
                            com.example.scholium.ui.screens.ReviewRebuttalScreen(navController = navController)
                        }

                        composable("chat_detail/{sessionId}") { backStackEntry ->
                            // Extract the ID from the URL
                            val sessionIdString = backStackEntry.arguments?.getString("sessionId")
                            val sessionId = sessionIdString?.toLongOrNull() ?: 0L

                            com.example.scholium.ui.screens.ChatDetailScreen(
                                navController = navController,
                                chatDao = chatDao,
                                sessionId = sessionId
                            )
                        }

                    }

                }
            }
        }
    }
}