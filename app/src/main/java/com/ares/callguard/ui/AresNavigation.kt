package com.ares.callguard.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ares.callguard.ui.history.HistoryScreen
import com.ares.callguard.ui.home.HomeScreen
import com.ares.callguard.ui.people.PeopleScreen
import com.ares.callguard.ui.person.AddPersonScreen
import com.ares.callguard.ui.person.EditPersonScreen
import com.ares.callguard.ui.person.PersonFormViewModel
import com.ares.callguard.ui.settings.AboutScreen
import com.ares.callguard.ui.settings.DiagnosticsScreen
import com.ares.callguard.ui.settings.PlatformsScreen
import com.ares.callguard.ui.settings.PrivacyScreen
import com.ares.callguard.ui.settings.SettingsPage
import com.ares.callguard.ui.settings.SettingsScreen
import com.ares.callguard.ui.setup.SetupScreen

private object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val PEOPLE = "people"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ADD_PERSON = "people/add"
    const val EDIT_PERSON = "people/{${PersonFormViewModel.PERSON_ID_ARG}}"

    fun editPerson(id: Long) = "people/$id"
    fun settingsPage(page: SettingsPage) = "settings/${page.name.lowercase()}"
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Rounded.Home),
    PEOPLE(Routes.PEOPLE, "People", Icons.Rounded.People),
    HISTORY(Routes.HISTORY, "History", Icons.Rounded.History),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
}

@Composable
fun AresNavigation(showWelcome: Boolean) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            if (Tab.entries.any { it.route == currentRoute }) {
                BottomBar(currentRoute, onSelect = navController::switchTab)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen draws its own top bar and handles the system bars itself.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (showWelcome) Routes.WELCOME else Routes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.WELCOME) {
                SetupScreen(
                    onBack = null,
                    onFinishWelcome = {
                        navController.navigate(Routes.HOME) { popUpTo(Routes.WELCOME) { inclusive = true } }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onAddPerson = { navController.navigate(Routes.ADD_PERSON) },
                    onOpenSettings = { navController.switchTab(Routes.SETTINGS) },
                )
            }
            composable(Routes.PEOPLE) {
                PeopleScreen(
                    onAddPerson = { navController.navigate(Routes.ADD_PERSON) },
                    onOpenPerson = { navController.navigate(Routes.editPerson(it)) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpen = { navController.navigate(Routes.settingsPage(it)) })
            }

            composable(Routes.ADD_PERSON) { AddPersonScreen(onDone = navController::popBackStack) }
            composable(
                route = Routes.EDIT_PERSON,
                arguments = listOf(navArgument(PersonFormViewModel.PERSON_ID_ARG) { type = NavType.LongType }),
            ) { EditPersonScreen(onDone = navController::popBackStack) }

            val back: () -> Unit = { navController.popBackStack() }
            composable(Routes.settingsPage(SettingsPage.SETUP)) { SetupScreen(onBack = back, onFinishWelcome = null) }
            composable(Routes.settingsPage(SettingsPage.PLATFORMS)) { PlatformsScreen(back) }
            composable(Routes.settingsPage(SettingsPage.DIAGNOSTICS)) { DiagnosticsScreen(back) }
            composable(Routes.settingsPage(SettingsPage.PRIVACY)) { PrivacyScreen(back) }
            composable(Routes.settingsPage(SettingsPage.ABOUT)) { AboutScreen(back) }
        }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab.route == currentRoute,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
