package shlang

import common.CommonCTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals


abstract class ShlangTests: CommonCTest() {
    @Test
    fun testArrayAccess() {
        val result = runCTest("shlang/arrayAccess", listOf(), options())
        assertReturnCode(result, 3)
    }

    @Test
    fun testArrayAccess1() {
        val result = runCTest("shlang/array_access1", listOf(), options())
        assertEquals("42 43 44 45 ", result.output)
    }

    @Test
    fun testArrayAccess2() {
        val result = runCTest("shlang/array_access2", listOf(), options())
        assertEquals("42 43 44 45 ", result.output)
    }

    @Test
    fun testAnd1() {
        val result = runCTest("shlang/and1", listOf(), options())
        assertReturnCode(result, 3)
    }

    @Test
    fun testDiscriminant() {
        val result = runCTest("shlang/discriminant1", listOf("runtime/runtime.c"), options())
        assertEquals("1.000000\n", result.output)
        assertReturnCode(result, 0)
    }

    @Test
    fun testMemsetMemcpy() {
        val result = runCTest("shlang/memset/memset_arrays", listOf("shlang/memset/memset_test.c"), options())
        assert(result, "Done")
    }

    @Test
    fun testSameNames() {
        val result = runCTest("shlang/sameNames", listOf("runtime/runtime.c"), options())
        assert(result, "1\n")
    }

    @Test
    fun testSum8() {
        val result = runCTest("shlang/sum8", listOf("runtime/runtime.c"), options())
        assert(result, "8.000000\n9.000000\n")
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testIfElse0() {
        val result = runCTest("shlang/ifElse0", listOf("runtime/runtime.c"), options())
        assert(result, "")
        assertReturnCode(result, 0)
    }

    @Test
    fun testFibonacci() {
        val result = runCTest("shlang/fibonacci1", listOf("runtime/runtime.c"), options())
        assert(result, "55\n")
    }

    @Test
    fun testFibonacciRecursive() {
        val result = runCTest("shlang/fibonacci_rec", listOf("runtime/runtime.c"), options())
        assertEquals(55, result.exitCode)
    }

    @Test
    fun testArithmetic0() {
        val result = runCTest("shlang/arithmetic0", listOf("runtime/runtime.c"), options())
        assert(result, "4\n")
    }

    @Test
    fun testSizeof0() {
        val result = runCTest("shlang/sizeof0", listOf("runtime/runtime.c"), options())
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testSizeof1() {
        val result = runCTest("shlang/sizeof1", listOf("runtime/runtime.c"), options())
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testCast1() {
        val result = runCTest("shlang/cast1", listOf("runtime/runtime.c"), options())
        assertEquals(23, result.exitCode)
    }

    @Test
    fun testDeref1() {
        val result = runCTest("shlang/deref", listOf("runtime/runtime.c"), options())
        assertEquals(67, result.exitCode)
    }

    @Test
    fun testPrintInt() {
        val result = runCTest("shlang/printInt", listOf("runtime/runtime.c"), options())
        assertEquals(20, result.exitCode)
    }

    @Test
    fun testFunctionCall1() {
        val result = runCTest("shlang/functionCall1", listOf("runtime/runtime.c"), options())
        assertEquals(8, result.exitCode)
    }

    @Test
    fun testMath1() {
        val result = runCTest("shlang/math1", listOf("runtime/runtime.c"), options())
        assertEquals(4, result.exitCode)
    }

    @Test
    fun testCollatz() {
        val result = runCTest("shlang/collatz_rec", listOf("runtime/runtime.c"), options())
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testSizeofTypes() {
        val result = runCTest("shlang/sizeof_types", listOf("runtime/runtime.c"), options())
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testGOTO() {
        val result = runCTest("shlang/goto", listOf("runtime/runtime.c"), options())
        assertEquals(220, result.exitCode)
    }

    @Test
    fun testStrcmp1() {
        val result = runCTest("shlang/strcmp1", listOf("runtime/runtime.c"), options())
        assertEquals(0, result.exitCode)
    }
}

class ShlangTestsO0: ShlangTests() {
    override fun options(): List<String> = listOf()
}

class ShlangTestsO1: ShlangTests() {
    override fun options(): List<String> = listOf("-O1")
}