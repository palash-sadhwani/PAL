package com.palash.pal.domain.tools

data class ToolResult(
    val success: Boolean,
    val output: String? = null,
    val errorMessage: String? = null
)
