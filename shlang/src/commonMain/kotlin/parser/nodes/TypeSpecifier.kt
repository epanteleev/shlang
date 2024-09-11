package parser.nodes

import types.*
import parser.nodes.visitors.TypeSpecifierVisitor


sealed class TypeSpecifier : Node() {
    private var cachedType: VarDescriptor? = null

    abstract fun<T> accept(visitor: TypeSpecifierVisitor<T>): T
    abstract fun specifyType(typeHolder: TypeHolder, pointers: List<NodePointer>): VarDescriptor

    protected fun memoizeType(type: () -> VarDescriptor): VarDescriptor {
        if (cachedType == null) {
            cachedType = type()
        }

        return cachedType!!
    }
}

data class DeclarationSpecifier(val specifiers: List<AnyTypeNode>) : TypeSpecifier() {
    internal var isTypedef = false

    private fun specifyType1(typeHolder: TypeHolder) = memoizeType {
        val typeBuilder = CTypeBuilder()
        for (specifier in specifiers) {
            val property = specifier.typeResolve(typeHolder, typeBuilder)
            if (property == StorageClass.TYPEDEF) {
                isTypedef = true
            }
        }

        return@memoizeType typeBuilder.build(typeHolder)
    }

    override fun specifyType(typeHolder: TypeHolder, pointers: List<NodePointer>): VarDescriptor {
        val type = specifyType1(typeHolder)
        if (pointers.isEmpty()) {
            return type
        }

        var pointerType = type.type.baseType()
        for (idx in 0 until pointers.size - 1) {
            val pointer = pointers[idx]
            pointerType = CPointerT(pointerType, pointer.property().toSet())
        }

        return VarDescriptor(CPointerType(CPointerT(pointerType), type.type.properties), type.storageClass)
    }

    override fun<T> accept(visitor: TypeSpecifierVisitor<T>): T = visitor.visit(this)
}

data class TypeName(val specifiers: DeclarationSpecifier, val abstractDecl: AbstractDeclarator?) : TypeSpecifier() {
    override fun<T> accept(visitor: TypeSpecifierVisitor<T>): T = visitor.visit(this)

    override fun specifyType(typeHolder: TypeHolder, pointers: List<NodePointer>): VarDescriptor {
        val specifierType = specifiers.specifyType(typeHolder, pointers)
        if (abstractDecl == null) {
            return specifierType
        }

        return VarDescriptor(abstractDecl.resolveType(specifierType.type, typeHolder), specifierType.storageClass)
    }
}