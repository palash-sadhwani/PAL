package com.palash.pal.domain.tools

interface Tool {
    val definition: ToolDefinition

    suspend fun execute(input: String): ToolResult
}
