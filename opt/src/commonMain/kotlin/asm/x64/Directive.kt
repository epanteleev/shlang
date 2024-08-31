package asm.x64

sealed class AnyDirective

sealed class SectionDirective: AnyDirective()

sealed class NamedDirective: AnyDirective() {
    abstract val name: String

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as NamedDirective

        return name == other.name
    }

    final override fun hashCode(): Int {
        return name.hashCode()
    }
}

sealed class AnonymousDirective: AnyDirective()

data object TextSection : SectionDirective() {
    override fun toString(): String = ".text"
}

data object DataSection : SectionDirective() {
    override fun toString(): String = ".data"
}

data object BssSection : SectionDirective() {
    override fun toString(): String = ".bss"
}

class GlobalDirective(val name: String) : AnonymousDirective() {
    override fun toString(): String = ".global $name"
}

data object ExternDirective : AnonymousDirective() {
    override fun toString(): String = ".extern"
}

class ObjLabel(override val name: String): NamedDirective() {
    internal val anonymousDirective = arrayListOf<AnyDirective>()

    override fun toString(): String = buildString {
        append("$name:\n")
        for ((idx, d) in anonymousDirective.withIndex()) {
            append("$d")
            if (idx != anonymousDirective.size - 1) {
                append("\n")
            }
        }
    }
}

class ByteDirective(val value: String): AnonymousDirective() {
    override fun toString(): String = ".byte $value"
}

class ShortDirective(val value: String): AnonymousDirective() {
    override fun toString(): String = ".short $value"
}

class LongDirective(val value: String): AnonymousDirective() {
    override fun toString(): String = ".long $value"
}

class QuadDirective(val value: String): AnonymousDirective() {
    override fun toString(): String = ".quad $value"
}

class StringDirective(val value: String): AnonymousDirective() {
    override fun toString(): String = ".string $value"
}

class CommSymbol(override val name: String, val size: Int): NamedDirective() {
    override fun toString(): String = ".comm $name, $size, 32"
}

class AsciiDirective(val data: String): AnonymousDirective() {
    override fun toString(): String = ".ascii \"$data\""
}