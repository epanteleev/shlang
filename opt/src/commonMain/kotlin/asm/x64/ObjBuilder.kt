package asm.x64

sealed interface ObjBuilder {
    fun byte(value: Byte)
    fun short(value: Short)
    fun long(value: Int)
    fun quad(label: ObjLabel)
    fun quad(label: ObjLabel, offset: Int)
    fun quad(value: Long)
    fun string(value: String)
    fun ascii(value: String)
}