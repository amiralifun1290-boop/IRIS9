package com.iris.assistant.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iris.assistant.ui.theme.IrisTeal
import com.iris.assistant.ui.theme.SurfaceDark

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomBar(navController) },
        containerColor = Color(0xFF050505)
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(padding)
        ) {
            composable("chat") { ChatScreen() }
            composable("actions") { ActionsScreen() }
            composable("permissions") { PermissionsScreen() }
            composable("activity") { ActivityLogScreen() }
            composable("camera") { CameraScreen() }
            composable("api_settings") { ApiSettingsScreen() }
        }
    }
}

@Composable
fun BottomBar(navController: NavHostController) {
    val items = listOf(
        "chat" to "💬",
        "actions" to "⚡",
        "camera" to "📷",
        "permissions" to "🔒",
        "activity" to "📋",
        "api_settings" to "⚙️"
    )
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = SurfaceDark) {
        items.forEach { (route, icon) ->
            NavigationBarItem(
                selected = current == route,
                onClick = { navController.navigate(route) { popUpTo("chat") } },
                icon = { Text(icon) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IrisTeal,
                    indicatorColor = Color(0x14FFFFFF)
                )
            )
        }
    }
}
