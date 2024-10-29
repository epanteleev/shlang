package shlang

import kotlin.test.*
import common.CommonCTest


sealed class UnionTests : CommonCTest() {
    @Test
    fun testUnion0() {
        val result = runCTest("shlang/union/union0", listOf("runtime/runtime.c"), options())
        assertEquals("1.000000\n", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testUnion1() {
        val result = runCTest("shlang/union/union1", listOf("runtime/runtime.c"), options())
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testUnion2() {
        val result = runCTest("shlang/union/union2", listOf("runtime/runtime.c"), options())
        assertEquals("1.000000\n", result.output)
        assertEquals(0, result.exitCode)
    }
}

class UnionTestsO0: UnionTests() {
    override fun options(): List<String> = listOf()
}

class UnionTestsO1: UnionTests() {
    override fun options(): List<String> = listOf("-O1")
}