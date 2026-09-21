package com.example.data.api

enum class AiProvider(
    val displayName: String,
    val description: String,
    val requiresModelPrefix: Boolean = false
) {
    HCNSEC("HCNSEC", "Official HCNSEC OpenAI-compatible API"),
    GOOGLE_AI_STUDIO("Google AI Studio", "Native Gemini API"),
    GROQ("Groq", "Ultra-fast OpenAI-compatible inference"),
    OPEN_ROUTER("OpenRouter", "Access many models through one API");

    companion object {
        fun detectFromKey(key: String): AiProvider? = when {
            key.trim().startsWith("AIza") -> GOOGLE_AI_STUDIO
            key.trim().startsWith("gsk_") -> GROQ
            key.trim().startsWith("sk-or-") -> OPEN_ROUTER
            else -> null
        }
    }
}
