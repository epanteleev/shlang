package parser.nodes.visitors

import parser.nodes.*


interface ExpressionVisitor<T> {
    fun visit(expression: CompoundLiteral): T
    fun visit(unaryOp: UnaryOp): T
    fun visit(binop: BinaryOp): T
    fun visit(conditional: Conditional): T
    fun visit(functionCall: FunctionCall): T
    fun visit(arrayAccess: ArrayAccess): T
    fun visit(stringNode: StringNode): T
    fun visit(assignment: CharNode): T
    fun visit(sizeOf: SizeOf): T
    fun visit(cast: Cast): T
    fun visit(numNode: NumNode): T
    fun visit(varNode: VarNode): T
    fun visit(arrowMemberAccess: ArrowMemberAccess): T
    fun visit(memberAccess: MemberAccess): T
    fun visit(emptyExpression: EmptyExpression): T
    fun visit(builtin: BuiltinVaArg): T
    fun visit(builtin: BuiltinVaStart): T
    fun visit(builtin: BuiltinVaEnd): T
    fun visit(builtin: BuiltinVaCopy): T
}