package com.example.domain.router

import com.example.domain.model.AiModel
import com.example.domain.model.ModelCapability
import com.example.domain.model.TaskCategory

/**
 * CapabilityRegistry detects and documents official capabilities for HCNSEC models
 * based on verified model identifiers.
 */
object CapabilityRegistry {

    fun detectCapabilities(modelId: String): Set<ModelCapability> {
        val lower = modelId.lowercase()
        val caps = mutableSetOf<ModelCapability>()

        // Reasoning models
        if (lower.contains("reasoner") || lower.contains("r1") || lower.contains("o1") || lower.contains("thinking")) {
            caps.add(ModelCapability.REASONING)
        }

        // Coding models
        if (
            lower.contains("coder") || lower.contains("code") || lower.contains("-dev") ||
            lower.contains("deepseek") || lower.contains("qwen2.5-72b") || lower.contains("qwen3") ||
            lower.contains("codestral") ||
            lower.startsWith("gpt-oss-")
        ) {
            caps.add(ModelCapability.CODING)
        }

        // Vision models
        if (lower.contains("vl") || lower.contains("vision") || lower.contains("4o") || lower.contains("omni")) {
            caps.add(ModelCapability.VISION)
        }

        // Large Context
        if (
            lower.contains("128k") || lower.contains("long") || lower.contains("deepseek") ||
            lower.contains("qwen") || lower.contains("gpt-oss") || lower.contains("gemini") ||
            lower.contains("nemotron") || lower.contains("llama")
        ) {
            caps.add(ModelCapability.LARGE_CONTEXT)
        }

        // Fast chat — smallest/lowest-latency tiers across the free-provider catalogs.
        if (
            lower.contains("mini") || lower.contains("flash") || lower.contains("flash-lite") ||
            lower.contains("speed") || lower.contains("turbo") || lower.contains("instant") ||
            lower.contains("lightning") ||
            lower.contains("-8b") || lower.contains("-7b") || lower.contains("3b-") ||
            lower.contains("-3b") || lower.contains("-4b") || lower.contains("-6b") ||
            lower.contains("27b") ||
            lower.contains("litert") ||
            lower.startsWith("gpt-oss-20b") || lower.contains("small-3") ||
            lower.contains("nemotron-nano")
        ) {
            caps.add(ModelCapability.FAST_CHAT)
        }

        // General creative
        caps.add(ModelCapability.CREATIVE)

        return caps
    }
}

class AutoModelRouter {

    /**
     * Classifies the user query, attached files, and workspace context into a TaskCategory.
     */
    fun classifyTask(
        query: String,
        hasAttachments: Boolean,
        hasZipWorkspace: Boolean,
        hasImages: Boolean
    ): TaskCategory {
        if (hasZipWorkspace) {
            return TaskCategory.FILE_PROJECT_ANALYSIS
        }

        if (hasImages) {
            return TaskCategory.VISION
        }

        val text = query.lowercase().trim()

        // Coding indicators
        val codingKeywords = listOf(
            "function", "fun ", "def ", "class ", "interface", "bug", "refactor",
            "compile", "gradle", "kotlin", "java", "python", "javascript", "typescript",
            "sql", "api", "regex", "algorithm", "git", "exception", "error", "stacktrace"
        )
        if (codingKeywords.any { text.contains(it) } || text.contains("```") || text.contains("fix this code")) {
            return TaskCategory.CODING
        }

        // Deep Reasoning indicators
        val reasoningKeywords = listOf(
            "prove", "proof", "solve", "step by step", "math", "derive", "calculate",
            "logic puzzle", "why does", "deduce", "theorem", "hypothesis", "reasoning"
        )
        if (reasoningKeywords.any { text.contains(it) }) {
            return TaskCategory.REASONING
        }

        // Summarization indicators
        val summaryKeywords = listOf(
            "summarize", "summary", "tldr", "tl;dr", "brief", "key points", "overview"
        )
        if (summaryKeywords.any { text.contains(it) }) {
            return TaskCategory.SUMMARIZATION
        }

        // Document analysis
        if (hasAttachments || text.contains("analyze this document") || text.contains("read this pdf")) {
            return TaskCategory.DOCUMENT_ANALYSIS
        }

        // Extraction
        val extractionKeywords = listOf(
            "extract", "json", "csv", "table", "schema", "parse", "structure this"
        )
        if (extractionKeywords.any { text.contains(it) }) {
            return TaskCategory.STRUCTURED_EXTRACTION
        }

        // Creative
        val creativeKeywords = listOf(
            "write a poem", "story", "novel", "creative", "brainstorm", "slogan", "pitch"
        )
        if (creativeKeywords.any { text.contains(it) }) {
            return TaskCategory.CREATIVE
        }

        return TaskCategory.GENERAL_CHAT
    }

    /**
     * Published generation-speed tier of each provider's fastest endpoint, ranked
     * fastest first. Values come from the vendors' published model catalogs
     * (Cerebras ~3000 tok/s for gpt-oss-120b, Groq ~1000 tok/s for gpt-oss-20b,
     * SambaNova ~430 tok/s, Gemini Flash-Lite optimized for volume, OpenRouter free
     * roster is dynamic). Tiers are coarse on purpose: they encode *relative* priority,
     * not exact tok/s, so the ranking stays valid as numbers shift over time.
     */
    private val providerSpeedOrder = listOf(
        "CEREBRAS",       // ~3000 tok/s catalog tops
        "GROQ",           // ~1000 tok/s
        "GOOGLE_AI_STUDIO", // Flash-Lite: fast, most generous renewable free tier
        "SAMBANOVA",      // historically ~430 tok/s
        "HCNSEC",         // user-preferred official endpoint
        "OPEN_ROUTER",    // :free models are slow at peak, useful fallback
        "NVIDIA",
        "MISTRAL",
        "FIREWORKS",
        "DEEPINFRA",
        "TOGETHER",
        "COHERE",
        "HUGGING_FACE",
        "OPENAI",
        "CUSTOM"
    )

    private fun speedRank(model: AiModel): Int {
        val idx = providerSpeedOrder.indexOf(model.providerId)
        return if (idx >= 0) idx else providerSpeedOrder.size
    }

    /**
     * Routes to the optimal available model for the given task category. Ties are
     * broken for maximum speed: provider speed tier first, then the FAST_CHAT flag
     * (smaller models stream with lower latency and stronger published tok/s), then
     * the original catalog order.
     */
    fun selectModel(
        task: TaskCategory,
        availableModels: List<AiModel>
    ): AiModel? {
        if (availableModels.isEmpty()) return null

        val priorityCapability = when (task) {
            TaskCategory.REASONING -> ModelCapability.REASONING
            TaskCategory.CODING, TaskCategory.FILE_PROJECT_ANALYSIS -> ModelCapability.CODING
            TaskCategory.VISION -> ModelCapability.VISION
            TaskCategory.LARGE_CONTEXT, TaskCategory.DOCUMENT_ANALYSIS -> ModelCapability.LARGE_CONTEXT
            // Chat latency is dominated by streaming speed: prefer fast models.
            TaskCategory.GENERAL_CHAT, TaskCategory.SUMMARIZATION -> ModelCapability.FAST_CHAT
            else -> null
        }

        val pool = if (priorityCapability != null) {
            val matching = availableModels.filter { it.capabilities.contains(priorityCapability) }
            matching.ifEmpty { availableModels }
        } else {
            availableModels
        }

        // Speed-first ranking within the filtered pool.
        return pool
            .sortedWith(
                compareBy(
                    { speedRank(it) },
                    { !it.capabilities.contains(ModelCapability.FAST_CHAT) },
                    { it.displayName.lowercase() }
                )
            )
            .firstOrNull()
    }
}
