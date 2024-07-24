package shlang

import common.CommonCTest
import kotlin.test.Test


abstract class FunTests: CommonCTest() {
    @Test
    fun test1() {
        val result = runCTest("shlang/1", listOf(), options())
        assertReturnCode(result, 57)
    }

    @Test
    fun test2() {
        val result = runCTest("shlang/2", listOf(), options())
        assertReturnCode(result, 0)
    }

    @Test
    fun test3() {
        val result = runCTest("shlang/3", listOf(), options())
        assertReturnCode(result, 20)
    }

    @Test
    fun test4() {
        val result = runCTest("shlang/4", listOf(), options())
        assertReturnCode(result, 0)
    }

    @Test
    fun test5() {
        val result = runCTest("shlang/5", listOf(), options())
        assertReturnCode(result, 20)
    }

    @Test
    fun test6() {
        val result = runCTest("shlang/6", listOf(), options())
        assertReturnCode(result, 10)
    }

    @Test
    fun test7() {
        val result = runCTest("shlang/7", listOf(), options())
        assertReturnCode(result, 20)
    }

    @Test
    fun test8() {
        val result = runCTest("shlang/8", listOf(), options())
        assertReturnCode(result, 60)
    }

    @Test
    fun test9() {
        val result = runCTest("shlang/9", listOf(), options())
        assertReturnCode(result, 49)
    }

    @Test
    fun test10() {
        val result = runCTest("shlang/10", listOf(), options())
        assertReturnCode(result, 0)
    }
}

class FunTestsO0: FunTests() {
    override fun options(): List<String> = listOf()
}

class FunTestsO1: FunTests() {
    override fun options(): List<String> = listOf("-O1")
}