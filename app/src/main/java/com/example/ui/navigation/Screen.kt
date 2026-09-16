package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Chat : Screen("chat?conversationId={conversationId}&workspaceId={workspaceId}") {
        fun createRoute(conversationId: String? = null, workspaceId: String? = null): String {
            val params = mutableListOf<String>()
            if (!conversationId.isNullOrEmpty()) params.add("conversationId=$conversationId")
            if (!workspaceId.isNullOrEmpty()) params.add("workspaceId=$workspaceId")
            return if (params.isNotEmpty()) "chat?${params.joinToString("&")}" else "chat"
        }
    }
    object WorkspaceDetail : Screen("workspace/{workspaceId}") {
        fun createRoute(workspaceId: String): String = "workspace/$workspaceId"
    }
    object Settings : Screen("settings")
}
