package com.autobot.rpa.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.autobot.rpa.R
import com.autobot.rpa.ui.screens.ScriptListScreen
import com.autobot.rpa.ui.screens.SettingsScreen
import com.autobot.rpa.ui.screens.ScriptEditorScreen
import com.autobot.rpa.ui.screens.ScriptExecutionScreen
import com.autobot.rpa.ui.screens.GroupListScreen

sealed class Screen(val route: String, val titleResId: Int, val icon: @Composable () -> Unit) {
    object Scripts : Screen("scripts", R.string.scripts, { Icon(Icons.Default.List, contentDescription = null) })
    object Execute : Screen("execute", R.string.execute, { Icon(Icons.Default.Home, contentDescription = null) })
    object Settings : Screen("settings", R.string.settings, { Icon(Icons.Default.Settings, contentDescription = null) })
    object Editor : Screen("editor/{scriptId}", R.string.edit_script, { Icon(Icons.Default.List, contentDescription = null) }) {
        fun createRoute(scriptId: Long) = "editor/$scriptId"
    }
    object Groups : Screen("groups", R.string.groups, { Icon(Icons.Default.List, contentDescription = null) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBotApp(onBackPressed: ((() -> Boolean) -> Unit)) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(Screen.Scripts, Screen.Execute, Screen.Settings)

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = screen.icon,
                            label = { Text(stringResource(screen.titleResId)) },
                            selected = currentRoute == screen.route,
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Scripts.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Scripts.route) {
                ScriptListScreen(
                    onNavigateToEditor = { scriptId ->
                        navController.navigate(Screen.Editor.createRoute(scriptId))
                    },
                    onNavigateToExecution = {
                        navController.navigate(Screen.Execute.route)
                    },
                    onNavigateToGroups = {
                        navController.navigate(Screen.Groups.route)
                    },
                    onBackPressed = onBackPressed
                )
            }
            composable(Screen.Execute.route) {
                ScriptExecutionScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(Screen.Editor.route) { backStackEntry ->
                val scriptId = backStackEntry.arguments?.getString("scriptId")?.toLongOrNull() ?: -1L
                ScriptEditorScreen(
                    scriptId = scriptId,
                    onNavigateBack = { navController.popBackStack() },
                    onBackPressed = onBackPressed
                )
            }
            composable(Screen.Groups.route) {
                GroupListScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
