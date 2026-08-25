package com.palash.pal.domain.tools

interface ToolPermissionPolicy {
    suspend fun canExecute(tool: Tool): Boolean
}
