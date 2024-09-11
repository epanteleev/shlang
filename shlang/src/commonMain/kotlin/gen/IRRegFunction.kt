package gen

import types.*
import ir.types.*
import ir.value.*
import parser.nodes.*
import common.assertion
import gen.TypeConverter.coerceArguments
import ir.instruction.*
import ir.instruction.Alloc
import ir.module.block.Label
import gen.TypeConverter.convertToType
import gen.TypeConverter.toIRType
import gen.TypeConverter.toIndexType
import gen.consteval.CommonConstEvalContext
import gen.consteval.ConstEvalExpression
import gen.consteval.ConstEvalExpressionInt
import ir.Definitions.QWORD_SIZE
import ir.attributes.GlobalValueAttribute
import ir.global.StringLiteralGlobalConstant
import ir.module.AnyFunctionPrototype
import ir.module.IndirectFunctionPrototype
import ir.module.builder.impl.ModuleBuilder
import ir.module.builder.impl.FunctionDataBuilder
import parser.nodes.visitors.DeclaratorVisitor
import parser.nodes.visitors.StatementVisitor


class IrGenFunction(moduleBuilder: ModuleBuilder,
                    typeHolder: TypeHolder,
                    varStack: VarStack<Value>,
                    nameGenerator: NameGenerator):
    AbstractIRGenerator(moduleBuilder, typeHolder, varStack, nameGenerator),
    StatementVisitor<Unit>,
    DeclaratorVisitor<Value> {
    private var currentFunction: FunctionDataBuilder? = null
    private var returnValueAdr: Alloc? = null
    private var exitBlock: Label = Label.entry //TODO late initialization
    private var stringTolabel = mutableMapOf<String, Label>()
    private val stmtStack = StmtStack()
    private val initializerContext = InitializerContext()

    private val ir: FunctionDataBuilder
        get() = currentFunction ?: throw IRCodeGenError("Function expected")

    private inline fun<reified T> scoped(noinline block: () -> T): T {
        return typeHolder.scoped { varStack.scoped(block) }
    }

    private fun seekOrAddLabel(name: String): Label {
        return stringTolabel[name] ?: let {
            val newLabel = ir.createLabel()
            stringTolabel[name] = newLabel
            newLabel
        }
    }

    private fun visitDeclaration(declaration: Declaration) {
        declaration.specifyType(typeHolder)

        for (declarator in declaration.declarators()) {
            declarator.accept(this)
        }
    }

    private fun makeConditionFromExpression(condition: Expression): Value {
        val conditionExpr = visitExpression(condition, true)
        if (conditionExpr.type() == Type.U1) {
            return conditionExpr
        }

        return when (val type = conditionExpr.type()) {
            is IntegerType, PointerType -> ir.icmp(conditionExpr, IntPredicate.Ne, Constant.of(type.asType(), 0))
            is FloatingPointType -> ir.fcmp(conditionExpr, FloatPredicate.One, Constant.of(type, 0))
            else -> throw IRCodeGenError("Unknown type")
        }
    }

    private inline fun<reified T: AnyPredicateType> makeCondition(a: Value, predicate: T, b: Value): Value = when (a.type()) {
        is IntegerType, PointerType -> ir.icmp(a, predicate as IntPredicate, b)
        is FloatingPointType -> ir.fcmp(a, predicate as FloatPredicate, b)
        else -> throw IRCodeGenError("Unknown type")
    }

    private fun visitExpression(expression: Expression, isRvalue: Boolean): Value = when (expression) {
        is BinaryOp     -> visitBinary(expression)
        is UnaryOp      -> visitUnary(expression, isRvalue)
        is NumNode      -> visitNumNode(expression)
        is VarNode      -> visitVarNode(expression, isRvalue)
        is FunctionCall -> visitFunctionCall(expression)
        is Cast         -> visitCast(expression)
        is ArrayAccess  -> visitArrayAccess(expression, isRvalue)
        is StringNode   -> visitStringNode(expression)
        is SizeOf       -> visitSizeOf(expression)
        is MemberAccess -> visitMemberAccess(expression, isRvalue)
        is ArrowMemberAccess -> visitArrowMemberAccess(expression, isRvalue)
        is FuncPointerCall   -> visitFunPointerCall(expression)
        is Conditional       -> visitConditional(expression)
        is CharNode          -> visitCharNode(expression)
        is SingleInitializer -> visitSingleInitializer(expression)
        else -> throw IRCodeGenError("Unknown expression: $expression")
    }

    private fun visitSingleInitializer(singleInitializer: SingleInitializer): Value {
        val lvalueAdr = initializerContext.peekValue()
        val type = initializerContext.peekType().baseType() as AggregateBaseType
        val idx = initializerContext.peekIndex()
        when (val expr = singleInitializer.expr) {
            is InitializerList -> when (type) {
                is CArrayBaseType -> {
                    val t = type.element()
                    val irType = mb.toIRType<AggregateType>(typeHolder, t.baseType())
                    val fieldPtr = ir.gep(lvalueAdr, irType, Constant.valueOf(Type.I64, idx))
                    initializerContext.scope(fieldPtr, t) { visitInitializerList(expr) }
                }
                is StructBaseType -> {
                    val t = type.fieldIndex(idx)
                    val irType = mb.toIRType<AggregateType>(typeHolder, t.baseType())
                    val fieldPtr = ir.gfp(lvalueAdr, irType, arrayOf(Constant.valueOf(Type.I64, idx)))
                    initializerContext.scope(fieldPtr, t) { visitInitializerList(expr) }
                }
                else -> throw IRCodeGenError("Unknown type")
            }
            else -> {
                val rvalue = visitExpression(expr, true)
                val irType = mb.toIRType<AggregateType>(typeHolder, type)
                val fieldType = irType.field(idx)
                val converted = ir.convertToType(rvalue, fieldType)
                val fieldPtr = ir.gfp(lvalueAdr, irType, arrayOf(Constant.valueOf(Type.I64, idx)))
                ir.store(fieldPtr, converted)
            }
        }
        return Value.UNDEF
    }

    private fun visitCharNode(charNode: CharNode): Value {
        val charType  = charNode.resolveType(typeHolder)
        val charValue = Constant.of(Type.I8, charNode.toInt())
        return ir.convertToType(charValue, mb.toIRType<PrimitiveType>(typeHolder, charType))
    }

    private fun visitConditional(conditional: Conditional): Value {
        val commonType = mb.toIRType<Type>(typeHolder, conditional.resolveType(typeHolder))
        if (commonType == Type.Void) {
            val condition = makeConditionFromExpression(conditional.cond)
            val thenBlock = ir.createLabel()
            val elseBlock = ir.createLabel()
            val exitBlock = ir.createLabel()

            ir.branchCond(condition, thenBlock, elseBlock)
            ir.switchLabel(thenBlock)
            visitExpression(conditional.eTrue, true)
            ir.branch(exitBlock)

            ir.switchLabel(elseBlock)
            visitExpression(conditional.eFalse, true)
            ir.branch(exitBlock)

            ir.switchLabel(exitBlock)
            return Value.UNDEF

        } else {
            //TODO 'if else' pattern????
            val onTrue    = visitExpression(conditional.eTrue, true)
            val onFalse   = visitExpression(conditional.eFalse, true)
            val condition = makeConditionFromExpression(conditional.cond)
            commonType as IntegerType //TODO
            val onTrueConverted = ir.convertToType(onTrue, commonType)
            val onFalseConverted = ir.convertToType(onFalse, commonType)
            return ir.select(condition, commonType, onTrueConverted, onFalseConverted)
        }
    }

    private fun visitArrowMemberAccess(arrowMemberAccess: ArrowMemberAccess, isRvalue: Boolean): Value {
        val struct       = visitExpression(arrowMemberAccess.primary, true)
        val structType   = arrowMemberAccess.primary.resolveType(typeHolder) as CPointerT
        val structIRType = mb.toIRType<StructType>(typeHolder, structType.dereference())

        val baseStructType = structType.dereference() as AnyStructType
        val member = baseStructType.fieldIndex(arrowMemberAccess.ident.str())

        val indexes = arrayOf(Constant.valueOf<IntegerConstant>(Type.I64, member))
        val gep = ir.gfp(struct, structIRType, indexes)
        if (!isRvalue) {
            return gep
        }

        val memberType = baseStructType.fields()[member].second.baseType()
        return if (memberType !is AggregateBaseType) {
            val memberIRType = mb.toIRType<PrimitiveType>(typeHolder, memberType)
            ir.load(memberIRType, gep)
        } else {
            gep
        }
    }

    private fun visitMemberAccess(memberAccess: MemberAccess, isRvalue: Boolean): Value {
        val struct = visitExpression(memberAccess.primary, false) //TODO isRvalue???
        val structType = memberAccess.primary.resolveType(typeHolder)
        if (structType !is AnyStructType) {
            throw IRCodeGenError("Struct type expected, but got $structType")
        }
        val structIRType = mb.toIRType<StructType>(typeHolder, structType)

        val member = structType.fieldIndex(memberAccess.memberName())

        val indexes = arrayOf(Constant.valueOf<IntegerConstant>(Type.I64, member))
        val gep = ir.gfp(struct, structIRType, indexes)
        if (!isRvalue) {
            return gep
        }
        val memberType = structType.fields()[member].second.baseType()
        return if (memberType !is AggregateBaseType) {
            val memberIRType = mb.toIRType<PrimitiveType>(typeHolder, memberType)
            ir.load(memberIRType, gep)
        } else {
            gep
        }
    }

    private fun visitSizeOf(sizeOf: SizeOf): Value = when (val expr = sizeOf.expr) {
        is TypeName -> {
            val resolved = expr.specifyType(typeHolder, listOf())
            val irType = mb.toIRType<NonTrivialType>(typeHolder, resolved.type.baseType())
            Constant.of(Type.I64, irType.sizeOf())
        }
        is Expression -> {
            val resolved = expr.resolveType(typeHolder)
            val irType = mb.toIRType<NonTrivialType>(typeHolder, resolved)
            Constant.of(Type.I64, irType.sizeOf())
        }
        else -> throw IRCodeGenError("Unknown sizeOf expression, expr=${expr}")
    }

    private fun visitStringNode(stringNode: StringNode): Value {
        val string = stringNode.data()
        val stringLiteral = StringLiteralGlobalConstant(createStringLiteralName(), ArrayType(Type.I8, string.length), string)
        return mb.addConstant(stringLiteral)
    }

    private fun visitArrayAccess(arrayAccess: ArrayAccess, isRvalue: Boolean): Value {
        val index = visitExpression(arrayAccess.expr, true)
        val array = visitExpression(arrayAccess.primary, true)

        val arrayType = arrayAccess.resolveType(typeHolder)
        val elementType = mb.toIRType<NonTrivialType>(typeHolder, arrayType)

        val convertedIndex = ir.toIndexType(index)
        val adr = ir.gep(array, elementType, convertedIndex)
        if (!isRvalue) {
            return adr
        }

        return if (arrayType is AggregateBaseType) {
            adr
        } else {
            ir.load(elementType as PrimitiveType, adr)
        }
    }

    private fun convertArg(function: AnyFunctionPrototype, argIdx: Int, expr: Value): Value {
        if (argIdx >= function.arguments().size) {
            if (!function.isVararg) {
                throw IRCodeGenError("Too many arguments in function call '${function.shortName()}'")
            }

            return when (expr.type()) {
                Type.F32 -> ir.convertToType(expr, Type.F64)
                Type.I8, Type.I16 -> ir.convertToType(expr, Type.I32)
                Type.U8, Type.U16 -> ir.convertToType(expr, Type.U32)
                else -> expr
            }
        }

        val cvt = function.arguments()[argIdx]
        return ir.convertToType(expr, cvt)
    }

    private fun convertFunctionArgs(function: AnyFunctionPrototype, args: List<Expression>): List<Value> {
        val convertedArgs = mutableListOf<Value>()
        for ((idx, argValue) in args.withIndex()) {
            val expr = visitExpression(argValue, true)
            when (val argCType = argValue.resolveType(typeHolder)) {
                is CPrimitive, is CPointerT, is CBaseFunctionType, is CUncompletedArrayBaseType -> {
                    val convertedArg = convertArg(function, idx, expr)
                    convertedArgs.add(convertedArg)
                }
                is CArrayBaseType -> {
                    val type = mb.toIRType<ArrayType>(typeHolder, argCType)
                    val convertedArg = ir.gep(expr, type.elementType(), Constant.of(Type.I64, 0))
                    convertedArgs.add(convertedArg)
                }
                is StructBaseType -> {
                    val type = mb.toIRType<StructType>(typeHolder, argCType)
                    convertedArgs.addAll(ir.coerceArguments(type, expr))
                }
                else -> throw IRCodeGenError("Unknown type, type=${argCType} in function call")
            }
        }
        return convertedArgs
    }

    private fun visitCast(cast: Cast): Value {
        val value = visitExpression(cast.cast, true)
        val toType = mb.toIRType<Type>(typeHolder, cast.resolveType(typeHolder))
        if (toType == Type.Void) {
            return value
        }

        assertion(toType is NonTrivialType) { "invariant" }
        toType as NonTrivialType
        return ir.convertToType(value, toType)
    }

    private fun visitFunPointerCall(funcPointerCall: FuncPointerCall): Value {
        val functionType = funcPointerCall.resolveFunctionType(typeHolder)
        val loadedFunctionPtr = visitExpression(funcPointerCall.primary, true) //TODO bug

        val irRetType = mb.toIRType<Type>(typeHolder, functionType.functionType.retType.baseType())
        val argTypes = functionType.functionType.argsTypes.map { mb.toIRType<NonTrivialType>(typeHolder, it.baseType()) }
        val prototype = IndirectFunctionPrototype(irRetType, argTypes, functionType.functionType.variadic)
        val convertedArgs = convertFunctionArgs(prototype, funcPointerCall.args)

        val cont = ir.createLabel()
        val ret = when (functionType.functionType.retType.baseType()) {
            VOID -> {
                ir.ivcall(loadedFunctionPtr, prototype, convertedArgs, cont)
                Value.UNDEF
            }
            is CPrimitive, is CPointerT -> ir.icall(loadedFunctionPtr, prototype, convertedArgs, cont)
            is StructBaseType -> when (prototype.returnType()) {
                is PrimitiveType -> ir.icall(loadedFunctionPtr, prototype, convertedArgs, cont)
                //is TupleType     -> ir.tupleCall(function, convertedArgs, cont)
                is StructType    -> ir.icall(loadedFunctionPtr, prototype, convertedArgs, cont)
                else -> throw IRCodeGenError("Unknown type ${functionType.functionType.retType}")
            }
            else -> throw IRCodeGenError("Unknown type ${functionType.functionType.retType}")
        }
        ir.switchLabel(cont)
        return ret
    }

    private fun visitFunctionCall(functionCall: FunctionCall): Value {
        val functionType = functionCall.resolveType(typeHolder)
        val function = mb.findFunction(functionCall.name()) ?: throw IRCodeGenError("Function '${functionCall.name()}' not found")
        val convertedArgs = convertFunctionArgs(function, functionCall.args)

        val cont = ir.createLabel()
        val ret = when (functionType) {
            VOID -> {
                ir.vcall(function, convertedArgs, cont)
                Value.UNDEF
            }

            is CPrimitive, is CPointerT -> ir.call(function, convertedArgs, cont)
            is StructBaseType -> when (function.returnType()) {
                is PrimitiveType -> ir.call(function, convertedArgs, cont)
                is TupleType     -> ir.tupleCall(function, convertedArgs, cont)
                is StructType    -> ir.call(function, convertedArgs, cont)
                else -> throw IRCodeGenError("Unknown type ${function.returnType()}")
            }

            else -> TODO("$functionType")
        }
        ir.switchLabel(cont)
        return ret
    }

    private fun eq(type: Type): AnyPredicateType = when (type) {
        is IntegerType       -> IntPredicate.Eq
        is FloatingPointType -> FloatPredicate.Oeq
        is PointerType       -> IntPredicate.Eq
        else -> throw IRCodeGenError("Unknown type")
    }

    private fun ne(type: Type): AnyPredicateType = when (type) {
        is IntegerType       -> IntPredicate.Ne
        is FloatingPointType -> FloatPredicate.One
        is PointerType       -> IntPredicate.Ne
        else -> throw IRCodeGenError("Unknown type")
    }

    private fun gt(type: Type): AnyPredicateType = when (type) {
        is IntegerType       -> IntPredicate.Gt
        is FloatingPointType -> FloatPredicate.Ogt
        is PointerType       -> IntPredicate.Gt
        else -> throw IRCodeGenError("Unknown type")
    }

    private fun lt(type: Type): AnyPredicateType = when (type) {
        is IntegerType       -> IntPredicate.Lt
        is FloatingPointType -> FloatPredicate.Olt
        is PointerType       -> IntPredicate.Lt
        else -> throw IRCodeGenError("Unknown type")
    }

    private fun le(type: Type): AnyPredicateType = when (type) {
        is IntegerType       -> IntPredicate.Le
        is FloatingPointType -> FloatPredicate.Ole
        is PointerType       -> IntPredicate.Le
        else -> throw IRCodeGenError("Unknown type")
    }

    private fun ge(type: Type): AnyPredicateType = when (type) {
        is IntegerType       -> IntPredicate.Ge
        is FloatingPointType -> FloatPredicate.Oge
        is PointerType       -> IntPredicate.Ge
        else -> throw IRCodeGenError("Unknown type")
    }

    private fun makeAlgebraicBinary(binop: BinaryOp, op: (a: Value, b: Value) -> LocalValue): Value {
        val commonType = mb.toIRType<NonTrivialType>(typeHolder, binop.resolveType(typeHolder))

        if (commonType is PointerType) {
            val rvalue     = visitExpression(binop.right, true)
            val rValueType = binop.right.resolveType(typeHolder)
            if (rValueType !is CPrimitive) {
                throw IRCodeGenError("Primitive type expected")
            }
            val convertedRValue = ir.convertToType(rvalue, Type.U64)

            val lvalue     = visitExpression(binop.left, true)
            val lValueType = binop.left.resolveType(typeHolder)
            if (lValueType !is CPointerT) {
                throw IRCodeGenError("Pointer type expected")
            }
            val convertedLValue = ir.convertToType(lvalue, Type.U64)

            val size = lValueType.dereference().size()
            val sizeValue = Constant.of(Type.U64, size)
            val mul = ir.mul(convertedRValue, sizeValue)

            val result = op(convertedLValue, mul)
            return ir.convertToType(result, commonType)

        } else {
            val right = visitExpression(binop.right, true)
            val rightConverted = ir.convertToType(right, commonType)

            val left = visitExpression(binop.left, true)
            val leftConverted = ir.convertToType(left, commonType)

            return op(leftConverted, rightConverted)
        }
    }

    private inline fun makeComparisonBinary(binop: BinaryOp, crossinline predicate: (NonTrivialType) -> AnyPredicateType): Value {
        val commonType = mb.toIRType<NonTrivialType>(typeHolder, binop.resolveType(typeHolder))

        val right = visitExpression(binop.right, true)
        val rightConverted = ir.convertToType(right, commonType)

        val left = visitExpression(binop.left, true)
        val leftConverted = ir.convertToType(left, commonType)

        return makeCondition(leftConverted, predicate(commonType), rightConverted)
    }

    private fun visitBinary(binop: BinaryOp): Value {
        return when (binop.opType) {
            BinaryOpType.ADD -> makeAlgebraicBinary(binop, ir::add)
            BinaryOpType.SUB -> makeAlgebraicBinary(binop, ir::sub)
            BinaryOpType.ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)

                if (leftType is AggregateBaseType) {
                    val left = visitExpression(binop.left, true)
                    ir.memcpy(left, right, U64Value(leftType.size().toLong()))

                    right
                } else {
                    val leftIrType = mb.toIRType<NonTrivialType>(typeHolder, leftType)
                    val leftConverted = ir.convertToType(right, leftIrType)

                    val left = visitExpression(binop.left, false)
                    ir.store(left, leftConverted)
                    leftConverted //TODO test it
                }
            }
            BinaryOpType.ADD_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<NonTrivialType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = if (leftType is CPrimitive) {
                    ir.load(leftIrType as PrimitiveType, left)
                } else {
                    throw IRCodeGenError("Primitive type expected")
                }

                val sum = ir.add(loadedLeft, rightConverted)
                ir.store(left, sum)
                sum // TODO unchecked !!!
            }

            BinaryOpType.DIV_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<PrimitiveType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = ir.load(leftIrType, left)

                val div = divide(leftIrType, loadedLeft, rightConverted)
                ir.store(left, div)
                div // TODO unchecked !!!
            }

            BinaryOpType.MUL_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<NonTrivialType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = if (leftType is CPrimitive) {
                    ir.load(leftIrType as PrimitiveType, left)
                } else {
                    throw IRCodeGenError("Primitive type expected")
                }

                val mul = ir.mul(loadedLeft, rightConverted)
                ir.store(left, mul)
                mul // TODO unchecked !!!
            }

            BinaryOpType.BIT_OR -> makeAlgebraicBinary(binop, ir::or)
            BinaryOpType.MUL -> makeAlgebraicBinary(binop, ir::mul)
            BinaryOpType.NE -> makeComparisonBinary(binop, ::ne)
            BinaryOpType.GT -> makeComparisonBinary(binop, ::gt)
            BinaryOpType.LT -> makeComparisonBinary(binop, ::lt)
            BinaryOpType.LE -> makeComparisonBinary(binop, ::le)
            BinaryOpType.AND -> {
                val initialBB = ir.currentLabel()

                val left = visitExpression(binop.left, true)
                val convertedLeft = ir.convertToType(left, Type.U1)

                val bb = ir.createLabel()
                val end = ir.createLabel()
                ir.branchCond(convertedLeft, bb, end)
                ir.switchLabel(bb)

                val right = visitExpression(binop.right, true)
                val convertedRight = ir.convertToType(right, Type.U8)
                assertion(right.type() == Type.U1) { "expects"}

                val current = ir.currentLabel()
                ir.branch(end)
                ir.switchLabel(end)
                ir.phi(listOf(U8Value(0), convertedRight), listOf(initialBB, current))
            }
            BinaryOpType.OR -> {
                val initialBB = ir.currentLabel()

                val left = visitExpression(binop.left, true)
                val convertedLeft = ir.convertToType(left, Type.U1)

                val bb = ir.createLabel()
                val end = ir.createLabel()
                ir.branchCond(convertedLeft, end, bb)
                ir.switchLabel(bb)

                val right = visitExpression(binop.right, true)
                val convertedRight = ir.convertToType(right, Type.U8)
                assertion(right.type() == Type.U1) { "expects"}

                val current = ir.currentLabel()
                ir.branch(end)
                ir.switchLabel(end)
                ir.phi(listOf(U8Value(1), convertedRight), listOf(initialBB, current))
            }
            BinaryOpType.SHR_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<IntegerType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = ir.load(leftIrType, left)

                val shr = ir.shr(loadedLeft, rightConverted)
                ir.store(left, shr)
                shr
            }
            BinaryOpType.SHL_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<IntegerType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = ir.load(leftIrType, left)

                val shl = ir.shl(loadedLeft, rightConverted)
                ir.store(left, shl)
                shl
            }
            BinaryOpType.BIT_XOR_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<IntegerType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = ir.load(leftIrType, left)

                val xor = ir.xor(loadedLeft, rightConverted)
                ir.store(left, xor)
                xor
            }
            BinaryOpType.BIT_OR_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<IntegerType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = ir.load(leftIrType, left)

                val or = ir.or(loadedLeft, rightConverted)
                ir.store(left, or)
                or
            }
            BinaryOpType.GE  -> makeComparisonBinary(binop, ::ge)
            BinaryOpType.EQ  -> makeComparisonBinary(binop, ::eq)
            BinaryOpType.SHL -> makeAlgebraicBinary(binop, ir::shl)
            BinaryOpType.SHR -> makeAlgebraicBinary(binop, ir::shr)
            BinaryOpType.BIT_AND -> makeAlgebraicBinary(binop, ir::and)
            BinaryOpType.BIT_XOR -> makeAlgebraicBinary(binop, ir::xor)
            BinaryOpType.MOD -> {
                val commonType = mb.toIRType<NonTrivialType>(typeHolder, binop.resolveType(typeHolder))

                val left = visitExpression(binop.left, true)
                val leftConverted = ir.convertToType(left, commonType)

                val right = visitExpression(binop.right, true)
                val rightConverted = ir.convertToType(right, commonType)

                val rem = ir.tupleDiv(leftConverted, rightConverted)
                ir.proj(rem, 1)
            }
            BinaryOpType.DIV -> {
                val commonType = mb.toIRType<PrimitiveType>(typeHolder, binop.resolveType(typeHolder))

                val right = visitExpression(binop.right, true)
                val rightConverted = ir.convertToType(right, commonType)

                val left = visitExpression(binop.left, true)
                val leftConverted = ir.convertToType(left, commonType)

                divide(commonType, leftConverted, rightConverted)
            }

            BinaryOpType.SUB_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<PrimitiveType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = ir.load(leftIrType, left)
                val sub = ir.sub(loadedLeft, rightConverted)
                ir.store(left, sub)
                sub
            }
            BinaryOpType.MOD_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<PrimitiveType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = ir.load(leftIrType, left)

                val rem = ir.tupleDiv(loadedLeft, rightConverted)
                val mod = ir.proj(rem, 1)
                ir.store(left, mod)
                mod
            }
            BinaryOpType.BIT_AND_ASSIGN -> {
                val right = visitExpression(binop.right, true)
                val leftType = binop.left.resolveType(typeHolder)
                val leftIrType = mb.toIRType<IntegerType>(typeHolder, leftType)
                val rightConverted = ir.convertToType(right, leftIrType)

                val left = visitExpression(binop.left, false)
                val loadedLeft = ir.load(leftIrType, left)

                val and = ir.and(loadedLeft, rightConverted)
                ir.store(left, and)
                and
            }
            BinaryOpType.COMMA -> {
                visitExpression(binop.left, false)
                visitExpression(binop.right, false)
            }
        }
    }

    private fun divide(type: PrimitiveType, a: Value, b: Value): Value {
        if (type is IntegerType) {
            val tupleDiv = ir.tupleDiv(a, b)
            return ir.proj(tupleDiv, 0)
        } else {
            assertion(type is FloatingPointType) { "Floating point type expected, but got $type" }
            return ir.div(a, b)
        }
    }

    private fun visitIncOrDec(unaryOp: UnaryOp, op: (a: Value, b: Value) -> LocalValue): Value {
        assertion(unaryOp.opType == PostfixUnaryOpType.INC || unaryOp.opType == PostfixUnaryOpType.DEC) {
            "Unknown operation, op=${unaryOp.opType}"
        }

        val ctype = unaryOp.resolveType(typeHolder)

        val addr = visitExpression(unaryOp.primary, false)
        val type = mb.toIRType<PrimitiveType>(typeHolder, ctype)
        val loaded = ir.load(type, addr)
        if (ctype is CPointerT) {
            val converted = ir.convertToType(loaded, Type.I64)
            val inc = op(converted, Constant.of(Type.I64, ctype.dereference().size()))
            ir.store(addr, ir.convertToType(inc, type))
        } else {
            val inc = op(loaded, Constant.of(loaded.type(), 1))
            ir.store(addr, ir.convertToType(inc, type))
        }
        return loaded
    }

    private fun visitPrefixIncOrDec(unaryOp: UnaryOp, op: (a: Value, b: Value) -> LocalValue): Value {
        assertion(unaryOp.opType == PrefixUnaryOpType.INC || unaryOp.opType == PrefixUnaryOpType.DEC) {
            "Unknown operation, op=${unaryOp.opType}"
        }

        val ctype = unaryOp.resolveType(typeHolder)

        val addr = visitExpression(unaryOp.primary, false)
        val type = mb.toIRType<PrimitiveType>(typeHolder, ctype)
        val loaded = ir.load(type, addr)
        if (ctype is CPointerT) {
            val converted = ir.convertToType(loaded, Type.I64)
            val inc = op(converted, Constant.of(Type.I64, ctype.dereference().size()))
            ir.store(addr, ir.convertToType(inc, type))
            return inc
        } else {
            val inc = op(loaded, Constant.of(loaded.type(), 1))
            ir.store(addr, ir.convertToType(inc, type))
            return inc
        }
    }

    private fun visitUnary(unaryOp: UnaryOp, isRvalue: Boolean): Value {
        return when (unaryOp.opType) {
            PrefixUnaryOpType.ADDRESS -> visitExpression(unaryOp.primary, false)

            PrefixUnaryOpType.DEREF -> {
                val addr = visitExpression(unaryOp.primary, true)
                if (!isRvalue) {
                    return addr
                }
                val type = unaryOp.resolveType(typeHolder)
                val loadedType = mb.toIRType<PrimitiveType>(typeHolder, type)
                ir.load(loadedType, addr)
            }
            PostfixUnaryOpType.INC -> visitIncOrDec(unaryOp, ir::add)
            PostfixUnaryOpType.DEC -> visitIncOrDec(unaryOp, ir::sub)
            PrefixUnaryOpType.INC  -> visitPrefixIncOrDec(unaryOp, ir::add)
            PrefixUnaryOpType.DEC  -> visitPrefixIncOrDec(unaryOp, ir::sub)
            PrefixUnaryOpType.NEG  -> {
                val value = visitExpression(unaryOp.primary, true)
                val type = unaryOp.resolveType(typeHolder)
                val valueType = mb.toIRType<NonTrivialType>(typeHolder, type)
                val converted = ir.convertToType(value, valueType)
                ir.neg(converted)
            }
            PrefixUnaryOpType.NOT -> {
                val value = visitExpression(unaryOp.primary, true)
                val type = unaryOp.resolveType(typeHolder)
                val commonType = mb.toIRType<NonTrivialType>(typeHolder, type)
                val converted = ir.convertToType(value, commonType)
                makeCondition(converted, eq(commonType), Constant.of(converted.asType(), 0))
            }
            PrefixUnaryOpType.BIT_NOT -> {
                val value = visitExpression(unaryOp.primary, true)
                val type = unaryOp.resolveType(typeHolder)
                val commonType = mb.toIRType<NonTrivialType>(typeHolder, type)
                val converted = ir.convertToType(value, commonType)
                ir.not(converted)
            }

            PrefixUnaryOpType.PLUS -> visitExpression(unaryOp.primary, isRvalue)
        }
    }

    private fun visitNumNode(numNode: NumNode): Constant = when (val num = numNode.number.toNumberOrNull()) {
        is Byte   -> Constant.of(Type.I8, num as Number)
        is UByte  -> Constant.of(Type.U8, num.toLong())
        is Int    -> Constant.of(Type.I32, num as Number)
        is UInt   -> Constant.of(Type.U32, num.toLong())
        is Long   -> Constant.of(Type.I64, num as Number)
        is ULong  -> Constant.of(Type.U64, num.toLong())
        is Float  -> Constant.of(Type.F32, num as Number)
        is Double -> Constant.of(Type.F64, num)
        else -> throw IRCodeGenError("Unknown number type, num=${numNode.number.str()}")
    }

    private fun getVariableAddress(varNode: VarNode, rvalueAddr: Value, isRvalue: Boolean): Value {
        val type = varNode.resolveType(typeHolder)

        if (type is AggregateBaseType) {
            return rvalueAddr
        }
        if (!isRvalue) {
            return rvalueAddr
        }
        if (type is CFunPointerT) {
            return rvalueAddr //tODO hack??!
        }
        val converted = mb.toIRType<PrimitiveType>(typeHolder, type)
        return ir.load(converted, rvalueAddr)
    }

    private fun visitVarNode(varNode: VarNode, isRvalue: Boolean): Value {
        val name = varNode.name()
        val rvalueAttr = varStack[name]
        if (rvalueAttr != null) {
            return getVariableAddress(varNode, rvalueAttr, isRvalue)
        }
        val global = mb.findFunction(name)
        if (global != null) {
            return global
        }
        throw IRCodeGenError("Variable '$name' not found")
    }

    private fun argumentTypes(ctypes: List<TypeDesc>): List<NonTrivialType> {
        val types = arrayListOf<NonTrivialType>()
        for (type in ctypes) {
            when (type.baseType()) {
                is StructBaseType -> {
                    val irType = mb.toIRType<StructType>(typeHolder, type.baseType())
                    val parameters = CallConvention.coerceArgumentTypes(irType)
                    if (parameters != null) {
                        types.addAll(parameters)
                    } else {
                        types.add(Type.Ptr)
                    }
                }
                is CArrayBaseType, is CUncompletedArrayBaseType -> {
                    types.add(Type.Ptr)
                }
                is CFunPointerT -> {
                    types.add(Type.Ptr)
                }
                is CPrimitive -> {
                    types.add(mb.toIRType<PrimitiveType>(typeHolder, type.baseType()))
                }
                else -> throw IRCodeGenError("Unknown type, type=$type")
            }
        }
        return types
    }

    private fun visitParameters(parameters: List<String>,
                                cTypes: List<TypeDesc>,
                                arguments: List<ArgumentValue>,
                                closure: (String, BaseType, List<ArgumentValue>) -> Unit) {
        var currentArg = 0
        while (currentArg < arguments.size) {
            when (val cType = cTypes[currentArg].baseType()) {
                is CPrimitive -> {
                    closure(parameters[currentArg], cType, listOf(arguments[currentArg]))
                    currentArg++
                }
                is StructBaseType -> {
                    val types = CallConvention.coerceArgumentTypes(mb.toIRType<StructType>(typeHolder, cType)) ?: listOf(Type.Ptr)
                    val args = mutableListOf<ArgumentValue>()
                    for (i in types.indices) {
                        args.add(arguments[currentArg + i])
                    }
                    closure(parameters[currentArg], cType, args)
                    currentArg += types.size
                }
                is AnyCArrayType -> {
                    closure(parameters[currentArg], cType, listOf(arguments[currentArg]))
                    currentArg++
                }
                else -> throw IRCodeGenError("Unknown type, type=$cType")
            }
        }
    }

    private fun visitParameter(param: String, cType: BaseType, args: List<ArgumentValue>) = when (cType) {
        is CFunPointerT, is AnyCArrayType -> {
            assertion(args.size == 1) { "invariant" }
            varStack[param] = args[0]
        }
        is CPrimitive -> {
            assertion(args.size == 1) { "invariant" }

            val irType    = mb.toIRType<NonTrivialType>(typeHolder, cType)
            val rvalueAdr = ir.alloc(irType)
            ir.store(rvalueAdr, ir.convertToType(args[0], irType))
            varStack[param] = rvalueAdr
        }
        is StructBaseType -> {
            if (cType.size() <= QWORD_SIZE * 2) {
                val irType    = mb.toIRType<NonTrivialType>(typeHolder, cType)
                val rvalueAdr = ir.alloc(irType)
                args.forEachIndexed { idx, arg ->
                    val offset   = (idx * QWORD_SIZE) / arg.type().sizeOf()
                    val fieldPtr = ir.gep(rvalueAdr, arg.type(), Constant.valueOf(Type.I64, offset))
                    ir.store(fieldPtr, arg)
                }
                varStack[param] = rvalueAdr
            } else {
                assertion(args.size == 1) { "invariant" }
                varStack[param] = args[0]
            }
        }
        else -> throw IRCodeGenError("Unknown type, type=$cType")
    }

    private fun emitReturnType(retCType: TypeDesc) {
        exitBlock = ir.createLabel()
        if (retCType.baseType() == VOID) {
            ir.switchLabel(exitBlock)
            ir.retVoid()
            return
        }
        val retType = mb.toIRType<NonTrivialType>(typeHolder, retCType.baseType())
        returnValueAdr = ir.alloc(retType)
        ir.switchLabel(exitBlock)
        emitReturn(retType, returnValueAdr!!)
    }

    private fun emitReturn(retType: Type, value: Value) {
        when (retType) {
            is PrimitiveType -> {
                val ret = ir.load(retType, value)
                ir.ret(retType, arrayOf(ret))
            }
            is StructType -> {
                val retValues = ir.coerceArguments(retType, value)
                val retTupleType = CallConvention.coerceArgumentTypes(retType)
                if (retTupleType != null) {
                    if (retTupleType.size == 1) {
                        ir.ret(retTupleType[0], retValues.toTypedArray())
                    } else {
                        ir.ret(TupleType(retTupleType.toTypedArray()), retValues.toTypedArray())
                    }
                } else {
                    ir.ret(Type.Ptr, retValues.toTypedArray())
                }
            }
            else -> throw IRCodeGenError("Unknown return type, type=$retType")
        }
    }

    private fun irReturnType(retType: TypeDesc): Type = when (retType.baseType()) {
        is CPrimitive -> mb.toIRType<Type>(typeHolder, retType.baseType())
        is StructBaseType -> {
            val structType = mb.toIRType<StructType>(typeHolder, retType.baseType())
            val list = CallConvention.coerceArgumentTypes(structType) ?: return Type.Void
            if (list.size == 1) {
                list[0]
            } else {
                TupleType(list.toTypedArray())
            }
        }
        else -> throw IRCodeGenError("Unknown return type, type=$retType")
    }

    override fun visit(functionNode: FunctionNode): Value = scoped {
        val parameters = functionNode.functionDeclarator().params()
        val fnType     = functionNode.declareType(functionNode.specifier, typeHolder).type.asType<CBaseFunctionType>()
        val retType    = fnType.retType()
        val irRetType  = irReturnType(retType)

        val argTypes = argumentTypes(fnType.args())
        currentFunction = mb.createFunction(functionNode.name(), irRetType, argTypes)

        visitParameters(parameters, fnType.args(), ir.arguments()) { param, cType, args ->
            visitParameter(param, cType, args)
        }

        emitReturnType(retType)

        ir.switchLabel(Label.entry)
        visitStatement(functionNode.body)

        if (ir.last() !is TerminateInstruction) {
            ir.branch(exitBlock)
        }
        return@scoped ir.prototype()
    }

    private fun visitStatement(statement: Statement) = scoped {
        statement.accept(this)
    }

    override fun visit(emptyStatement: EmptyStatement) {}

    override fun visit(exprStatement: ExprStatement) {
        if (ir.last() is TerminateInstruction) {
            return
        }
        visitExpression(exprStatement.expr, true)
    }

    override fun visit(labeledStatement: LabeledStatement) {
        if (ir.last() is TerminateInstruction && labeledStatement.gotos().isEmpty()) {
            return
        }
        val label = seekOrAddLabel(labeledStatement.name())
        if (ir.last() !is TerminateInstruction) {
            ir.branch(label)
        }
        ir.switchLabel(label)
        visitStatement(labeledStatement.stmt)
    }

    override fun visit(gotoStatement: GotoStatement) {
        if (ir.last() is TerminateInstruction) {
            return
        }
        if (gotoStatement.label() == null) {
            throw IRCodeGenError("Goto statement outside of labeled statement")
        }

        val label = seekOrAddLabel(gotoStatement.id.str())
        ir.branch(label)
    }

    override fun visit(continueStatement: ContinueStatement) {
        if (ir.last() is TerminateInstruction) {
            return
        }

        val loopInfo = stmtStack.topLoop() ?: throw IRCodeGenError("Continue statement outside of loop")
        ir.branch(loopInfo.resolveCondition(ir))
    }

    override fun visit(breakStatement: BreakStatement) {
        if (ir.last() is TerminateInstruction) {
            return
        }

        val loopInfo = stmtStack.topSwitchOrLoop() ?: throw IRCodeGenError("Break statement outside of loop or switch")
        ir.branch(loopInfo.resolveExit(ir))
    }

    override fun visit(defaultStatement: DefaultStatement) = scoped {
        val switchInfo = stmtStack.top() as SwitchStmtInfo
        ir.switchLabel(switchInfo.default)
        visitStatement(defaultStatement.stmt)
    }

    override fun visit(caseStatement: CaseStatement) = scoped {
        val switchInfo = stmtStack.top() as SwitchStmtInfo

        val ctx = CommonConstEvalContext<Int>(typeHolder)
        val constant = ConstEvalExpression.eval(caseStatement.constExpression, ConstEvalExpressionInt(ctx))

        val caseValueConverted = I32Value(constant)
        val caseBlock = ir.createLabel()
        if (switchInfo.table.isNotEmpty() && ir.last() !is TerminateInstruction) {
            // fall through
            ir.branch(caseBlock)
        }

        switchInfo.table.add(caseBlock)
        switchInfo.values.add(caseValueConverted)

        ir.switchLabel(caseBlock)
        visitStatement(caseStatement.stmt)
    }

    override fun visit(returnStatement: ReturnStatement) {
        if (ir.last() is TerminateInstruction) {
            return
        }
        val expr = returnStatement.expr
        if (expr is EmptyExpression) {
            ir.branch(exitBlock)
            return
        }
        val value = visitExpression(expr, true)
        val realType = ir.prototype().returnType()
        when (val type = returnStatement.expr.resolveType(typeHolder)) {
            is CPrimitive, is CPointerT -> {
                realType as PrimitiveType
                val returnType = ir.convertToType(value, realType)
                ir.store(returnValueAdr!!, returnType)
            }
            is StructBaseType -> {
                ir.memcpy(returnValueAdr!!, value, U64Value(type.size().toLong()))
            }
            else -> throw IRCodeGenError("Unknown return type, type=${returnStatement.expr.resolveType(typeHolder)}")
        }

        ir.branch(exitBlock)
    }

    override fun visit(compoundStatement: CompoundStatement) = scoped {
        if (ir.last() is TerminateInstruction) {
            return@scoped
        }
        for (node in compoundStatement.statements) {
            when (node) {
                is Declaration -> visitDeclaration(node)
                is Statement   -> visitStatement(node)
                else -> throw IRCodeGenError("Statement expected")
            }
        }
    }

    override fun visit(ifStatement: IfStatement) = scoped {
        if (ir.last() is TerminateInstruction) {
            return@scoped
        }
        val condition = makeConditionFromExpression(ifStatement.condition)
        val thenBlock = ir.createLabel()

        if (ifStatement.elseNode is EmptyStatement) {
            val endBlock = ir.createLabel()
            ir.branchCond(condition, thenBlock, endBlock)
            ir.switchLabel(thenBlock)
            visitStatement(ifStatement.then)
            if (ir.last() !is TerminateInstruction) {
                ir.branch(endBlock)
            }
            ir.switchLabel(endBlock)
        } else {

            val elseBlock = ir.createLabel()
            ir.branchCond(condition, thenBlock, elseBlock)
            // then
            ir.switchLabel(thenBlock)
            visitStatement(ifStatement.then)
            val endBlock = if (ir.last() !is TerminateInstruction) {
                val endBlock = ir.createLabel()
                ir.branch(endBlock)
                endBlock
            } else {
                null
            }

            // else
            ir.switchLabel(elseBlock)
            visitStatement(ifStatement.elseNode)

            if (ir.last() !is TerminateInstruction) {
                val newEndBlock = endBlock ?: ir.createLabel()
                ir.branch(newEndBlock)
                ir.switchLabel(newEndBlock)
            } else if (endBlock != null) {
                ir.switchLabel(endBlock)
            }
        }
    }

    override fun visit(doWhileStatement: DoWhileStatement) = scoped {
        if (ir.last() is TerminateInstruction) {
            return@scoped
        }
        val bodyBlock = ir.createLabel()
        stmtStack.scoped(LoopStmtInfo()) { loopStmt ->
            ir.branch(bodyBlock)
            ir.switchLabel(bodyBlock)

            visitStatement(doWhileStatement.body)

            if (ir.last() !is TerminateInstruction) {
                val conditionBlock = loopStmt.resolveCondition(ir)
                ir.branch(conditionBlock)
                ir.switchLabel(conditionBlock)
                val condition = makeConditionFromExpression(doWhileStatement.condition)
                val endBlock = loopStmt.resolveExit(ir)
                ir.branchCond(condition, bodyBlock, endBlock)
                ir.switchLabel(endBlock)
            }
            if (loopStmt.exit() != null) {
                val exitBlock = loopStmt.resolveExit(ir)
                ir.switchLabel(exitBlock)
            }
        }
    }

    override fun visit(whileStatement: WhileStatement) = scoped {
        if (ir.last() is TerminateInstruction) {
            return@scoped
        }
        val bodyBlock = ir.createLabel()
        stmtStack.scoped(LoopStmtInfo()) { loopStmtInfo ->
            val conditionBlock = loopStmtInfo.resolveCondition(ir)
            ir.branch(conditionBlock)
            ir.switchLabel(conditionBlock)
            val condition = makeConditionFromExpression(whileStatement.condition)

            val endBlock = loopStmtInfo.resolveExit(ir)
            ir.branchCond(condition, bodyBlock, endBlock)
            ir.switchLabel(bodyBlock)
            visitStatement(whileStatement.body)
            if (ir.last() !is TerminateInstruction) {
                ir.branch(conditionBlock)
            }
            ir.switchLabel(endBlock)
        }
    }

    private fun visitInit(init: Node) = when (init) {
        is Declaration    -> visitDeclaration(init)
        is ExprStatement  -> visit(init)
        is EmptyStatement -> {}
        else -> throw IRCodeGenError("Unknown init statement, init=$init")
    }

    private fun visitUpdate(update: Expression) {
        if (update is EmptyExpression) {
            return
        }

        visitExpression(update, true)
    }

    override fun visit(forStatement: ForStatement) = scoped {
        if (ir.last() is TerminateInstruction) {
            return@scoped
        }
        val bodyBlock = ir.createLabel()
        stmtStack.scoped(LoopStmtInfo()) { loopStmtInfo ->
            visitInit(forStatement.init)

            val conditionBlock = ir.createLabel()
            ir.branch(conditionBlock)
            ir.switchLabel(conditionBlock)
            val condition = makeConditionFromExpression(forStatement.condition)
            val endBlock = loopStmtInfo.resolveExit(ir)
            ir.branchCond(condition, bodyBlock, endBlock)
            ir.switchLabel(bodyBlock)
            visitStatement(forStatement.body)
            if (ir.last() !is TerminateInstruction) {
                val updateBlock = ir.createLabel()
                ir.branch(updateBlock)
                ir.switchLabel(updateBlock)
                visitUpdate(forStatement.update)
                if (ir.last() !is TerminateInstruction) {
                    ir.branch(conditionBlock)
                }
            }
            ir.switchLabel(endBlock)
        }
    }

    override fun visit(switchStatement: SwitchStatement) = scoped {
        if (ir.last() is TerminateInstruction) {
            return@scoped
        }
        val condition = visitExpression(switchStatement.condition, true)
        val conditionBlock = ir.currentLabel()

        val defaultBlock = ir.createLabel()
        stmtStack.scoped(SwitchStmtInfo(defaultBlock, arrayListOf(), arrayListOf())) { info ->
            visitStatement(switchStatement.body)
            if (info.exit() != null) {
                val endBlock = info.resolveExit(ir)
                if (ir.last() !is TerminateInstruction) {
                    ir.branch(endBlock)
                }
            }

            ir.switchLabel(conditionBlock)
            ir.switch(condition, defaultBlock, info.values, info.table)

            if (info.exit() != null) {
                val endBlock = info.resolveExit(ir)
                ir.switchLabel(endBlock)
            }
        }
    }

    override fun visit(declarator: Declarator): Value {
        val type    = typeHolder[declarator.name()]
        val varName = declarator.name()

        val irType = mb.toIRType<NonTrivialType>(typeHolder, type.type.baseType())
        if (type.storageClass == StorageClass.STATIC) {
            val constant = Constant.zero(irType)
            val global = mb.addGlobal(varName, irType, constant, GlobalValueAttribute.INTERNAL)
            varStack[varName] = global
            return global
        }
        val rvalueAdr     = ir.alloc(irType)
        varStack[varName] = rvalueAdr
        return rvalueAdr
    }

    private fun visitInitializerList(initializerList: InitializerList) {
        for ((idx, init) in initializerList.initializers.withIndex()) {
            when (init) {
                is SingleInitializer -> initializerContext.withIndex(idx) { visitSingleInitializer(init) }
                is DesignationInitializer -> initializerContext.withIndex(idx) { visitDesignationInitializer(init) }
            }
        }
    }

    private fun visitDesignationInitializer(designationInitializer: DesignationInitializer) {
        val type = initializerContext.peekType().baseType()
        val value = initializerContext.peekValue()
        for (designator in designationInitializer.designation.designators) {
            when (designator) {
                is ArrayDesignator -> {
                    val arrayType = type as CArrayBaseType
                    val elementType = arrayType.element()
                    val converted = mb.toIRType<NonTrivialType>(typeHolder, elementType.baseType())
                    val expression = visitExpression(designationInitializer.initializer, true)
                    val index = designator.constEval(typeHolder)
                    val convertedRvalue = ir.convertToType(expression, converted)
                    val elementAdr = ir.gep(value, converted, Constant.valueOf(Type.I64, index))
                    ir.store(elementAdr, convertedRvalue)
                }
                is MemberDesignator -> {
                    type as StructBaseType
                    val fieldType = mb.toIRType<StructType>(typeHolder, type)
                    val expression = visitExpression(designationInitializer.initializer, true)
                    val index = type.fieldIndex(designator.name())
                    val converted = ir.convertToType(expression, fieldType.field(index))
                    val fieldAdr = ir.gfp(value, fieldType, arrayOf(Constant.valueOf(Type.I64, index)))
                    ir.store(fieldAdr, converted)
                }
            }
        }
    }

    override fun visit(initDeclarator: InitDeclarator): Value {
        val varDesc = typeHolder[initDeclarator.name()]
        if (varDesc.storageClass == StorageClass.STATIC) {
            val irType = mb.toIRType<NonTrivialType>(typeHolder, varDesc.type.baseType())
            val constant = constEvalExpression(irType, initDeclarator.rvalue) ?: throw IRCodeGenError("Unknown constant")
            val varName = initDeclarator.name()
            val global = mb.addGlobal(varName, irType, constant, GlobalValueAttribute.INTERNAL)
            varStack[varName] = global
            return global
        }
        val type = varDesc.type.baseType()
        if (type !is AggregateBaseType) {
            val rvalue = visitExpression(initDeclarator.rvalue, true)
            val commonType = mb.toIRType<NonTrivialType>(typeHolder, type)
            val convertedRvalue = ir.convertToType(rvalue, commonType)

            val lvalueAdr = visit(initDeclarator.declarator)
            ir.store(lvalueAdr, convertedRvalue)
            return convertedRvalue
        }
        val lvalueAdr = initDeclarator.declarator.accept(this)
        when (val rvalue = initDeclarator.rvalue) {
            is InitializerList -> {
                initializerContext.scope(lvalueAdr, initDeclarator.cType().type) { visitInitializerList(rvalue) }
                return lvalueAdr
            }
            is FunctionCall -> {
                val call = visitExpression(rvalue, true)
                when (val rType = rvalue.resolveType(typeHolder)) {
                    is StructBaseType -> {
                        val structType = mb.toIRType<StructType>(typeHolder, rType)
                        val list = CallConvention.coerceArgumentTypes(structType)
                        if (list == null) {
                            ir.memcpy(lvalueAdr, call, U64Value(rType.size().toLong()))
                            return lvalueAdr
                        }

                        if (list.size == 1) {
                            val gep = ir.gep(lvalueAdr, structType, Constant.of(Type.I64, 0))
                            ir.store(gep, call)
                        } else {
                            list.forEachIndexed { idx, arg ->
                                val offset   = (idx * QWORD_SIZE) / arg.sizeOf()
                                val fieldPtr = ir.gep(lvalueAdr, arg, Constant.valueOf(Type.I64, offset))
                                val proj = ir.proj(call, idx)
                                ir.store(fieldPtr, proj)
                            }
                        }

                        return lvalueAdr
                    }
                    else -> throw IRCodeGenError("Unknown type, type=$rType")
                }
            }
            else -> {
                val rvalue = visitExpression(initDeclarator.rvalue, true)
                val commonType = mb.toIRType<NonTrivialType>(typeHolder, type)
                ir.memcpy(lvalueAdr, rvalue, U64Value(commonType.sizeOf().toLong()))
                return lvalueAdr
            }
        }
    }

    override fun visit(arrayDeclarator: ArrayDeclarator): Value {
        TODO("Not yet implemented")
    }

    override fun visit(emptyDeclarator: EmptyDeclarator): Value {
        return Value.UNDEF
    }

    override fun visit(structDeclarator: StructDeclarator): Value {
        TODO("Not yet implemented")
    }

    override fun visit(directDeclarator: DirectDeclarator): Value {
        TODO("Not yet implemented")
    }
}