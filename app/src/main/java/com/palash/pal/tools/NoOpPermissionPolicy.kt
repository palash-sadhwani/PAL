package com.palash.pal.tools

import com.palash.pal.domain.tools.Tool
import com.palash.pal.domain.tools.ToolPermissionPolicy

class NoOpPermissionPolicy : ToolPermissionPolicy {
    override suspend fun canExecute(tool: Tool): Boolean = true
}
