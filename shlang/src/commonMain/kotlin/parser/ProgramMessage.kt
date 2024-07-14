package parser

import tokenizer.*


abstract class ProgramMessage

data class InvalidToken(val message: String, val token: AnyToken): ProgramMessage() {
    override fun toString(): String {
        return when (token) {
            is CToken -> "Error: $message at ${token.line()}:${token.pos()}, but found '${token.str()}' in '${token.position().filename()}'"
            else -> "Error: $message at $token"
        }
    }
}

class EndOfFile(val filename: String): ProgramMessage() {
    override fun toString(): String {
        return "Error: Unexpected EOF in '$filename'"
    }
}