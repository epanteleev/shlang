package tokenizer


class TokenPrinter private constructor(val tokens: TokenList) {
    private val stringBuilder = StringBuilder()

    private fun construct(): String {
        for (token in tokens) {
            if (token is ExitIncludeGuard) {
                stringBuilder.append('\n')
            }
            stringBuilder.append(token.str())
        }
        return stringBuilder.toString()
    }

    companion object {
        fun print(tokens: TokenList): String {
            return TokenPrinter(tokens).construct()
        }
    }
}