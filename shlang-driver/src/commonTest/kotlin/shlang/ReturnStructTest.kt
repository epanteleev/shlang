package shlang

import common.CommonCTest
import kotlin.test.Test
import kotlin.test.assertEquals


abstract class ReturnStructTest: CommonCTest() {
    @Test
    fun testReturnStruct() {
        val result = runCTest("shlang/return_struct/return_struct", listOf("runtime/runtime.c"), options())
        assertEquals("x: 1, y: 2\n", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testReturnStruct1() {
        val result = runCTest("shlang/return_struct/return_struct1", listOf("runtime/runtime.c"), options())
        assertEquals("x: 1, y: 2, z: 3\n", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testReturnStruct2() {
        val result = runCTest("shlang/return_struct/return_struct2", listOf("runtime/runtime.c"), options())
        assertEquals("x: 1.000000, y: 2.000000, z: 3.000000\n", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testReturnStruct3() {
        val result = runCTest("shlang/return_struct/return_struct3", listOf("runtime/runtime.c"), options())
        assertEquals("x: 1.000000, y: 2.000000, z: 3\n", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testReturnStruct4() {
        val result = runCTest("shlang/return_struct/return_struct4", listOf("runtime/runtime.c"), options())
        assertEquals("x: 1 y: 2 z: 3 w: 4 v: 5 u: 6 t: 7 s: 8\n", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testReturnStruct5() {
        val result = runCTest("shlang/return_struct/return_struct5", listOf("runtime/runtime.c"), options())
        assertEquals("x: 1, y: 2, z: 3\n", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testReturnStruct6() {
        val result = runCTest("shlang/return_struct/return_struct6", listOf("runtime/runtime.c"), options())
        assertEquals("x: 1 y: 2 z: 3 w: 4 v: 5\n", result.output)
        assertEquals(0, result.exitCode)
    }
}

class ReturnStructTestO0: ReturnStructTest() {
    override fun options(): List<String> = listOf()
}

class ReturnStructTestO1: ReturnStructTest() {
    override fun options(): List<String> = listOf("-O1")
}