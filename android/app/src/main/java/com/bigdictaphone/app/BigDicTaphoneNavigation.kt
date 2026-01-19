package com.bigdictaphone.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bigdictaphone.app.ui.screens.*
import com.bigdictaphone.app.viewmodel.RecordingViewModel

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Record : Screen("record", "Record", { Icon(Icons.Default.Mic, contentDescription = "Record") })
    object Recordings : Screen("recordings", "Recordings", { Icon(Icons.Default.List, contentDescription = "Recordings") })
    object ToDo : Screen("todo", "To-Do", { Icon(Icons.Default.Checklist, contentDescription = "To-Do") })
    object Settings : Screen("settings", "Settings", { Icon(Icons.Default.Settings, contentDescription = "Settings") })
}

val bottomNavItems = listOf(Screen.Record, Screen.Recordings, Screen.ToDo, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BigDicTaphoneApp() {
    val navController = rememberNavController()
    val viewModel: RecordingViewModel = viewModel()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = screen.icon,
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Record.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Record.route) {
                RecordingScreen(viewModel = viewModel)
            }
            composable(Screen.Recordings.route) {
                RecordingsListScreen(
                    viewModel = viewModel,
                    onRecordingClick = { recording ->
                        navController.navigate("recording/${recording.id}")
                    }
                )
            }
            composable(Screen.ToDo.route) {
                ToDoListScreen(
                    viewModel = viewModel,
                    onNavigateToRecording = { recordingId ->
                        navController.navigate("recording/$recordingId")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable("recording/{recordingId}") { backStackEntry ->
                val recordingId = backStackEntry.arguments?.getString("recordingId")
                recordingId?.let {
                    RecordingDetailScreen(
                        recordingId = it,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
