package types

import typedesc.TypeDesc

sealed class Member {
    abstract fun cType(): CType
    abstract fun typeDesc(): TypeDesc
    abstract fun size(): Int
}

class AnonMember(val typeDesc: TypeDesc): Member() {
    override fun cType(): AnyStructType = typeDesc.cType() as AnyStructType
    override fun typeDesc(): TypeDesc   = typeDesc
    override fun size(): Int            = typeDesc.size()
    override fun toString(): String     = typeDesc.toString()
}

class FieldMember(val name: String, val typeDesc: TypeDesc): Member() {
    override fun cType(): CType       = typeDesc.cType()
    override fun typeDesc(): TypeDesc = typeDesc
    override fun size(): Int          = typeDesc.size()
    override fun toString(): String   = "$typeDesc $name;"
}