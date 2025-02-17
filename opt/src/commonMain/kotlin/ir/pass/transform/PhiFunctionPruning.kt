package ir.pass.transform

import ir.module.*
import common.assertion
import ir.instruction.Phi
import ir.value.constant.UndefValue
import ir.pass.analysis.traverse.PreOrderFabric


internal class PhiFunctionPruning private constructor(private val cfg: FunctionData) {
    private val usefull = hashMapOf<Phi, Boolean>()
    private val worklist = arrayListOf<Phi>()

    fun markUseless(phi: Phi) {
        usefull[phi] = false
    }

    fun markUseful(phi: Phi) {
        usefull[phi] = true
    }

    fun isUseful(phi: Phi): Boolean {
        return usefull[phi]!!
    }

    fun forEachUseless(closure: (Phi) -> Unit) {
        for ((phi, useful) in usefull) {
            if (!useful) {
                closure(phi)
            }
        }
    }

    private fun initialSetup() {
        for (bb in cfg.analysis(PreOrderFabric)) {
            for (inst in bb) {
                if (inst is Phi) {
                    markUseless(inst)
                    continue
                }

                for (op in inst.operands()) {
                    if (op !is Phi) {
                        continue
                    }

                    markUseful(op)
                    worklist.add(op)
                }
            }
        }
    }

    private fun usefulnessPropagation() {
        while (worklist.isNotEmpty()) {
            val phi = worklist.removeLast()

            for (op in phi.operands()) {
                if (op !is Phi) {
                    continue
                }

                if (isUseful(op)) {
                    continue
                }

                markUseful(op)
                worklist.add(op)
            }
        }
    }

    private fun prunePhis() {
        fun removePhi(phi: Phi) {
            assertion(phi.usedIn().fold(true) { acc, value -> acc && (value is Phi) }) {
                "phi value is used in non phi instruction"
            }

            phi.owner().kill(phi, UndefValue)
        }

        forEachUseless { phi -> removePhi(phi) }
    }

    private fun run() {
        initialSetup()
        usefulnessPropagation()
        prunePhis()
    }

    companion object {
        fun run(module: Module): Module {
            for (f in module.functions()) {
                PhiFunctionPruning(f).run()
            }

            return module
        }
    }
}