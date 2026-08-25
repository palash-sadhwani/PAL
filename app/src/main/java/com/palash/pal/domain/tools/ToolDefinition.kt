package com.palash.pal.domain.tools

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val inputDefinition: String,
    val requiredPermissions: Set<String> = emptySet()
)
