package common


object GNUAssemblerRunner {

    fun run(filename: String, outputFileName: String) {
        val gnuAsCommandLine = listOf(filename, "-o", outputFileName)
        val result = RunExecutable.runCommand("as", gnuAsCommandLine, null)
        if (result.exitCode != 0) {
            throw RuntimeException("execution failed with code ${result.exitCode}:\n ${result.error}")
        }
    }
}