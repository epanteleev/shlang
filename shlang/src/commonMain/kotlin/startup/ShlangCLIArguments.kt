package startup

import common.ProcessedFile


class ShlangCLIArguments {
    private val includeDirectories = hashSetOf<String>()
    private val defines = hashMapOf<String, String>()
    private var preprocessOnly = false
    private var dumpDefines = false
    private var optionC = false
    private var inputs = arrayListOf<ProcessedFile>()

    private var dumpIrDirectoryOutput: String? = null
    private var optimizationLevel = 0
    private var outFilename = DEFAULT_OUTPUT

    fun inputs(): List<ProcessedFile> = inputs

    fun setDumpIrDirectory(out: String) {
        dumpIrDirectoryOutput = out
    }

    fun getDumpIrDirectory(): String? = dumpIrDirectoryOutput

    fun setOutputFilename(name: String) {
        outFilename = ProcessedFile.fromFilename(name)
    }

    fun setOptLevel(level: Int) {
        optimizationLevel = level
    }

    fun getOptLevel(): Int = optimizationLevel

    fun getOutputFilename(): ProcessedFile = outFilename

    fun setDumpDefines(dumpDefines: Boolean) {
        this.dumpDefines = dumpDefines
    }

    fun setIsCompile(flag: Boolean) {
        optionC = flag
    }

    fun isCompile() = optionC

    fun setPreprocessOnly(preprocessOnly: Boolean) {
        this.preprocessOnly = preprocessOnly
    }

    fun setInputFileName(executableFileName: String) {
        inputs.add(ProcessedFile.fromFilename(executableFileName))
    }

    fun isPreprocessOnly(): Boolean = preprocessOnly
    fun isDumpDefines(): Boolean = dumpDefines

    fun addIncludeDirectory(directory: String) {
        includeDirectories.add(directory)
    }

    fun addDefine(name: String, value: String) {
        defines[name] = value
    }

    fun getDefines(): Map<String, String> = defines

    fun getIncludeDirectories(): Set<String> = includeDirectories

    companion object {
        val DEFAULT_OUTPUT = ProcessedFile.create("a", common.Extension.EXE)
    }
}