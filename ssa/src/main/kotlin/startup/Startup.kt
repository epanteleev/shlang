package startup

import ir.module.Module
import ir.pass.ana.VerifySSA
import ir.pass.transform.Mem2Reg
import ir.pass.transform.Mem2RegFabric
import ir.read.ModuleReader
import java.io.File


fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("<ir-file>.ir")
        return
    }

    val text = File(args[0]).readText()
    val module = ModuleReader(text).read()
    var opt: Module? = null

    try {
        Driver.output(args[0], module) {
            opt = it
            opt = Mem2RegFabric.create(opt as Module).run()
            opt = VerifySSA.run(opt as Module)
            opt as Module
        }
    } catch (ex: Throwable) {
        if (opt != null) {
            println(opt!!.toString())
        }

        ex.printStackTrace()
    }
}