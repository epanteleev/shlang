package parser.nodes.visitors

import parser.nodes.*


interface UnclassifiedNodeVisitor<T> {
    fun visit(functionNode: FunctionNode): T
    fun visit(programNode: ProgramNode): T
    fun visit(dummyNode: DummyNode): T
    fun visit(enumerator: Enumerator): T
    fun visit(structField: StructField): T
    fun visit(declaration: Declaration): T
    fun visit(nodePointer: NodePointer): T
    fun visit(identNode: IdentNode): T
    fun visit(abstractDeclarator: AbstractDeclarator): T
    fun visit(directDeclarator: DirectDeclarator): T
    fun visit(designation: Designation): T
    fun visit(arrayDesignator: ArrayDesignator): T
    fun visit(memberDesignator: MemberDesignator): T
}
