package com.palash.pal.domain.tools

interface ToolRegistry {
    fun register(tool: Tool)

    fun getTool(id: String): Tool?

    fun getTools(): List<Tool>
}
