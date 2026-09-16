package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.di.AppContainer
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.onboarding.OnboardingViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.workspace.WorkspaceScreen
import com.example.ui.screens.workspace.WorkspaceViewModel

@Composable
fun AppNavGraph(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = if (appContainer.apiKeyRepository.hasApiKey()) {
        Screen.Chat.createRoute()
    } else {
        Screen.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ONBOARDING SCREEN
        composable(Screen.Onboarding.route) {
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.Factory(
                    apiKeyRepository = appContainer.apiKeyRepository,
                    apiClient = appContainer.apiClient,
                    modelRepository = appContainer.modelRepository
                )
            )

            OnboardingScreen(
                viewModel = onboardingViewModel,
                onConnected = {
                    navController.navigate(Screen.Chat.createRoute()) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // CHAT SCREEN
        composable(
            route = "chat?conversationId={conversationId}&workspaceId={workspaceId}",
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("workspaceId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId")
            val wsId = backStackEntry.arguments?.getString("workspaceId")

            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    chatRepository = appContainer.chatRepository,
                    modelRepository = appContainer.modelRepository,
                    workspaceRepository = appContainer.workspaceRepository,
                    apiKeyRepository = appContainer.apiKeyRepository
                )
            )

            // Trigger init for destination
            chatViewModel.initConversation(convId, wsId)

            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToWorkspace = { targetWorkspaceId ->
                    navController.navigate(Screen.WorkspaceDetail.createRoute(targetWorkspaceId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // WORKSPACE DETAIL SCREEN
        composable(
            route = Screen.WorkspaceDetail.route,
            arguments = listOf(
                navArgument("workspaceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val wsId = backStackEntry.arguments?.getString("workspaceId").orEmpty()

            val workspaceViewModel: WorkspaceViewModel = viewModel(
                factory = WorkspaceViewModel.Factory(
                    workspaceId = wsId,
                    workspaceRepository = appContainer.workspaceRepository
                )
            )

            WorkspaceScreen(
                viewModel = workspaceViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenInChat = { targetWsId ->
                    navController.navigate(Screen.Chat.createRoute(workspaceId = targetWsId))
                }
            )
        }

        // SETTINGS SCREEN
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    apiKeyRepository = appContainer.apiKeyRepository,
                    apiClient = appContainer.apiClient,
                    modelRepository = appContainer.modelRepository
                )
            )

            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
