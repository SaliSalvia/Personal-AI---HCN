package com.example.data.api

enum class AiProvider(
    val displayName: String,
    val description: String,
    val defaultBaseUrl: String? = null,
    val requiresModelPrefix: Boolean = false
) {
    HCNSEC("HCNSEC", "Official HCNSEC OpenAI-compatible API", "https://api.hcnsec.cn/v1"),
    GOOGLE_AI_STUDIO("Google AI Studio", "Native Gemini API", "https://generativelanguage.googleapis.com/v1beta"),
    GROQ("Groq", "Fast OpenAI-compatible inference with a generous free tier", "https://api.groq.com/openai/v1"),
    OPEN_ROUTER("OpenRouter", "Many free and paid models through one API", "https://openrouter.ai/api/v1"),
    CEREBRAS("Cerebras", "Very fast inference for open models", "https://api.cerebras.ai/v1"),
    SAMBANOVA("SambaNova", "Open-model inference with a free developer tier", "https://api.sambanova.ai/v1"),
    TOGETHER("Together AI", "Open models with trial credits and pay-as-you-go", "https://api.together.xyz/v1"),
    DEEPINFRA("DeepInfra", "Open-model inference with free daily credits", "https://api.deepinfra.com/v1/openai"),
    FIREWORKS("Fireworks AI", "Fast open-model serverless inference", "https://api.fireworks.ai/inference/v1"),
    MISTRAL("Mistral AI", "Le Chat and Mistral API platform", "https://api.mistral.ai/v1"),
    COHERE("Cohere", "Command models and retrieval APIs", "https://api.cohere.com/compatibility/v1"),
    NVIDIA("NVIDIA NIM", "Hosted NVIDIA and open models", "https://integrate.api.nvidia.com/v1"),
    HUGGING_FACE("Hugging Face", "Inference providers and open models", "https://router.huggingface.co/v1"),
    OPENAI("OpenAI", "Official OpenAI API (paid, with occasional credits)", "https://api.openai.com/v1"),
    CUSTOM("Custom endpoint", "Any OpenAI-compatible API endpoint", null);

    companion object {
        /** Auto-detect provider from key prefix when possible.
         * Returns null for providers/custom endpoints that require explicit selection. */
        fun detectFromKey(key: String): AiProvider? = when {
            key.trim().startsWith("AIza") -> GOOGLE_AI_STUDIO
            key.trim().startsWith("gsk_") -> GROQ
            key.trim().startsWith("sk-or-") -> OPEN_ROUTER
            else -> null
        }

        val catalog: List<AiProvider> = entries

        /** Default provider for a provider-neutral app is HCNSEC. */
        val defaultProvider: AiProvider get() = HCNSEC
    }
}
