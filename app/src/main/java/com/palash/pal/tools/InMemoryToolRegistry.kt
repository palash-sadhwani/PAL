package com.palash.pal.tools

import com.palash.pal.domain.tools.Tool
import com.palash.pal.domain.tools.ToolRegistry
import java.util.concurrent.ConcurrentHashMap

class InMemoryToolRegistry : ToolRegistry {
    private val tools = ConcurrentHashMap<String, Tool>()

    override fun register(tool: Tool) {
        tools[tool.definition.id] = tool
    }

    override fun getTool(id: String): Tool? = tools[id]

    override fun getTools(): List<Tool> = tools.values.sortedBy { it.definition.id }
}
