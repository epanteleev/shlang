package ir.types


sealed interface PrimitiveType : NonTrivialType {
    override fun alignmentOf(): Int {
        return sizeOf()
    }
}