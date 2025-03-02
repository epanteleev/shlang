package parser.nodes

import types.*
import common.assertion
import parser.nodes.visitors.*
import tokenizer.Position
import typedesc.TypeDesc
import typedesc.TypeHolder
import typedesc.TypeResolutionException
import typedesc.VarDescriptor


sealed class AnyDeclarator {
    private var cachedType: VarDescriptor? = null

    abstract fun begin(): Position
    abstract fun name(): String
    abstract fun<T> accept(visitor: DeclaratorVisitor<T>): T
    internal abstract fun declareType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): VarDescriptor

    fun varDescriptor(): VarDescriptor {
        if (cachedType == null) {
            throw IllegalStateException("type is not resolved")
        }

        return cachedType!!
    }

    protected fun memoizeType(type: () -> VarDescriptor): VarDescriptor {
        if (cachedType == null) {
            cachedType = type()
        }

        return cachedType!!
    }
}

data class Declarator(val directDeclarator: DirectDeclarator, val pointers: List<NodePointer>): AnyDeclarator() {
    override fun begin(): Position = directDeclarator.begin()
    override fun<T> accept(visitor: DeclaratorVisitor<T>) = visitor.visit(this)

    override fun name(): String {
        return directDeclarator.name()
    }

    override fun declareType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): VarDescriptor = memoizeType {
        val declspecType = declspec.specifyType(typeHolder, pointers)
        val type = directDeclarator.resolveType(declspecType.typeDesc, typeHolder)
        if (declspec.isTypedef) {
            assertion(declspecType.storageClass == null) { "typedef with storage class is not supported" }

            typeHolder.addTypedef(name(), type)
            return@memoizeType VarDescriptor(type, declspecType.storageClass)
        }

        val varDesc = VarDescriptor(type, declspecType.storageClass)
        val baseType = type.cType()
        if (baseType is CFunctionType) {
            // declare extern function or function without body
            typeHolder.addFunctionType(name(), varDesc)
        } else {
            typeHolder.addVar(name(), varDesc)
        }
        return@memoizeType varDesc
    }
}

data class InitDeclarator(val declarator: Declarator, val rvalue: Expression): AnyDeclarator() {
    override fun begin(): Position = declarator.begin()
    override fun<T> accept(visitor: DeclaratorVisitor<T>) = visitor.visit(this)

    override fun name(): String {
        return declarator.name()
    }

    override fun declareType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): VarDescriptor = memoizeType {
        val declspecType = declspec.specifyType(typeHolder, declarator.pointers)

        val type = declarator.directDeclarator.resolveType(declspecType.typeDesc, typeHolder)
        assertion (!declspec.isTypedef) { "typedef is not supported here" }

        val baseType = type.cType()
        if (baseType !is CUncompletedArrayType) {
            return@memoizeType typeHolder.addVar(name(), VarDescriptor(type, declspecType.storageClass))
        }

        when (rvalue) {
            is InitializerList -> {
                // Special case for array initialization without exact size like:
                // int a[] = {1, 2};
                // 'a' is array of 2 elements, not pointer to int

                when (val initializerType = rvalue.resolveType(typeHolder)) {
                    is InitializerType -> {
                        val rvalueType = TypeDesc.from(CArrayType(baseType.element(), rvalue.length().toLong()), listOf())
                        return@memoizeType typeHolder.addVar(name(), VarDescriptor(rvalueType, declspecType.storageClass))
                    }
                    is CStringLiteral -> {
                        val rvalueType = TypeDesc.from(CArrayType(baseType.element(), initializerType.dimension + 1), listOf())
                        return@memoizeType typeHolder.addVar(name(), VarDescriptor(rvalueType, declspecType.storageClass))
                    }
                    else -> throw TypeResolutionException("Array size is not specified: type=$initializerType", declarator.begin())
                }
            }
            is StringNode -> {
                // Special case for string initialization like:
                // char a[] = "hello";
                return@memoizeType typeHolder.addVar(name(), VarDescriptor(TypeDesc.from(rvalue.resolveType(typeHolder)), declspecType.storageClass))
            }
            else -> throw TypeResolutionException("Array size is not specified", declarator.begin())
        }
    }
}

class EmptyDeclarator(private val where: Position) : AnyDeclarator() {
    override fun begin(): Position = where
    override fun name(): String = ""

    override fun<T> accept(visitor: DeclaratorVisitor<T>) = visitor.visit(this)

    override fun declareType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): VarDescriptor {
        throw TypeResolutionException("Empty declarator is not supported", begin())
    }
}