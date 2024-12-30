package startup

import common.Files
import common.commandLine.AnyCLIArguments


class ShlangCLIArguments : AnyCLIArguments() {
    private val includeDirectories = hashSetOf<String>()
    private val defines = hashMapOf<String, String>()
    private var preprocessOnly = false
    private var dumpDefines = false
    private var optionC = false
    private var inputFilename = "<input>"

    fun getOutputFilename(): String {
        if (outFilename != null) {
            return outFilename!!
        }

        val name = getFilename()
        val lastIndex = name.lastIndexOf('.')
        val basename = if (lastIndex != -1) {
            name.substring(0, lastIndex)
        } else {
            name
        }

        return "$basename.o"
    }

    fun getFilename(): String = inputFilename
    fun getBasename(): String = getName(inputFilename)

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
        inputFilename = executableFileName

        if (outFilename == null && Files.getExtension(executableFileName) == ".o") {
            setOutputFilename(Files.replaceExtension(executableFileName, ".o"))
        }
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

    fun makeOptCLIArguments(): OptCLIArguments {
        val optCLIArguments = OptCLIArguments()
        optCLIArguments.setFilename(inputFilename)
        optCLIArguments.setOptLevel(optimizationLevel)
        if (isDumpIr()) {
            optCLIArguments.setDumpIrDirectory(dumpIrDirectoryOutput!!)
        }
        if (outFilename != null) {
            optCLIArguments.setOutputFilename(outFilename!!)
        }
        return optCLIArguments
    }
}