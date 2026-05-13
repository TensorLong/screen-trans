package com.yiqun.translator.data.remote.ai.chatgpt

data class AiModelIcon(
    val label: String,
    val contentDescription: String,
    val backgroundColor: Long,
    val contentColor: Long = 0xFFFFFFFF,
)

object AiModelIconResolver {
    fun resolve(model: String?): AiModelIcon {
        val normalized = model.orEmpty().trim().lowercase()
        return when {
            normalized.isBlank() -> generic()
            normalized.contains("qwen") ||
                    normalized.contains("tongyi") ||
                    normalized.contains("dashscope") ||
                    normalized.contains("通义") ||
                    normalized.contains("千问") -> AiModelIcon("千", "Qwen", 0xFF6A4CFF)
            normalized.contains("deepseek") -> AiModelIcon("DS", "DeepSeek", 0xFF2563EB)
            normalized.contains("gemini") ||
                    normalized.contains("google/") -> AiModelIcon("G", "Gemini", 0xFF1A73E8)
            normalized.contains("claude") ||
                    normalized.contains("anthropic") -> AiModelIcon("C", "Claude", 0xFFC15F3C)
            normalized.contains("gpt") ||
                    normalized.contains("openai") ||
                    normalized.matches(Regex(".*\\bo[134]\\b.*")) -> AiModelIcon("GPT", "OpenAI", 0xFF10A37F)
            normalized.contains("llama") ||
                    normalized.contains("meta") -> AiModelIcon("L", "Llama", 0xFF3B82F6)
            normalized.contains("mistral") -> AiModelIcon("M", "Mistral", 0xFFFF7000)
            normalized.contains("kimi") ||
                    normalized.contains("moonshot") -> AiModelIcon("K", "Kimi", 0xFF111827)
            else -> generic()
        }
    }

    private fun generic(): AiModelIcon = AiModelIcon("AI", "AI model", 0xFF4B5563)
}
