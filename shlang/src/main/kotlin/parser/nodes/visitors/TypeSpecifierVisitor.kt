package parser.nodes.visitors

import parser.nodes.*


interface TypeSpecifierVisitor<T> {
    fun visit(specifierType: DeclarationSpecifier): T
    fun visit(node: TypeName): T
}