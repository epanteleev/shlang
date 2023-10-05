package ir

abstract class AnyFunctionPrototype(val name: String,
                                    protected val returnType: Type,
                                    protected val arguments: List<Type>) {
    fun type(): Type {
        return returnType
    }

    fun arguments(): List<Type> {
        return arguments
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is AnyFunctionPrototype) {
            return name == other.name &&
                    returnType == other.type() &&
                    arguments == other.arguments()

        }

        return false
    }

    override fun hashCode(): Int {
        return name.hashCode() + arguments.hashCode() + returnType.hashCode()
    }
}

class FunctionPrototype(name: String, returnType: Type, arguments: List<Type>):
    AnyFunctionPrototype(name, returnType, arguments) {
    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("define $name(")
        arguments.joinTo(builder)
        builder.append("): $returnType")
        return builder.toString()
    }
}

class ExternFunction(name: String, returnType: Type, arguments: List<Type>):
    AnyFunctionPrototype(name, returnType, arguments) {
    override fun toString(): String {
        return "extern ${type()} $name(${arguments.joinToString()})"
    }
}