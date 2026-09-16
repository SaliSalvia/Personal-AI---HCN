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
        if (lower.contains("coder") || lower.contains("code") || lower.contains("deepseek") || lower.contains("qwen2.5-72b")) {
            caps.add(ModelCapability.CODING)
        }

        // Vision models
        if (lower.contains("vl") || lower.contains("vision") || lower.contains("4o") || lower.contains("omni")) {
            caps.add(ModelCapability.VISION)
        }

        // Large Context
        if (lower.contains("128k") || lower.contains("long") || lower.contains("deepseek") || lower.contains("qwen")) {
            caps.add(ModelCapability.LARGE_CONTEXT)
        }

        // Fast chat
        if (lower.contains("mini") || lower.contains("flash") || lower.contains("speed") || lower.contains("turbo") || lower.contains("7b") || lower.contains("8b")) {
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
     * Routes to the optimal available HCNSEC model for the given task category.
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
            TaskCategory.GENERAL_CHAT -> ModelCapability.FAST_CHAT
            else -> null
        }

        if (priorityCapability != null) {
            val matching = availableModels.firstOrNull { it.capabilities.contains(priorityCapability) }
            if (matching != null) return matching
        }

        // Return first model or default
        return availableModels.firstOrNull()
    }
}
