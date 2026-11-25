package com.example.data.network

data class ModelOption(
    val displayName: String,
    val apiModelId: String,
    val inputPricePer1M: Double,
    val outputPricePer1M: Double
)

data class ProviderSpec(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val models: List<ModelOption>
)

object ProviderCatalog {
    val providers: List<ProviderSpec> = listOf(
        ProviderSpec(
            id = "gemini",
            displayName = "Google Gemini",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf(
                ModelOption("Gemini 4 Hydrogen", "gemini-4-hydrogen", 0.30, 1.20),
                ModelOption("Gemini 4 Neon", "gemini-4-neon", 1.25, 5.00),
                ModelOption("Gemini 3.6 Pro", "gemini-3.6-pro", 1.25, 5.00),
                ModelOption("Gemini 2.5 Flash", "gemini-2.5-flash", 0.15, 0.60),
                ModelOption("Gemini 2.5 Flash-Lite", "gemini-2.5-flash-lite", 0.075, 0.30)
            )
        ),
        ProviderSpec(
            id = "openai",
            displayName = "OpenAI (ChatGPT)",
            defaultBaseUrl = "https://api.openai.com/v1",
            models = listOf(
                ModelOption("GPT-4o", "gpt-4o", 2.50, 10.00),
                ModelOption("o3-mini", "o3-mini", 1.10, 4.40),
                ModelOption("GPT-4.1", "gpt-4.1", 2.00, 8.00)
            )
        ),
        ProviderSpec(
            id = "anthropic",
            displayName = "Anthropic (Claude)",
            defaultBaseUrl = "https://api.anthropic.com/v1",
            models = listOf(
                ModelOption("Claude Opus 4", "claude-opus-4-20250514", 15.00, 75.00),
                ModelOption("Claude Sonnet 4", "claude-sonnet-4-20250514", 3.00, 15.00),
                ModelOption("Claude Haiku", "claude-3-5-haiku-latest", 0.80, 4.00)
            )
        ),
        ProviderSpec(
            id = "deepseek",
            displayName = "DeepSeek",
            defaultBaseUrl = "https://api.deepseek.com/v1",
            models = listOf(
                ModelOption("DeepSeek V4 Pro", "deepseek-v4-pro", 0.55, 2.19),
                ModelOption("DeepSeek V3", "deepseek-chat", 0.27, 1.10),
                ModelOption("DeepSeek R1", "deepseek-reasoner", 0.55, 2.19)
            )
        ),
        ProviderSpec(
            id = "azure",
            displayName = "Microsoft Copilot (Azure OpenAI)",
            defaultBaseUrl = "https://YOUR_RESOURCE.openai.azure.com/openai/deployments/copilot-pro",
            models = listOf(
                ModelOption("Copilot Pro (Azure Endpoint)", "copilot-pro", 2.50, 10.00)
            )
        ),
        ProviderSpec(
            id = "custom",
            displayName = "Custom / Aggregator (OpenRouter / aniModels)",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            models = listOf(
                ModelOption("OpenRouter Auto", "openrouter/auto", 0.50, 1.50),
                ModelOption("Gemini 2.5 Flash (OR)", "google/gemini-2.5-flash", 0.15, 0.60),
                ModelOption("DeepSeek V3 (OR)", "deepseek/deepseek-chat", 0.27, 1.10),
                ModelOption("Claude Sonnet 4 (OR)", "anthropic/claude-sonnet-4", 3.00, 15.00)
            )
        )
    )

    fun findProvider(id: String): ProviderSpec =
        providers.find { it.id == id } ?: providers.first()

    fun estimateCostUsd(modelId: String, promptTokens: Long, completionTokens: Long): Double {
        val option = providers.flatMap { it.models }
            .find { it.apiModelId.equals(modelId, ignoreCase = true) || it.displayName.equals(modelId, ignoreCase = true) }
        val inPrice = option?.inputPricePer1M ?: 0.50
        val outPrice = option?.outputPricePer1M ?: 1.50
        return (promptTokens / 1_000_000.0) * inPrice + (completionTokens / 1_000_000.0) * outPrice
    }
}
