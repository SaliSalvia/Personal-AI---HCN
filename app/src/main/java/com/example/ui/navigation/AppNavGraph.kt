package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.settings.AppLanguage
import com.example.di.AppContainer
import com.example.ui.localization.LocalAppStrings
import com.example.ui.localization.rememberAppStrings
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
    val language by appContainer.languageRepository.language.collectAsState()
    val strings = rememberAppStrings(language)
    val layoutDirection = if (language == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        AppNavContent(appContainer, navController)
    }
}

@Composable
private fun AppNavContent(
    appContainer: AppContainer,
    navController: NavHostController
) {
    val startDestination = remember {
        if (appContainer.apiKeyRepository.hasApiKey()) Screen.Chat.createRoute() else Screen.Onboarding.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
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

        composable(
            route = "chat?conversationId={conversationId}&workspaceId={workspaceId}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("workspaceId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId")
            val wsId = backStackEntry.arguments?.getString("workspaceId")
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    chatRepository = appContainer.chatRepository,
                    modelRepository = appContainer.modelRepository,
                    workspaceRepository = appContainer.workspaceRepository,
                    sourceDocumentRepository = appContainer.sourceDocumentRepository,
                    apiKeyRepository = appContainer.apiKeyRepository
                )
            )
            LaunchedEffect(convId, wsId) { chatViewModel.initConversation(convId, wsId) }
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToWorkspace = { navController.navigate(Screen.WorkspaceDetail.createRoute(it)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.WorkspaceDetail.route,
            arguments = listOf(navArgument("workspaceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val wsId = backStackEntry.arguments?.getString("workspaceId").orEmpty()
            val workspaceViewModel: WorkspaceViewModel = viewModel(
                factory = WorkspaceViewModel.Factory(wsId, appContainer.workspaceRepository)
            )
            WorkspaceScreen(
                viewModel = workspaceViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenInChat = { navController.navigate(Screen.Chat.createRoute(workspaceId = it)) }
            )
        }

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
                languageRepository = appContainer.languageRepository,
                onNavigateBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Onboarding.route) { popUpTo(0) { inclusive = true } }
                }
            )
        }
    }
}
