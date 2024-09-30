package ssa.ir

import ir.types.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class StructTypeTest {
    @Test
    fun test1() {
        val structType = StructType("Point", arrayListOf(Type.I32, Type.I32))
        assertEquals(8, structType.sizeOf())
        assertEquals(0, structType.offset(0))
        assertEquals(4, structType.offset(1))
        assertFalse { structType.hasFloatOnly(0, 4) }
        assertFalse { structType.hasFloatOnly(4, 8) }
    }

    @Test
    fun test2() {
        val structType = StructType("Point", arrayListOf(Type.I32, Type.I64))
        assertEquals(16, structType.sizeOf())
    }

    @Test
    fun test3() {
        val pointType = StructType("Point", arrayListOf(Type.I32, Type.I64))
        val structType1 = StructType("Rect", arrayListOf(pointType, pointType))
        val structType2 = StructType("Rect", arrayListOf(pointType, Type.I64))
        val structType3 = StructType("Rect", arrayListOf(Type.I64, pointType))
        assertEquals(32, structType1.sizeOf())
        assertEquals(24, structType2.sizeOf())
        assertEquals( 24, structType3.sizeOf())
    }

    @Test
    fun test4() {
        val structType = StructType("Point", arrayListOf(Type.I32, Type.I8))
        assertEquals(8, structType.sizeOf())
    }

    @Test
    fun test5() {
        val structType = StructType("Point", arrayListOf(Type.I8, Type.I32))
        assertEquals(8, structType.sizeOf())
    }

    @Test
    fun test6() {
        val structType = StructType("Point", arrayListOf(Type.I32, Type.I8))
        assertEquals(0, structType.offset(0))
        assertEquals(4, structType.offset(1))
    }

    @Test
    fun test7() {
        val structType = StructType("Point", arrayListOf(Type.I32, Type.Ptr))
        assertEquals(16, structType.sizeOf())
        assertEquals(0, structType.offset(0))
        assertEquals(8, structType.offset(1))
    }

    @Test
    fun test8() {
        val structType = StructType("Vect", arrayListOf(Type.F32, Type.F32, Type.F32))
        assertEquals(12, structType.sizeOf())
        assertEquals(0, structType.offset(0))
        assertEquals(4, structType.offset(1))
        assertEquals(8, structType.offset(2))
        assertTrue { structType.hasFloatOnly(0, 4) }
        assertTrue { structType.hasFloatOnly(0, 8) }
    }

    @Test
    fun test9() {
        val structType = StructType("Point", arrayListOf(Type.I32, Type.F32))
        assertEquals(8, structType.sizeOf())
        assertEquals(0, structType.offset(0))
        assertEquals(4, structType.offset(1))
        assertFalse { structType.hasFloatOnly(0, 4) }
        assertTrue { structType.hasFloatOnly(4, 8) }
        assertFalse { structType.hasFloatOnly(0, 8) }
    }

    @Test
    fun test10() {
        val pointType = StructType("Point", arrayListOf(Type.I32, Type.F64))
        val structType1 = StructType("Rect", arrayListOf(pointType, pointType))

        assertEquals(32, structType1.sizeOf())
        assertFalse { structType1.hasFloatOnly(0, 8) }
        assertFalse { structType1.hasFloatOnly(0, 4) }
        assertTrue { structType1.hasFloatOnly(8, 16) }
    }

    @Test
    fun test11() {
        val structType = StructType("Point", arrayListOf(Type.I64, Type.I32))
        assertEquals(16, structType.sizeOf())
        assertEquals(0, structType.offset(0))
        assertEquals(8, structType.offset(1))
//        assertFalse { structType.hasFloatOnly(4, 8) } TODO failure
        assertFalse { structType.hasFloatOnly(0, 4) }
    }
}