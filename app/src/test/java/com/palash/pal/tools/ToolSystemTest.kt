package com.palash.pal.tools

import com.palash.pal.domain.tools.Tool
import com.palash.pal.domain.tools.ToolDefinition
import com.palash.pal.domain.tools.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSystemTest {
    @Test
    fun registryStoresAndReturnsTools() = runTest {
        val registry = InMemoryToolRegistry()
        val tool = SampleTool()

        registry.register(tool)

        assertEquals(tool, registry.getTool("sample-tool"))
        assertEquals(listOf(tool), registry.getTools())
    }

    @Test
    fun noOpPermissionPolicyAlwaysAllows() = runTest {
        val policy = NoOpPermissionPolicy()

        assertTrue(policy.canExecute(SampleTool()))
    }

    private class SampleTool : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            id = "sample-tool",
            name = "Sample Tool",
            description = "A sample tool for tests",
            inputDefinition = "string"
        )

        override suspend fun execute(input: String): ToolResult {
            return ToolResult(success = true, output = input)
        }
    }
}
