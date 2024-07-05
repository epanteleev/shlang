package parser.nodes

import common.assertion
import types.*
import parser.nodes.visitors.*


abstract class AnyDeclarator: Node() {
    abstract fun name(): String
    abstract fun<T> accept(visitor: DeclaratorVisitor<T>): T
    abstract fun resolveType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): CType //TODO rename to 'declare'
}

data class Declarator(val directDeclarator: DirectDeclarator, val pointers: List<NodePointer>): AnyDeclarator() {
    override fun<T> accept(visitor: DeclaratorVisitor<T>) = visitor.visit(this)

    override fun name(): String {
        return directDeclarator.name()
    }

    override fun resolveType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): CType {
        var pointerType = declspec.specifyType(typeHolder)
        for (pointer in pointers) {
            pointerType = CPointerType(pointerType)
        }

        pointerType = directDeclarator.resolveType(pointerType, typeHolder)

        if (declspec.isTypedef) {
            typeHolder.addStructType(name(), pointerType.baseType())
        } else {
            typeHolder.addVar(name(), pointerType)
        }
        return pointerType
    }
}

data class AssignmentDeclarator(val declarator: Declarator, val rvalue: Expression): AnyDeclarator() { //TODO rename
    override fun<T> accept(visitor: DeclaratorVisitor<T>) = visitor.visit(this)

    override fun name(): String {
        return declarator.name()
    }

    override fun resolveType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): CType {
        return declarator.resolveType(declspec, typeHolder)
    }
}

object EmptyDeclarator : AnyDeclarator() {
    override fun name(): String = ""

    override fun<T> accept(visitor: DeclaratorVisitor<T>) = visitor.visit(this)

    override fun resolveType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): CType {
        return CType.UNKNOWN
    }
}

data class StructDeclarator(val declarator: AnyDeclarator, val expr: Expression): AnyDeclarator() {
    override fun <T> accept(visitor: DeclaratorVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun resolveType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): CType {
        require(expr is EmptyExpression) {
            "unsupported expression in struct declarator $expr"
        }

        return declarator.resolveType(declspec, typeHolder)
    }

    override fun name(): String {
        return when (declarator) {
            is Declarator -> declarator.name()
            else -> throw IllegalStateException("$declarator")
        }
    }
}

data class FunctionNode(val specifier: DeclarationSpecifier,
                        val declarator: Declarator,
                        val body: Statement) : AnyDeclarator() {
    override fun name(): String {
        return declarator.directDeclarator.decl.name()
    }

    fun functionDeclarator(): ParameterTypeList {
        return declarator.directDeclarator.directDeclaratorParams[0] as ParameterTypeList
    }

    override fun resolveType(declspec: DeclarationSpecifier, typeHolder: TypeHolder): CFunctionType {
        assertion(declspec === this.specifier) { "specifier mismatch" }

        val s = declarator.resolveType(declspec, typeHolder) as CFunctionType
        typeHolder.addFunctionType(name(), s) //TODO already added???
        return s
    }

    fun resolveType(typeHolder: TypeHolder): CFunctionType {
        return resolveType(specifier, typeHolder)
    }

    override fun <T> accept(visitor: DeclaratorVisitor<T>): T = visitor.visit(this)
}