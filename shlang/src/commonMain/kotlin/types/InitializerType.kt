package types

class InitializerType(private val initializer: List<CType>): CType() { //TODO remove it or stop inherit aggregate type ???
    override fun toString(): String = buildString {
        append("{")
        initializer.forEachIndexed { index, type ->
            append(type)
            if (index < initializer.size - 1) append(", ")
        }
        append("}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InitializerType) return false

        if (initializer != other.initializer) return false

        return true
    }

    override fun hashCode(): Int {
        return initializer.hashCode()
    }
}