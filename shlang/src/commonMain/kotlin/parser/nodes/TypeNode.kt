package parser.nodes

import types.*
import typedesc.*
import codegen.consteval.*
import intrinsic.VaStart
import tokenizer.tokens.*
import tokenizer.tokens.CToken
import parser.nodes.visitors.TypeNodeVisitor
import tokenizer.Position


sealed class AnyTypeNode(val name: CToken) : Node() {
    final override fun begin(): Position = name.position()
    fun name(): String = name.str()

    abstract fun<T> accept(visitor: TypeNodeVisitor<T>): T

    abstract fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder): TypeProperty

    protected fun resolveFieldTypes(typeHolder: TypeHolder, fields: List<StructField>): List<Member> {
        val members = arrayListOf<Member>()
        for (field in fields) {
            val type = field.declspec.specifyType(typeHolder, listOf()).typeDesc
            if (field.declarators.isEmpty()) {
                members.add(AnonMember(type))
                continue
            }
            for (declarator in field.declarators) {
                val resolved = declarator.declareType(field.declspec, typeHolder).typeDesc
                members.add(FieldMember(declarator.name(), resolved))
            }
        }

        return members
    }

    protected inline fun<reified T: TypeProperty> addToBuilder(typeBuilder: CTypeBuilder, closure: () -> T): T {
        val property = closure()
        typeBuilder.add(property)
        return property
    }
}

class UnionSpecifier(name: Identifier, val fields: List<StructField>) : AnyTypeNode(name) {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        val structType = CUnionType(name(), resolveFieldTypes(typeHolder, fields))
        return@addToBuilder typeHolder.addNewType(name.str(), structType)
    }
}

class UnionDeclaration(name: Identifier) : AnyTypeNode(name) { //TODO separate class
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        typeHolder.getTypeOrNull<CUnionType>(name.str()) ?: typeHolder.addNewType(name.str(), CUncompletedUnionType(name.str()))
    }
}

class TypeQualifierNode(name: Keyword): AnyTypeNode(name) {
    override fun <T> accept(visitor: TypeNodeVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        qualifier()
    }

    fun qualifier(): TypeQualifier = when (name.str()) {
        "const"    -> TypeQualifier.CONST
        "volatile" -> TypeQualifier.VOLATILE
        "restrict" -> TypeQualifier.RESTRICT
        else       -> throw IllegalStateException("Unknown type qualifier '$name'")
    }
}

class StorageClassSpecifier(name: Keyword): AnyTypeNode(name) {
    override fun <T> accept(visitor: TypeNodeVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder): TypeProperty {
        val storageClass = storageClass()
        if (storageClass != StorageClass.TYPEDEF) {
           typeBuilder.add(storageClass)
        }

        return storageClass
    }

    private fun storageClass(): StorageClass = when (name.str()) {
        "typedef"  -> StorageClass.TYPEDEF
        "extern"   -> StorageClass.EXTERN
        "static"   -> StorageClass.STATIC
        "register" -> StorageClass.REGISTER
        "auto"     -> StorageClass.AUTO
        else       -> throw IllegalStateException("Unknown storage class $name")
    }
}

class TypeNode(name: CToken) : AnyTypeNode(name) {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        when (name.str()) {
            "void"    -> VOID
            "char"    -> CHAR
            "short"   -> SHORT
            "int"     -> INT
            "long"    -> LONG
            "float"   -> FLOAT
            "double"  -> DOUBLE
            "signed"  -> INT
            "unsigned"-> UINT
            "_Bool"   -> BOOL
            "__builtin_va_list" -> VaStart.vaList
            else      -> typeHolder.getTypedef(name.str()).cType()
        }
    }
}

class StructSpecifier(name: Identifier, val fields: List<StructField>) : AnyTypeNode(name) {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        val structType = CStructType(name.str(), resolveFieldTypes(typeHolder, fields))
        return@addToBuilder typeHolder.addNewType(name.str(), structType)
    }
}

class StructDeclaration(name: Identifier) : AnyTypeNode(name) {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder<CType>(typeBuilder) {
        typeHolder.getTypeOrNull<CStructType>(name.str()) ?: typeHolder.addNewType(name.str(), CUncompletedStructType(name.str()))
    }
}

class Enumerator(val ident: Identifier, val constExpr: Expression) {
    fun begin(): Position = ident.position()
    fun name() = ident.str()
}

class EnumSpecifier(name: Identifier, val enumerators: List<Enumerator>) : AnyTypeNode(name) {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        val enumeratorValues = hashMapOf<String, Int>()
        var enumValue = 0
        for (field in enumerators) {
            val constExpression = field.constExpr
            if (constExpression !is EmptyExpression) {
                val ctx = CommonConstEvalContext<Int>(typeHolder, enumeratorValues)
                val constExpr = ConstEvalExpression.eval(constExpression, TryConstEvalExpressionInt(ctx))
                    ?: throw IllegalStateException("Cannot evaluate enum value")
                enumValue = constExpr
            }
            enumeratorValues[field.name()] = enumValue
            enumValue++
        }

        return@addToBuilder typeHolder.addNewType(name.str(), CEnumType(name.str(), enumeratorValues))
    }
}

class EnumDeclaration(name: Identifier) : AnyTypeNode(name) {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        typeHolder.getTypeOrNull<CEnumType>(name.str()) ?: CUncompletedEnumType(name.str())
    }
}

// 6.7.4 Function specifiers
// https://port70.net/~nsz/c/c11/n1570.html#6.7.4
class FunctionSpecifierNode(name: Keyword) : AnyTypeNode(name) {
    override fun <T> accept(visitor: TypeNodeVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder): TypeProperty = when (name.str()) {
        "inline"   -> FunctionSpecifier.INLINE
        "noreturn" -> FunctionSpecifier.NORETURN
        else -> throw IllegalStateException("Unknown function specifier $name")
    }
}