package parser.nodes

import types.*
import codegen.consteval.*
import intrinsic.x64.VaInit
import tokenizer.tokens.*
import tokenizer.tokens.CToken
import parser.nodes.visitors.TypeNodeVisitor
import typedesc.CTypeBuilder
import typedesc.FunctionSpecifier
import typedesc.StorageClass
import typedesc.TypeHolder
import typedesc.TypeProperty
import typedesc.TypeQualifier


sealed class AnyTypeNode : Node() {
    abstract fun name(): String
    abstract fun<T> accept(visitor: TypeNodeVisitor<T>): T

    abstract fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder): TypeProperty

    protected inline fun<reified T: TypeProperty> addToBuilder(typeBuilder: CTypeBuilder, closure: () -> T): T {
        val property = closure()
        typeBuilder.add(property)
        return property
    }
}

data class UnionSpecifier(val name: Identifier, val fields: List<StructField>) : AnyTypeNode() {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        val members = arrayListOf<Member>()
        for (field in fields) {
            val type = field.declspec.specifyType(typeHolder, listOf()).type
            if (field.declarators.isEmpty()) {
                members.add(AnonMember(type))
                continue
            }
            for (declarator in field.declarators) {
                members.add(FieldMember(declarator.name(), type))
            }
        }

        val structType = CUnionType(name(), members)
        name.let { typeHolder.addNewType(it.str(), structType) }
        return@addToBuilder structType
    }

    override fun name(): String {
        return name.str()
    }
}

data class UnionDeclaration(val name: Identifier) : AnyTypeNode() { //TODO separate class
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        typeHolder.getTypeOrNull<CUnionType>(name.str()) ?: typeHolder.addNewType(name.str(), CUncompletedUnionType(name.str()))
    }

    override fun name(): String = name.str()
}

data class TypeQualifierNode(private val name: Keyword): AnyTypeNode() {
    override fun name(): String = name.str()

    override fun <T> accept(visitor: TypeNodeVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        qualifier()
    }

    fun qualifier(): TypeQualifier {
        return when (name.str()) {
            "const"    -> TypeQualifier.CONST
            "volatile" -> TypeQualifier.VOLATILE
            "restrict" -> TypeQualifier.RESTRICT
            else       -> TypeQualifier.EMPTY
        }
    }
}

data class StorageClassSpecifier(private val name: Keyword): AnyTypeNode() {
    override fun name(): String = name.str()

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

    fun storageClass(): StorageClass {
        return when (name.str()) {
            "typedef"  -> StorageClass.TYPEDEF
            "extern"   -> StorageClass.EXTERN
            "static"   -> StorageClass.STATIC
            "register" -> StorageClass.REGISTER
            "auto"     -> StorageClass.AUTO
            else       -> throw IllegalStateException("Unknown storage class $name")
        }
    }
}

data class TypeNode(private val name: CToken) : AnyTypeNode() {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)
    override fun name(): String = name.str()

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
            "__builtin_va_list" -> VaInit.vaList
            else      -> typeHolder.getTypedef(name.str()).cType()
        }
    }
}

data class StructSpecifier(private val name: Identifier, val fields: List<StructField>) : AnyTypeNode() {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)
    override fun name(): String = name.str()

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        val members = arrayListOf<Member>()
        for (field in fields) {
            val type = field.declspec.specifyType(typeHolder, listOf()) //TODo
            if (field.declarators.isEmpty()) {
                members.add(AnonMember(type.type))
                continue
            }
            for (declarator in field.declarators) {
                val resolved = declarator.declareType(field.declspec, typeHolder).type
                members.add(FieldMember(declarator.name(), resolved))
            }
        }
        val structType = CStructType(name.str(), members)
        return@addToBuilder typeHolder.addNewType(name.str(), structType)
    }
}

data class StructDeclaration(private val name: Identifier) : AnyTypeNode() {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)
    override fun name(): String = name.str()

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder<CType>(typeBuilder) {
        typeHolder.getTypeOrNull<CStructType>(name.str()) ?: typeHolder.addNewType(name.str(), CUncompletedStructType(name.str()))
    }
}

data class EnumSpecifier(private val name: Identifier, val enumerators: List<Enumerator>) : AnyTypeNode() {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        val enumeratorValues = hashMapOf<String, Int>()
        var enumValue = 0
        for (field in enumerators) {
            val constExpression = field.constExpr
            if (constExpression !is EmptyExpression) {
                val ctx = CommonConstEvalContext<Int>(typeHolder, enumeratorValues)
                val constExpr = ConstEvalExpression.eval(constExpression, TryConstEvalExpressionInt(ctx))
                if (constExpr == null) {
                    throw IllegalStateException("Cannot evaluate enum value")
                }
                enumValue = constExpr
            }
            enumeratorValues[field.name()] = enumValue
            enumValue++
        }

        return@addToBuilder typeHolder.addNewType(name.str(), CEnumType(name.str(), enumeratorValues))
    }

    override fun name(): String = name.str()
}

data class EnumDeclaration(private val name: Identifier) : AnyTypeNode() {
    override fun<T> accept(visitor: TypeNodeVisitor<T>) = visitor.visit(this)

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder) = addToBuilder(typeBuilder) {
        typeHolder.getTypeOrNull<CEnumType>(name.str()) ?: CUncompletedEnumType(name.str())
    }

    override fun name(): String = name.str()
}

// 6.7.4 Function specifiers
// https://port70.net/~nsz/c/c11/n1570.html#6.7.4
data class FunctionSpecifierNode(private val name: Keyword) : AnyTypeNode() {
    override fun name(): String = name.str()

    override fun <T> accept(visitor: TypeNodeVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun typeResolve(typeHolder: TypeHolder, typeBuilder: CTypeBuilder): TypeProperty {
        return when (name.str()) {
            "inline"   -> FunctionSpecifier.INLINE
            "noreturn" -> FunctionSpecifier.NORETURN
            else -> throw IllegalStateException("Unknown function specifier $name")
        }
    }
}