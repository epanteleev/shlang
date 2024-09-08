package gen

import gen.consteval.*
import ir.global.*
import ir.module.builder.impl.ModuleBuilder
import ir.types.*
import ir.value.Constant
import ir.value.InitializerListValue
import ir.value.StringLiteralConstant
import ir.value.Value
import parser.nodes.Expression
import parser.nodes.InitializerList
import parser.nodes.SingleInitializer
import parser.nodes.StringNode
import types.*


abstract class AbstractIRGenerator(protected val mb: ModuleBuilder,
                                   protected val typeHolder: TypeHolder,
                                   protected val varStack: VarStack<Value>,
                                   protected val nameGenerator: NameGenerator) {
    protected fun constEvalExpression(lValueType: NonTrivialType, expr: Expression): Constant? {
        val result = constEvalExpression0(expr)
        if (result != null) {
            return Constant.of(lValueType, result)
        }
        return when (expr) {
            is InitializerList   -> constEvalInitializers(lValueType, expr)
            is SingleInitializer -> constEvalExpression(lValueType, expr.expr)
            is StringNode        -> StringLiteralConstant(expr.data())
            else -> throw IRCodeGenError("Unsupported expression $expr")
        }
    }

    private fun constEvalExpression0(expr: Expression): Number? = when (expr.resolveType(typeHolder)) {
        CType.CINT, CType.CSHORT, CType.CCHAR, CType.CUINT, CType.CUSHORT, CType.CUCHAR -> {
            val ctx = CommonConstEvalContext<Int>(typeHolder)
            ConstEvalExpression.eval(expr, ConstEvalExpressionInt(ctx))
        }
        CType.CLONG, CType.CULONG -> {
            val ctx = CommonConstEvalContext<Long>(typeHolder)
            ConstEvalExpression.eval(expr, ConstEvalExpressionLong(ctx))
        }
        CType.CFLOAT -> {
            val ctx = CommonConstEvalContext<Float>(typeHolder)
            ConstEvalExpression.eval(expr, ConstEvalExpressionFloat(ctx))
        }
        CType.CDOUBLE -> {
            val ctx = CommonConstEvalContext<Double>(typeHolder)
            ConstEvalExpression.eval(expr, ConstEvalExpressionDouble(ctx))
        }
        else -> null
    }

    private fun constEvalInitializers(lValueType: NonTrivialType, expr: InitializerList): InitializerListValue {
        if (lValueType !is AggregateType) {
            throw IRCodeGenError("Unsupported type $lValueType")
        }
        val elements = expr.initializers.mapIndexed { index, initializer ->
            val elementLValueType = lValueType.field(index)
            constEvalExpression(elementLValueType, initializer) ?: throw IRCodeGenError("Unsupported type $elementLValueType")
        }

        return InitializerListValue(lValueType, elements)
    }

    protected fun createStringLiteralName(): String {
        return nameGenerator.createStringLiteralName()
    }

    protected fun createGlobalConstantName(): String {
        return nameGenerator.createGlobalConstantName()
    }

    private fun tryMakeGlobalAggregateConstant(lValueType: NonTrivialType, expr: Expression): AnyAggregateGlobalConstant? = when (expr) {
        is StringNode -> {
            val content = expr.data()
            StringLiteralGlobalConstant(createStringLiteralName(), ArrayType(Type.U8, content.length), content)
        }
        is InitializerList -> when (lValueType) {
            is ArrayType -> {
                val elements = constEvalInitializers(lValueType, expr)
                ArrayGlobalConstant(createStringLiteralName(), elements)
            }

            is StructType -> {
                val elements = constEvalInitializers(lValueType, expr)
                StructGlobalConstant(createStringLiteralName(), elements)
            }

            else -> throw IRCodeGenError("Unsupported type $lValueType")
        }
        else -> null
    }
}