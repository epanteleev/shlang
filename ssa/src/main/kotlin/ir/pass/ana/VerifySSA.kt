package ir.pass.ana

import common.forEachWith
import ir.types.Type
import ir.module.Module
import ir.instruction.*
import ir.utils.CreationInfo
import ir.module.block.Block
import ir.module.block.Label
import ir.module.FunctionData
import ir.AnyFunctionPrototype
import ir.instruction.utils.IRInstructionVisitor


data class ValidateSSAErrorException(override val message: String): Exception(message)


class VerifySSA private constructor(private val functionData: FunctionData,
                                    private val prototypes: List<AnyFunctionPrototype>): IRInstructionVisitor<Unit> {
    private val dominatorTree by lazy { functionData.blocks.dominatorTree() }
    private val creation by lazy { CreationInfo.create(functionData.blocks) }
    private var bb = functionData.blocks.begin()
    private var exitBlocks = 0
    private val adjustStackFrame = arrayListOf<AdjustStackFrame>()

    private fun pass() {
        for (bb in functionData.blocks) {
            validateBlock(bb)
        }
        validateExitBlock()
    }

    private fun validateExitBlock() {
        assert(exitBlocks == 1) {
            "Allowed only one exit block, but found $exitBlocks blocks."
        }

        assert(adjustStackFrame.isEmpty()) {
            "Stack frame is not balanced: $adjustStackFrame"
        }
    }

    private fun checkReturn() {
        assert(bb.successors().isEmpty()) {
            "Exit block '$bb' has successors: ${bb.successors()}"
        }
        exitBlocks += 1
    }

    private fun validateBlock(block: Block) {
        if (block.equals(Label.entry)) {
            assert(block.predecessors().isEmpty()) { "Begin block must not have predecessors." }
        }
        assert(!block.isEmpty()) { "Block must not be empty" }
        bb = block
        validateInstructions(block)
    }

    /** Check whether definition dominates to usage. */
    private fun validateDefUse(instruction: Instruction, block: Block) {
        for (use in instruction.operands()) {
            if (use !is ValueInstruction) {
                continue
            }

            val definedIn = creation[use].block
            val isDefDominatesUse = dominatorTree.dominates(definedIn, block)
            assert(isDefDominatesUse) { "Definition doesn't dominate to usage: value defined in '$definedIn', but used in '$block'" }
        }
    }

    private fun validateInstructions(block: Block) {
        for (instruction in block) {
            if (instruction !is Phi) {
                validateDefUse(instruction, block)
            }

            if (instruction is ValueInstruction) {
                for (user in instruction.usedIn()) {
                    assert(user.operands().contains(instruction)) {
                        "should be inst='${instruction.dump()}', user='${user.dump()}', usedIn='${instruction.usedIn()}'"
                    }
                }
            }

            instruction.visit(this)
        }
    }

    override fun visit(alloc: Alloc) {
        assert(Alloc.typeCheck(alloc)) {
            "Instruction '${alloc.dump()}' has inconsistent types."
        }
    }

    override fun visit(generate: Generate) {
        assert(Generate.typeCheck(generate)) {
            "Instruction '${generate.dump()}' has inconsistent types."
        }
    }

    override fun visit(lea: Lea) {
        assert(Lea.typeCheck(lea)) {
            "Instruction '${lea.dump()}' has inconsistent types."
        }
    }

    override fun visit(binary: ArithmeticBinary) {
        assert(ArithmeticBinary.typeCheck(binary)) {
            "Instruction '${binary.dump()}' requires all operands to be of " +
                    "the same type: a=${binary.first().type()}, b=${binary.second().type()}"
        }
    }

    override fun visit(neg: Neg) {
        assert(Neg.typeCheck(neg)) {
            "Instruction '${neg.dump()}' must have the same types: destination=${neg.type()} operand=${neg.operand().type()}"
        }
    }

    override fun visit(not: Not) {
        assert(Not.typeCheck(not)) {
            "Instruction '${not.dump()}' must have the same types: destination=${not.type()} operand=${not.operand().type()}"
        }
    }

    override fun visit(branch: Branch) {
        val target = branch.target()
        val successors = bb.successors()

        assert(bb.successors().size == 1) {
            "Block '$bb' has other count of successors: successors=$successors"
        }
        assert(target == successors[0]) {
            "Block '$bb' has inconsistent successors: branch=${branch.targets.joinToString { it.toString() }}, successors='${successors}'"
        }
        assert(target.predecessors().contains(bb)) {
            "Block '$target' has inconsistent predecessors: branch=${branch.targets.joinToString { it.toString() }}, predecessors='${target.predecessors()}'"
        }
    }

    override fun visit(branchCond: BranchCond) {
        val onTrue  = branchCond.onTrue()
        val onFalse = branchCond.onFalse()
        val successors = bb.successors()

        assert(BranchCond.typeCheck(branchCond)) {
            "Inconsistent branch condition: '${branchCond.dump()}'"
        }
        assert(bb.successors().size == 2) {
            "Block '$bb' has not 2 successors exactly: ${bb.successors()}"
        }
        assert(onTrue == successors[0]) {
            "Block '$bb' has inconsistent successors: branch='${branchCond.dump()}', successors='${successors}'"
        }
        assert(onFalse == successors[1]) {
            "Block '$bb' has inconsistent successors: branch='${branchCond.dump()}', successors='${successors}'"
        }
        assert(onTrue.predecessors().contains(bb)) {
            "Block '$onTrue' has inconsistent predecessors: branch='${branchCond.dump()}', predecessors='${onTrue.predecessors()}'"
        }
        assert(onFalse.predecessors().contains(bb)) {
            "Block '$onTrue' has inconsistent predecessors: branch='${branchCond.dump()}', predecessors='${onTrue.predecessors()}'"
        }
    }

    override fun visit(call: Call) {
        assert(Callable.typeCheck(call)) {
            "Call instruction '${call.dump()}' has inconsistent return types."
        }

        assert(prototypes.contains(call.prototype())) {
            "Called undefined function: prototype=${call.prototype()}"
        }
    }

    override fun visit(bitcast: Bitcast) {
        assert(Bitcast.typeCheck(bitcast)) {
            "Cast instruction '${bitcast.dump()}' has inconsistent types."
        }
    }

    override fun visit(zext: ZeroExtend) {
        assert(ZeroExtend.typeCheck(zext)) {
            "Instruction '${zext.dump()}' has inconsistent types."
        }
    }

    override fun visit(sext: SignExtend) {
        assert(SignExtend.typeCheck(sext)) {
            "Instruction '${sext.dump()}' has inconsistent types."
        }
    }

    override fun visit(pcmp: PointerCompare) {
        assert(PointerCompare.typeCheck(pcmp)) {
            "Instruction '${pcmp.dump()}' has inconsistent types."
        }
    }

    override fun visit(trunc: Truncate) {
        assert(Truncate.typeCheck(trunc)) {
            "Instruction '${trunc.dump()}' has inconsistent types."
        }
    }

    override fun visit(fptruncate: FpTruncate) {
        assert(FpTruncate.typeCheck(fptruncate)) {
            "Instruction '${fptruncate.dump()}' has inconsistent types."
        }
    }

    override fun visit(fpext: FpExtend) {
        assert(FpExtend.typeCheck(fpext)) {
            "Instruction '${fpext.dump()}' has inconsistent types."
        }
    }

    override fun visit(fptosi: FloatToSigned) {
        assert(FloatToSigned.typeCheck(fptosi)) {
            "Instruction '${fptosi.dump()}' has inconsistent types."
        }
    }

    override fun visit(copy: Copy) {
        assert(Copy.typeCheck(copy)) {
            "Instruction '${copy.dump()}' has inconsistent types."
        }
    }

    override fun visit(move: Move) {
        assert(Move.typeCheck(move)) {
            "Instruction '${move.dump()}' has inconsistent types."
        }
    }

    override fun visit(downStackFrame: DownStackFrame) {
        adjustStackFrame.add(downStackFrame)
    }

    override fun visit(gep: GetElementPtr) {
        assert(GetElementPtr.typeCheck(gep)) {
            "Instruction '${gep.dump()}' has inconsistent types."
        }
    }

    override fun visit(gfp: GetFieldPtr) {
        assert(GetFieldPtr.typeCheck(gfp)) {
            "Instruction '${gfp.dump()}' has inconsistent types."
        }
    }

    override fun visit(icmp: SignedIntCompare) {
        assert(SignedIntCompare.typeCheck(icmp)) {
            "Instruction '${icmp.dump()}' requires all operands to be of the same type: a=${icmp.first().type()}, b=${icmp.second().type()}"
        }
    }

    override fun visit(ucmp: UnsignedIntCompare) {
        assert(UnsignedIntCompare.typeCheck(ucmp)) {
            "Instruction '${ucmp.dump()}' requires all operands to be of the same type: a=${ucmp.first().type()}, b=${ucmp.second().type()}"
        }
    }

    override fun visit(floatCompare: FloatCompare) {
        assert(FloatCompare.typeCheck(floatCompare)) {
            "Instruction '${floatCompare.dump()}' requires all operands to be of the same type: a=${floatCompare.first().type()}, b=${floatCompare.second().type()}"
        }
    }

    override fun visit(load: Load) {
        assert(Load.typeCheck(load)) {
            "Instruction '${load.dump()}' requires all operands to be of the same type."
        }
    }

    override fun visit(phi: Phi) {
        assert(Phi.typeCheck(phi)) {
            "Inconsistent phi instruction '${phi.dump()}': different types ${phi.operands().map { it.type() }.joinToString()}"
        }

        val blocks = phi.incoming()
        blocks.forEachWith(phi.operands()) { incoming, use ->
            if (use !is ValueInstruction) {
                return@forEachWith
            }
            val actual = creation[use].block
            assert(dominatorTree.dominates(actual, incoming)) {
                "Inconsistent phi instruction $phi: value defined in $incoming, used in $actual"
            }
        }

        val incoming     = phi.incoming()
        val predecessors = bb.predecessors()
        assert(predecessors.size == incoming.size) {
            "Inconsistent phi instruction: incoming blocks and predecessors are not equal. incoming=$incoming predecessors=$predecessors"
        }
    }

    override fun visit(returnValue: ReturnValue) {
        assert(ReturnValue.typeCheck(returnValue)) {
            "Inconsistent return value: '${returnValue.dump()}'"
        }

        val retType = functionData.prototype.returnType()
        assert(returnValue.type() == retType) {
            "Inconsistent return type: '${returnValue.dump()}', but expected '${retType}'"
        }

        checkReturn()
    }

    override fun visit(returnVoid: ReturnVoid) {
        val retType = functionData.prototype.returnType()
        assert(Type.Void == retType) {
            "Inconsistent return type: '${returnVoid.dump()}', but expected '${retType}'"
        }

        checkReturn()
    }

    override fun visit(indirectionCall: IndirectionCall) {
        assert(Callable.typeCheck(indirectionCall)) {
            "Call instruction '${indirectionCall.dump()}' has inconsistent return types."
        }
    }

    override fun visit(indirectionVoidCall: IndirectionVoidCall) {
        assert(Callable.typeCheck(indirectionVoidCall)) {
            "Call instruction '${indirectionVoidCall.dump()}' has inconsistent return types."
        }
    }

    override fun visit(select: Select) {
        assert(Select.typeCheck(select)) {
            "Instruction '${select.dump()}' requires all operands to be of the same type."
        }
    }

    override fun visit(store: Store) {
        assert(Store.typeCheck(store)) {
            "Instruction '${store.dump()}' requires all operands to be of the same type."
        }
    }

    override fun visit(upStackFrame: UpStackFrame) {
        val downStackFrame = adjustStackFrame.removeAt(adjustStackFrame.size - 1)
        val downCall = downStackFrame.call().prototype()
        val upCall = upStackFrame.call().prototype()
        assert(upCall == downCall) {
            "Inconsistent stack frame size: down='${downCall}' up='${upCall}'"
        }
    }

    override fun visit(voidCall: VoidCall) {
        assert(Callable.typeCheck(voidCall)) {
            "Call instruction '${voidCall.dump()}' has inconsistent return types."
        }

        assert(prototypes.contains(voidCall.prototype())) {
            "Called undefined function: prototype=${voidCall.prototype()}"
        }
    }

    companion object {
        fun run(module: Module): Module {
            val prototypes = module.prototypes
            module.functions.forEach { data ->
                VerifySSA(data, prototypes).pass()
            }

            return module
        }

        private fun assert(condition: Boolean, message: () -> String) {
            if (!condition) {
                throw ValidateSSAErrorException(message())
            }
        }
    }
}