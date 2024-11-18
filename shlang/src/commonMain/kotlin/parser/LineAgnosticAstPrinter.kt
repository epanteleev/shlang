package parser

import parser.nodes.*
import parser.nodes.visitors.NodeVisitor


class LineAgnosticAstPrinter: NodeVisitor<Unit> {
    private val buffer = StringBuilder()

    fun <T> joinTo(list: Iterable<T>, separator: CharSequence = ", ", prefix: CharSequence = "", transform: ((T) -> Unit)) {
        var count = 0
        buffer.append(prefix)
        for (element in list) {
            if (++count > 1) {
                buffer.append(separator)
            }
            transform(element)
            count += 1
        }
    }

    override fun visit(identNode: IdentNode) {
        buffer.append(identNode.str())
    }

    override fun visit(expression: CompoundLiteral) {
        buffer.append('(')
        expression.typeName.accept(this)
        buffer.append(')')
        expression.initializerList.accept(this)
    }

    override fun visit(cast: Cast) {
        buffer.append('(')
        cast.typeName.accept(this)
        buffer.append(')')
        buffer.append(' ')
        cast.cast.accept(this)
    }

    override fun visit(arrayAccess: ArrayAccess) {
        arrayAccess.primary.accept(this)
        buffer.append('[')
        arrayAccess.expr.accept(this)
        buffer.append(']')
    }

    override fun visit(unaryOp: UnaryOp) {
        if (unaryOp.opType is PrefixUnaryOpType) {
            buffer.append(unaryOp.opType)
        }
        unaryOp.primary.accept(this)
        if (unaryOp.opType is PostfixUnaryOpType) {
            buffer.append(unaryOp.opType)
        }
    }

    override fun visit(sizeOf: SizeOf) {
        buffer.append("sizeof(")
        sizeOf.expr.accept(this)
        buffer.append(')')
    }

    override fun visit(stringNode: StringNode) {
        for (literal in stringNode.literals) {
            buffer.append(literal.str())
        }
    }

    override fun visit(assignment: CharNode) {
        buffer.append('\'')
        buffer.append(assignment.char.str())
        buffer.append('\'')
    }

    override fun visit(numNode: NumNode) {
        buffer.append(numNode.number.str())
    }

    override fun visit(switchStatement: SwitchStatement) {
        buffer.append("switch(")
        switchStatement.condition.accept(this)
        buffer.append(") {")
        switchStatement.body.accept(this)
        buffer.append('}')
    }

    override fun visit(declarator: Declarator) {
        joinTo(declarator.pointers, "") {
            visit(it)
        }

        declarator.directDeclarator.accept(this)
    }

    override fun visit(declaration: Declaration) {
        declaration.declspec.accept(this)
        buffer.append(' ')
        joinTo(declaration.declarators(), ", ") {
            it.accept(this)
        }

        buffer.append(';')
    }

    override fun visit(returnStatement: ReturnStatement) {
        buffer.append("return ")
        returnStatement.expr.accept(this)
        buffer.append(';')
    }

    override fun visit(ifStatement: IfStatement) {
        buffer.append("if(")
        ifStatement.condition.accept(this)
        buffer.append(") {")
        ifStatement.then.accept(this)
        buffer.append('}')
        if (ifStatement.elseNode !is EmptyStatement) {
            buffer.append(" else ")
            if (ifStatement.elseNode !is IfStatement) {
                buffer.append('{')
            }

            ifStatement.elseNode.accept(this)

            if (ifStatement.elseNode !is IfStatement) {
                buffer.append('}')
            }
        }
    }

    override fun visit(whileStatement: WhileStatement) {
        buffer.append("while(")
        whileStatement.condition.accept(this)
        buffer.append(") {")
        whileStatement.body.accept(this)
        buffer.append('}')
    }

    override fun visit(forStatement: ForStatement) {
        buffer.append("for(")

        forStatement.init?.accept(this)
        if (forStatement.init is EmptyStatement) {
            buffer.append(';')
        }
        forStatement.condition.accept(this)
        buffer.append(';')
        forStatement.update.accept(this)
        buffer.append(") {")
        forStatement.body.accept(this)
        buffer.append('}')
    }

    override fun visit(doWhileStatement: DoWhileStatement) {
        buffer.append("do {")
        doWhileStatement.body.accept(this)
        buffer.append("} while(")
        doWhileStatement.condition.accept(this)
        buffer.append(");")
    }

    override fun visit(caseStatement: CaseStatement) {
        buffer.append("case ")
        caseStatement.constExpression.accept(this)
        buffer.append(": ")
        caseStatement.stmt.accept(this)
    }

    override fun visit(defaultStatement: DefaultStatement) {
        buffer.append("default: ")
        defaultStatement.stmt.accept(this)
    }

    override fun visit(breakStatement: BreakStatement) {
        buffer.append("break;")
    }

    override fun visit(continueStatement: ContinueStatement) {
        buffer.append("continue;")
    }

    override fun visit(gotoStatement: GotoStatement) {
        buffer.append("goto ")
        buffer.append(gotoStatement.id.str())
        buffer.append(';')
    }
    override fun visit(labeledStatement: LabeledStatement) {
        buffer.append(labeledStatement.label.str())
        buffer.append(": ")
        labeledStatement.stmt.accept(this)
    }

    override fun visit(compoundStatement: CompoundStatement) {
        joinTo(compoundStatement.statements, " ") {
            it.accept(this)
        }
    }

    override fun visit(exprStatement: ExprStatement) {
        exprStatement.expr.accept(this)
    }

    override fun visit(functionNode: FunctionNode) {
        functionNode.specifier.accept(this)
        buffer.append(' ')
        functionNode.declarator.accept(this)
        buffer.append(" {")
        functionNode.body.accept(this)
        buffer.append('}')
    }

    override fun visit(parameter: Parameter) {
        parameter.declspec.accept(this)
        buffer.append(' ')
        parameter.declarator.accept(this)
    }

    override fun visit(initDeclarator: InitDeclarator) {
        initDeclarator.declarator.accept(this)
        buffer.append(" = ")
        initDeclarator.rvalue.accept(this)
    }

    override fun visit(parameters: ParameterTypeList) {
        buffer.append('(')
        joinTo(parameters.params, ", ") {
            it.accept(this)
        }
        buffer.append(')')
    }

    override fun visit(functionPointer: FunctionPointerDeclarator) {
        buffer.append("(")
        functionPointer.declarator.accept(this)
        buffer.append(")")
    }

    override fun visit(conditional: Conditional) {
        conditional.cond.accept(this)
        buffer.append("? ")
        conditional.eTrue.accept(this)
        buffer.append(" : ")
        conditional.eFalse.accept(this)
    }

    override fun visit(memberAccess: MemberAccess) {
        memberAccess.primary.accept(this)
        buffer.append('.')
        buffer.append(memberAccess.ident.str())
    }

    override fun visit(initializerList: InitializerList) {
        buffer.append('{')
        joinTo(initializerList.initializers, ", ") {
            it.accept(this)
        }
        buffer.append('}')
    }

    override fun visit(designationInitializer: DesignationInitializer) {
        visit(designationInitializer.designation)
        buffer.append(" = ")
        designationInitializer.initializer.accept(this)
    }

    override fun visit(singleInitializer: SingleInitializer) {
        singleInitializer.expr.accept(this)
    }

    override fun visit(builtin: BuiltinVaArg) {
        buffer.append("__builtin_va_arg")
        buffer.append('(')
        builtin.assign.accept(this)
        buffer.append(',')
        builtin.typeName.accept(this)
        buffer.append(')')
    }

    override fun visit(builtin: BuiltinVaStart) {
        buffer.append("__builtin_va_start")
        buffer.append('(')
        builtin.vaList.accept(this)
        buffer.append(',')
        builtin.param.accept(this)
        buffer.append(')')
    }

    override fun visit(builtin: BuiltinVaEnd) {
        buffer.append("__builtin_va_end")
        buffer.append('(')
        builtin.vaList.accept(this)
        buffer.append(')')
    }

    override fun visit(builtin: BuiltinVaCopy) {
        buffer.append("__builtin_va_copy")
        buffer.append('(')
        builtin.dest.accept(this)
        buffer.append(',')
        builtin.src.accept(this)
        buffer.append(')')
    }

    override fun visit(arrayDeclarator: ArrayDeclarator) {
        buffer.append('[')
        arrayDeclarator.constexpr.accept(this)
        buffer.append(']')
    }

    override fun visit(typeName: TypeName) {
        typeName.specifiers.accept(this)
        typeName.abstractDecl?.accept(this)
    }

    override fun visit(identifierList: IdentifierList) {
        buffer.append('(')
        joinTo(identifierList.list, ", ") {
            it.accept(this)
        }
        buffer.append(')')
    }

    override fun visit(abstractDeclarator: AbstractDeclarator) {
        if (abstractDeclarator.directAbstractDeclarators == null) {
            return
        }

        joinTo(abstractDeclarator.directAbstractDeclarators, " ") {
            it.accept(this)
        }
    }

    override fun visit(specifierType: DeclarationSpecifier) {
        joinTo(specifierType.specifiers, " ") {
            it.accept(this)
        }
    }

    override fun visit(functionCall: FunctionCall) {
        functionCall.primary.accept(this)
        buffer.append('(')
        joinTo(functionCall.args, ", ") {
            it.accept(this)
        }
        buffer.append(')')
    }

    fun visit(structField: StructField) {
        structField.declspec.accept(this)
        buffer.append(' ')
        joinTo(structField.declarators, ", ") {
            it.accept(this)
        }
        buffer.append(';')
    }

    override fun visit(structSpecifier: StructSpecifier) {
        buffer.append("struct ")
        buffer.append(structSpecifier.name())
        buffer.append(" {")
        joinTo(structSpecifier.fields, " ") {
            visit(it)
        }
        buffer.append('}')
    }

    override fun visit(structDeclaration: StructDeclaration) {
        buffer.append("struct ")
        buffer.append(structDeclaration.name())
    }

    override fun visit(unionSpecifier: UnionSpecifier) {
        buffer.append("union")
        buffer.append(' ')
        buffer.append(unionSpecifier.name.str())

        buffer.append(" {")
        joinTo(unionSpecifier.fields, " ") {
            visit(it)
        }
        buffer.append('}')
    }

    override fun visit(unionDeclaration: UnionDeclaration) {
        buffer.append("union ")
        buffer.append(unionDeclaration.name.str())
        buffer.append(';')
    }

    override fun visit(typeNode: TypeNode) {
        buffer.append(typeNode.name())
    }

    override fun visit(enumSpecifier: EnumSpecifier) {
        buffer.append("enum ")
        buffer.append(enumSpecifier.name())
        buffer.append(" {")
        joinTo(enumSpecifier.enumerators, ", ") {
            buffer.append(it.ident.str())
            if (it.constExpr !is EmptyExpression) {
                buffer.append(" = ")
                it.constExpr.accept(this)
            }
        }
        buffer.append('}')
    }

    override fun visit(enumDeclaration: EnumDeclaration) {
        buffer.append("enum ")
        buffer.append(enumDeclaration.name())
        buffer.append(';')
    }

    override fun visit(functionSpecifierNode: FunctionSpecifierNode) {
        buffer.append(functionSpecifierNode.name())
    }

    override fun visit(varNode: VarNode) {
        buffer.append(varNode.name())
    }

    override fun visit(arrowMemberAccess: ArrowMemberAccess) {
        arrowMemberAccess.primary.accept(this)
        buffer.append("->")
        buffer.append(arrowMemberAccess.fieldName())
    }

    override fun visit(parameterVarArg: ParameterVarArg) {
        buffer.append("...")
    }

    override fun visit(directDeclarator: DirectDeclarator) {
        directDeclarator.decl.accept(this)
        joinTo(directDeclarator.directDeclaratorParams, "") {
            it.accept(this)
        }
    }

    fun visit(designation: Designation) {
        joinTo(designation.designators, "") {
            when (it) {
                is ArrayDesignator -> visit(it)
                is MemberDesignator -> visit(it)
            }
        }
    }

    fun visit(arrayDesignator: ArrayDesignator) {
        buffer.append('[')
        arrayDesignator.constExpression.accept(this)
        buffer.append(']')
    }

    fun visit(memberDesignator: MemberDesignator) {
        buffer.append('.')
        buffer.append(memberDesignator.name.str())
    }

    override fun visit(varDecl: DirectVarDeclarator) {
        buffer.append(varDecl.name())
    }

    override fun visit(emptyExpression: EmptyExpression) {

    }

    override fun visit(emptyDeclarator: EmptyDeclarator) {

    }

    override fun visit(typeQualifier: TypeQualifierNode) {
        buffer.append(typeQualifier.name())
    }

    override fun visit(storageClassSpecifier: StorageClassSpecifier) {
        buffer.append(storageClassSpecifier.name())
    }

    override fun visit(structDeclarator: StructDeclarator) {
        structDeclarator.declarator.accept(this)

        if (structDeclarator.expr !is EmptyExpression) {
            buffer.append(':')
            structDeclarator.expr.accept(this)
        }
    }

    override fun visit(emptyStatement: EmptyStatement) {
    }

    override fun visit(binop: BinaryOp) {
        binop.left.accept(this)
        buffer.append(' ').append(binop.opType).append(' ')
        binop.right.accept(this)
        if (binop.opType == BinaryOpType.ASSIGN) {
            buffer.append(';')
        }
    }

    fun visit(nodePointer: NodePointer) {
        buffer.append('*')
        for (qualifier in nodePointer.qualifiers) {
            buffer.append(qualifier)
            buffer.append(' ')
        }
    }

    companion object {
        fun print(expr: Node): String {
            val astPrinter = LineAgnosticAstPrinter()
            expr.accept(astPrinter)
            return astPrinter.buffer.toString()
        }

        fun print(programNode: ProgramNode): String {
            val astPrinter = LineAgnosticAstPrinter()
            for ((idx, node) in programNode.nodes.withIndex()) {
                node.accept(astPrinter)
                if (idx != programNode.nodes.size - 1) {
                    astPrinter.buffer.append(' ')
                }
            }
            return astPrinter.buffer.toString()
        }
    }
}