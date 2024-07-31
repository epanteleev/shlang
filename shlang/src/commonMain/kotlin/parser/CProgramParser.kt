package parser

import common.assertion
import tokenizer.*
import parser.nodes.*


data class ParserException(val info: ProgramMessage) : Exception(info.toString())

// Parser for C language
// Based on C11 standard
// https://port70.net/~nsz/c/c11/n1570.html
//
// Grammar:
// https://cs.wmich.edu/~gupta/teaching/cs4850/sumII06/The%20syntax%20of%20C%20in%20Backus-Naur%20form.htm
//
class CProgramParser private constructor(filename: String, iterator: TokenList): AbstractCParser(filename, iterator) {
    // translation_unit
    //	: external_declaration
    //	| translation_unit external_declaration
    //	;
    fun translation_unit(): ProgramNode {
        val nodes = mutableListOf<Node>()
        while (!eof()) {
            val node = external_declaration()?:
                throw ParserException(InvalidToken("Expected external declaration", peak()))
            nodes.add(node)
        }

        return ProgramNode(nodes)
    }

    //  function-definition:
    //    : declaration-specifiers declarator declaration-list? compound-statement
    //    ;
    //
    //  declaration-list:
    //    : declaration
    //    | declaration-list declaration
    //    ;
    fun function_definition(): FunctionNode? = funcRule {
        val declspec = declaration_specifiers()?: return@funcRule null
        val declarator = declarator()?: return@funcRule null
        val declarations = mutableListOf<Declaration>() //TODO just skip it temporarily
        while (true) {
            val declaration = declaration()?: break
            declarations.add(declaration)
        }
        assertion(declarations.isEmpty()) { "Declaration list is not supported yet" }
        val body = compound_statement() ?: let {
            throw ParserException(InvalidToken("Expected compound statement", peak()))
        }
        return@funcRule FunctionNode(declspec, declarator, body)
    }

    // 6.8 Statements and blocks
    //
    // <statement> ::= <labeled-statement>
    //              | <expression-statement>
    //              | <compound-statement>
    //              | <selection-statement>
    //              | <iteration-statement>
    //              | <jump-statement>
    fun statement(): Statement? = rule {
        return@rule labeled_statement() ?:
        expression_statement() ?:
        compound_statement() ?:
        selection_statement() ?:
        iteration_statement() ?:
        jump_statement()
    }

    // <jump-statement> ::= goto <identifier> ;
    //                   | continue ;
    //                   | break ;
    //                   | return {<expression>}? ;
    fun jump_statement(): Statement? = rule {
        if (check("goto")) {
            eat()
            val ident = peak<Identifier>()
            eat()
            if (check(";")) {
                eat()
                return@rule labelResolver.addGoto(GotoStatement(ident))
            }
            throw ParserException(InvalidToken("Expected ';'", peak()))
        }
        if (check("continue")) {
            eat()
            if (check(";")) {
                eat()
                return@rule ContinueStatement
            }
            throw ParserException(InvalidToken("Expected ';'", peak()))
        }
        if (check("break")) {
            eat()
            if (check(";")) {
                eat()
                return@rule BreakStatement
            }
            throw ParserException(InvalidToken("Expected ';'", peak()))
        }
        if (check("return")) {
            eat()
            val expr = expression()
            if (check(";")) {
                eat()
                return@rule ReturnStatement(expr ?: EmptyExpression)
            }
            throw ParserException(InvalidToken("Expected ';'", peak()))
        }
        return@rule null
    }

    // 6.8.1 Labeled statements
    //
    // <labeled-statement> ::= <identifier> : <statement>
    //                      | case <constant-expression> : <statement>
    //                      | default : <statement>
    fun labeled_statement(): Statement? = rule {
        if (check<Identifier>() && checkOffset(1, ":")) {
            val ident = peak<Identifier>()
            eat()
            eat()
            val stmt = statement() ?: throw ParserException(InvalidToken("Expected statement", peak()))
            return@rule labelResolver.addLabel(LabeledStatement(ident, stmt))
        }
        if (check("case")) {
            eat()
            val expr = constant_expression() ?: throw ParserException(InvalidToken("Expected constant expression", peak()))
            if (!check(":")) {
                throw ParserException(InvalidToken("Expected ':'", peak()))
            }
            eat()
            val stmt = statement() ?: throw ParserException(InvalidToken("Expected statement", peak()))
            return@rule CaseStatement(expr, stmt)
        }
        if (check("default")) {
            eat()
            if (!check(":")) {
                throw ParserException(InvalidToken("Expected ':'", peak()))
            }
            eat()
            val stmt = statement() ?: throw ParserException(InvalidToken("Expected statement", peak()))
            return@rule DefaultStatement(stmt)
        }
        return@rule null
    }

    // 6.8.3 Expression and null statements
    //
    // <expression-statement> ::= {<expression>}? ;
    fun expression_statement(): Statement? = rule {
        val expr = expression()
        if (expr == null) {
            if (check(";")) {
                eat()
                return@rule EmptyStatement
            }
            return@rule null
        }
        if (check(";")) {
            eat()
            return@rule ExprStatement(expr)
        }
        throw ParserException(InvalidToken("Expected ';'", peak()))
    }

    // <selection-statement> ::= if ( <expression> ) <statement>
    //                        | if ( <expression> ) <statement> else <statement>
    //                        | switch ( <expression> ) <statement>
    fun selection_statement(): Statement? = rule {
        if (check("if")) {
            eat()
            if (!check("(")) {
                throw ParserException(InvalidToken("Expected '('", peak()))
            }
            eat()
            val expr = expression() ?: throw ParserException(InvalidToken("Expected expression", peak()))
            if (!check(")")) {
                throw ParserException(InvalidToken("Expected ')'", peak()))
            }
            eat()
            val then = statement() ?: throw ParserException(InvalidToken("Expected statement", peak()))
            if (!check("else")) {
                return@rule IfStatement(expr, then, EmptyStatement)
            }
            eat()
            val els = statement() ?: throw ParserException(InvalidToken("Expected statement", peak()))
            return@rule IfStatement(expr, then, els)
        }
        if (check("switch")) {
            eat()
            if (!check("(")) {
                throw ParserException(InvalidToken("Expected '('", peak()))
            }
            eat()
            val expr = expression() ?: throw ParserException(InvalidToken("Expected expression", peak()))
            if (!check(")")) {
                throw ParserException(InvalidToken("Expected ')'", peak()))
            }
            eat()
            val stmt = statement() ?: throw ParserException(InvalidToken("Expected statement", peak()))
            return@rule SwitchStatement(expr, stmt)
        }
        return@rule null
    }


    // <iteration-statement> ::= while ( <expression> ) <statement>
    //                        | do <statement> while ( <expression> ) ;
    //                        | for ( {<expression>}? ; {<expression>}? ; {<expression>}? ) <statement>
    fun iteration_statement(): Statement? = rule {
        if (check("while")) {
            eat()
            if (!check("(")) {
                throw ParserException(InvalidToken("Expected '('", peak()))
            }
            eat()
            val condition = conditional_expression() ?: throw ParserException(
                InvalidToken("Expected conditional expression", peak())
            )
            if (!check(")")) {
                throw ParserException(InvalidToken("Expected ')'", peak()))
            }
            eat()
            val body = statement() ?: throw ParserException(InvalidToken("Expected statement", peak()))
            return WhileStatement(condition, body)
        }
        if (check("do")) {
            eat()
            val body = statement()?: throw ParserException(InvalidToken("Expected statement", peak()))
            if (!check("while")) {
                throw ParserException(InvalidToken("Expected 'while'", peak()))
            }
            eat()
            if (!check("(")) {
                throw ParserException(InvalidToken("Expected '('", peak()))
            }
            eat()
            val condition = conditional_expression()
                ?: throw ParserException(InvalidToken("Expected conditional expression", peak()))
            if (!check(")")) {
                throw ParserException(InvalidToken("Expected ')'", peak()))
            }
            eat()
            if (check(";")) {
                eat()
                return DoWhileStatement(body, condition)
            } else {
                throw ParserException(InvalidToken("Expected ';'", peak()))
            }
        }
        if (check("for")) {
            eat()
            if (!check("(")) {
                throw ParserException(InvalidToken("Expected '('", peak()))
            }
            eat()
            val init = declaration() ?: expression_statement() ?: DummyNode

            val condition = if (!check(";")) {
                expression() ?: throw ParserException(InvalidToken("Expected expression", peak()))
            } else {
                EmptyExpression
            }

            if (!check(";")) {
                throw ParserException(InvalidToken("Expected ';'", peak()))
            }
            eat()

            val update = if (!check(")")) {
                expression() ?: throw ParserException(InvalidToken("Expected expression", peak()))
            } else {
                EmptyExpression
            }
            if (!check(")")) {
                throw ParserException(InvalidToken("Expected ')'", peak()))
            }
            eat()
            val body = statement() ?: throw ParserException(InvalidToken("Expected statement", peak()))
            return ForStatement(init, condition, update, body)
        }
        return@rule null
    }

    // initializer
    //	: assignment_expression
    //	| '{' initializer_list '}'
    //	| '{' initializer_list ',' '}'
    //	;
    fun initializer(): Expression? = rule {
        if (!check("{")) {
            return@rule assignment_expression()
        }
        eat()
        val list = initializer_list()
        if (check("}")) {
            eat()
            return@rule list
        } else {
            throw ParserException(InvalidToken("Expected '}'", peak()))
        }
    }

    // 6.8.2 Compound statement
    //
    //  compound-statement:
    //   : { block-item-list? }
    //   ;
    //  block-item-list:
    //   : block-item
    //   | block-item-list block-item
    //   ;
    //  block-item:
    //   : declaration
    //   | statement
    //   ;
    fun compound_statement(): Statement? = rule {
        if (!check("{")) {
            return@rule null
        }
        eat()
        if (check("}")) {
            eat()
            return@rule EmptyStatement
        }
        val statements = mutableListOf<Node>()
        while (!check("}")) {
            statements.add(declaration()?: statement()?:
                throw ParserException(InvalidToken("Expected declaration or statement", peak())))
        }
        eat()
        return@rule CompoundStatement(statements)
    }

    // expression
    //	: assignment_expression
    //	| expression ',' assignment_expression
    //	;
    fun expression(): Expression? = rule {
        val assign = assignment_expression()?: return@rule null
        if (check(",")) {
            eat()
            val expr = expression()?: throw ParserException(InvalidToken("Expected expression", peak()))
            return@rule BinaryOp(assign, expr, BinaryOpType.COMMA)
        }
        return@rule assign
    }

    // assign    = conditional (assign-op assign)?
    // assign-op = "=" | "+=" | "-=" | "*=" | "/=" | "%=" | "&=" | "|=" | "^="
    //           | "<<=" | ">>="
    fun assignment_expression(): Expression? = rule {
        val cond = conditional_expression()?: return@rule null
        if (check("=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.ASSIGN)
        }
        if (check("+=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.ADD_ASSIGN)
        }
        if (check("-=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.SUB_ASSIGN)
        }
        if (check("*=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.MUL_ASSIGN)
        }
        if (check("/=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.DIV_ASSIGN)
        }
        if (check("%=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.MOD_ASSIGN)
        }
        if (check("&=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.BIT_AND_ASSIGN)
        }
        if (check("|=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.BIT_OR_ASSIGN)
        }
        if (check("^=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.BIT_XOR_ASSIGN)
        }
        if (check("<<=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.SHL_ASSIGN)
        }
        if (check(">>=")) {
            eat()
            val assign = assignment_expression()?: throw ParserException(InvalidToken("Expected assignment expression", peak()))
            return@rule BinaryOp(cond, assign, BinaryOpType.SHR_ASSIGN)
        }
        return@rule cond
    }

    // storage_class_specifier
    //	: TYPEDEF
    //	| EXTERN
    //	| STATIC
    //	| AUTO
    //	| REGISTER
    //	;
    fun storage_class_specifier(): StorageClassSpecifier? = rule {
        if (check("typedef")) {
            val tok = peak<Keyword>()
            eat()
            return@rule StorageClassSpecifier(tok)
        }
        if (check("extern")) {
            val tok = peak<Keyword>()
            eat()
            return@rule StorageClassSpecifier(tok)
        }
        if (check("static")) {
            val tok = peak<Keyword>()
            eat()
            return@rule StorageClassSpecifier(tok)
        }
        if (check("auto")) {
            val tok = peak<Keyword>()
            eat()
            return@rule StorageClassSpecifier(tok)
        }
        if (check("register")) {
            val tok = peak<Keyword>()
            eat()
            return@rule StorageClassSpecifier(tok)
        }
        return@rule null
    }

    //init_declarator = declarator
    //	              | declarator '=' initializer
    //	              ;
    fun init_declarator(): AnyDeclarator? = rule {
        val declarator = declarator()?: return@rule null
        if (check("=")) {
            eat()
            val initializer = initializer()?: throw ParserException(InvalidToken("Expected initializer", peak()))
            return@rule InitDeclarator(declarator, initializer) //TODO
        }
        return@rule declarator
    }

    // init_declarator_list
    //	: init_declarator
    //	| init_declarator_list ',' init_declarator
    //	;
    fun init_declarator_list(): List<AnyDeclarator> {
        val initDeclarators = mutableListOf<AnyDeclarator>()
        while (check<CToken>()) {
            val initDeclarator = init_declarator() ?: return initDeclarators
            initDeclarators.add(initDeclarator)
            if (check(",")) {
                eat()
            } else {
                return initDeclarators
            }
        }
        throw ParserException(InvalidToken("Expected ';'", peak()))
    }

    // declaration
    //	: declaration_specifiers ';'
    //	| declaration_specifiers init_declarator_list ';'
    //	;
    fun declaration(): Declaration? = rule {
        val declarationSpecifiers = declaration_specifiers() ?: return@rule null
        if (check(";")) {
            eat()
            return@rule Declaration(declarationSpecifiers, listOf())
        }
        val initDeclaratorList = init_declarator_list()
        if (check(";")) {
            eat()
            return@rule Declaration(declarationSpecifiers, initDeclaratorList)
        }
        return@rule null
    }

    // parameter_type_list
    //	: parameter_list
    //	| parameter_list ',' '...' <- skip it here
    //	;
    fun parameter_type_list(): List<AnyParameter>? = rule {
        val parameters = parameter_list()
        if (parameters.isEmpty()) {
            return@rule null
        }

        return@rule parameters
    }

    // parameter_list
    //	: parameter_declaration
    //	| parameter_list ',' parameter_declaration
    //	;
    fun parameter_list(): List<AnyParameter> {
        val parameters = mutableListOf<AnyParameter>()
        val param = parameter_declaration()
        if (param != null) {
            parameters.add(param)
        }
        while (true) {
            val parameter = rule {
                if (!check(",")) {
                    return@rule null
                }
                eat()
                if (check("...")) {
                    eat()
                    return@rule ParameterVarArg()
                }
                parameter_declaration() ?: throw ParserException(InvalidToken("Expected parameter declaration", peak()))
            }
            if (parameter == null) {
                return parameters
            }
            parameters.add(parameter)
        }
    }

    // struct_declarator
    //	: declarator
    //	| ':' constant_expression
    //	| declarator ':' constant_expression
    //	;
    fun struct_declarator(): StructDeclarator? = rule {
        if (check(":")) {
            eat()
            val expr = constant_expression() ?: throw ParserException(InvalidToken("Expected constant expression", peak()))
            return@rule StructDeclarator(EmptyDeclarator, expr)
        }
        val declarator = declarator()?: return@rule null
        if (check(":")) {
            eat()
            val expr = constant_expression() ?: throw ParserException(InvalidToken("Expected constant expression", peak()))
            return@rule StructDeclarator(declarator, expr)
        }
        return@rule StructDeclarator(declarator, EmptyExpression)
    }

    // struct_declarator_list
    //	: struct_declarator
    //	| struct_declarator_list ',' struct_declarator
    //	;
    fun struct_declarator_list(): List<StructDeclarator> {
        val declarators = mutableListOf<StructDeclarator>()
        while (true) {
            val declarator = struct_declarator()?: return declarators
            declarators.add(declarator)
            if (check(",")) {
                eat()
            } else {
                return declarators
            }
        }
    }

    // struct_declaration
    //	: specifier_qualifier_list struct_declarator_list ';'
    //	;
    fun struct_declaration(): StructField? = rule {
        val declspec = specifier_qualifier_list()?: return@rule null
        val declarators = struct_declarator_list()
        if (check(";")) {
            eat()
            return@rule StructField(declspec, declarators)
        }
        throw ParserException(InvalidToken("Expected ';'", peak()))
    }

    // struct_declaration_list
    //	: struct_declaration
    //	| struct_declaration_list struct_declaration
    //	;
    fun struct_declaration_list(): List<StructField> {
        val fields = mutableListOf<StructField>()
        while (true) {
            val field = struct_declaration() ?: return fields
            fields.add(field)
            if (check("}")) {
                return fields
            }
        }
    }

    // struct_or_union_specifier
    //	: struct_or_union IDENTIFIER '{' struct_declaration_list '}'
    //	| struct_or_union '{' struct_declaration_list '}'
    //	| struct_or_union IDENTIFIER
    //	;
    fun struct_or_union_specifier(): AnyTypeNode? = rule {
        if (check("struct")) {
            eat()
            if (check("{")) {
                eat()
                val fields = struct_declaration_list()
                if (check("}")) {
                    eat()
                    return@rule StructSpecifier(Identifier.UNKNOWN, fields)
                }
                throw ParserException(InvalidToken("Expected '}'", peak()))
            }
            if (check<Identifier>()) {
                val name = peak<Identifier>()
                eat()
                if (!check("{")) {
                    return@rule StructDeclaration(name)
                }
                eat()
                val fields = struct_declaration_list()
                if (!check("}")) {
                    throw ParserException(InvalidToken("Expected '}'", peak()))
                }
                eat()
                return@rule StructSpecifier(name, fields)
            }
            throw ParserException(InvalidToken("Expected identifier", peak()))
        }
        if (check("union")) {
            eat()
            if (check("{")) {
                eat()
                val fields = struct_declaration_list()
                if (check("}")) {
                    eat()
                    return@rule UnionSpecifier(Identifier.UNKNOWN, fields)
                }
                throw ParserException(InvalidToken("Expected '}'", peak()))
            }
            if (check<Identifier>()) {
                val name = peak<Identifier>()
                eat()
                if (!check("{")) {
                    return@rule UnionDeclaration(name)
                }
                eat()
                val fields = struct_declaration_list()
                if (check("}")) {
                    eat()
                    return@rule UnionSpecifier(name, fields)
                }
                throw ParserException(InvalidToken("Expected '}'", peak()))
            }
            throw ParserException(InvalidToken("Expected identifier", peak()))
        }
        return@rule null
    }

    // enumerator
    //	: IDENTIFIER
    //	| IDENTIFIER '=' constant_expression
    //	;
    fun enumerator(): Enumerator? = rule {
        if (!check<Identifier>()) {
            return@rule null
        }
        val name = peak<Identifier>()
        eat()
        if (check("=")) {
            eat()
            val expr = constant_expression()?: throw ParserException(InvalidToken("Expected constant expression", peak()))
            return@rule Enumerator(name, expr)
        }
        return@rule Enumerator(name, EmptyExpression)
    }

    // enumerator_list
    //	: enumerator
    //	| enumerator_list ',' enumerator
    //	;
    fun enumerator_list(): List<Enumerator> {
        val enumerators = mutableListOf<Enumerator>()
        while (true) {
            val enumerator = enumerator()?: return enumerators
            enumerators.add(enumerator)
            if (check(",")) {
                eat()
            } else {
                return enumerators
            }
        }
    }

    // enum_specifier
    //	: 'enum' '{' enumerator_list '}'
    //	| 'enum' IDENTIFIER '{' enumerator_list '}'
    //	| 'enum' IDENTIFIER
    //	;
    fun enum_specifier(): AnyTypeNode? = rule {
        if (!check("enum")) {
            return@rule null
        }
        eat()
        if (check("{")) {
            eat()
            val enumerators = enumerator_list()
            if (check("}")) {
                eat()
                return@rule EnumSpecifier(Identifier.UNKNOWN, enumerators)
            }
            throw ParserException(InvalidToken("Expected '}'", peak()))
        }
        if (check<Identifier>()) {
            val name = peak<Identifier>()
            eat()
            if (check("{")) {
                eat()
                val enumerators = enumerator_list()
                if (check("}")) {
                    eat()
                    return@rule EnumSpecifier(name, enumerators)
                }
                throw ParserException(InvalidToken("Expected '}'", peak()))
            }
            return@rule EnumDeclaration(name)
        }
        throw ParserException(InvalidToken("Expected identifier", peak()))
    }

    // type_specifier
    //	: void
    //	| char
    //	| short
    //	| int
    //	| long
    //	| float
    //	| double
    //	| signed
    //	| unsigned
    //  | _Bool
    //	| struct_or_union_specifier
    //	| enum_specifier
    //	| TYPE_NAME
    //	;
    fun type_specifier(): AnyTypeNode? = rule {
        if (check("void")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("char")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("short")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("int")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("long")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("float")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("double")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("signed")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("unsigned")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("_Bool")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeNode(tok)
        }
        if (check("__builtin_va_list")) { //TODO hack
            val tok = peak<Identifier>()
            eat()
            return@rule TypeNode(tok)
        }
        val structOrEnum = struct_or_union_specifier() ?: enum_specifier()
        if (structOrEnum != null) {
            return@rule structOrEnum
        }

        if (check<Identifier>()) {
            val tok = peak<Identifier>()
            if (typeHolder.getTypeOrNull(tok.str()) != null) {
                eat()
                return@rule TypeNode(tok)
            }
        }
        return@rule null
    }

    // type_qualifier
    //	: CONST
    //	| VOLATILE
    //  | RESTRICT
    //	;
    fun type_qualifier(): TypeQualifierNode? = rule {
        if (check("const")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeQualifierNode(tok)
        }
        if (check("volatile")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeQualifierNode(tok)
        }
        if (check("restrict")) {
            val tok = peak<Keyword>()
            eat()
            return@rule TypeQualifierNode(tok)
        }
        return@rule null
    }

    // 6.7.4 Function specifiers
    // https://port70.net/~nsz/c/c11/n1570.html#6.7.4
    //
    // function_specifier:
    //  : inline
    //  | _Noreturn
    //  ;
    fun function_specifier(): FunctionSpecifierNode? = rule {
        if (check("inline")) {
            val tok = peak<Keyword>()
            eat()
            return@rule FunctionSpecifierNode(tok)
        }
        if (check("_Noreturn")) {
            val tok = peak<Keyword>()
            eat()
            return@rule FunctionSpecifierNode(tok)
        }
        return@rule null
    }

    // declaration_specifiers
    //	: storage_class_specifier
    //	| storage_class_specifier declaration_specifiers
    //	| type_specifier
    //	| type_specifier declaration_specifiers
    //	| type_qualifier
    //	| type_qualifier declaration_specifiers
    //  | function-specifier
    //  | function-specifier declaration_specifiers
    //	;
    fun declaration_specifiers(): DeclarationSpecifier? = rule {
        val specifiers = mutableListOf<AnyTypeNode>()
        while (true) {
            val storageClass = storage_class_specifier()
            if (storageClass != null) {
                specifiers.add(storageClass)
                continue
            }
            val type = type_specifier()
            if (type != null) {
                specifiers.add(type)
                continue
            }
            val typeQualifier = type_qualifier()
            if (typeQualifier != null) {
                specifiers.add(typeQualifier)
                continue
            }
            val functionSpecifier = function_specifier()
            if (functionSpecifier != null) {
                specifiers.add(functionSpecifier)
                continue
            }
            break
        }

        return@rule if (specifiers.isEmpty()) {
            null
        } else {
            DeclarationSpecifier(specifiers)
        }
    }

    // parameter_declaration
    //	: declaration_specifiers declarator
    //	| declaration_specifiers abstract_declarator
    //	| declaration_specifiers
    //	;
    fun parameter_declaration(): Parameter? = rule {
        val declspec = declaration_specifiers() ?: return@rule null

        val declarator = declarator()
        if (declarator == null) {
            val abstractDeclarator = abstract_declarator()?: return@rule Parameter(declspec, EmptyDeclarator)
            return@rule Parameter(declspec, abstractDeclarator)
        }
        return@rule Parameter(declspec, declarator)
    }

    // direct_declarator
    //	: IDENTIFIER
    //	| '(' declarator ')'
    //	| direct_declarator '[' constant_expression ']'
    //	| direct_declarator '[' ']'
    //	| direct_declarator '(' parameter_type_list ')'
    //	| direct_declarator '(' identifier_list ')'
    //	| direct_declarator '(' ')'
    fun direct_declarator(): DirectDeclarator? = rule {
        fun declarator_list(): List<DirectDeclaratorParam> {
            val declarators = mutableListOf<DirectDeclaratorParam>()
            while (true) {
                if (check("(")) {
                    eat()
                    if (check(")")) {
                        eat()
                        declarators.add(ParameterTypeList(listOf()))
                        continue
                    }
                    val declspec = parameter_type_list()
                    if (declspec != null) {
                        if (check(")")) {
                            eat()
                            declarators.add(ParameterTypeList(declspec))
                            continue
                        }
                        throw ParserException(InvalidToken("Expected ')'", peak()))
                    }
                    val identifiers = identifier_list()
                    if (identifiers != null) {
                        if (check(")")) {
                            eat()
                            declarators.add(identifiers)
                            continue
                        }
                        throw ParserException(InvalidToken("Expected ')'", peak()))
                    }

                    throw ParserException(InvalidToken("Expected ')'", peak()))
                }
                if (check("[")) {
                    eat()
                    if (check("]")) {
                        eat()
                        declarators.add(ArrayDeclarator(EmptyExpression))
                        continue
                    }
                    val size = constant_expression()?: throw ParserException(InvalidToken("Expected constant expression", peak()))
                    if (check("]")) {
                        eat()
                        declarators.add(ArrayDeclarator(size))
                        continue
                    }
                    throw ParserException(InvalidToken("Expected ']'", peak()))
                }
                break
            }
            return declarators
        }

        if (check("(")) {
            eat()
            val declarator = declarator()
            if (declarator != null) {
                if (check(")")) {
                    eat()
                    val declarators = declarator_list()
                    return@rule DirectDeclarator(FunctionPointerDeclarator(declarator), declarators)
                }
                throw ParserException(InvalidToken("Expected ')'", peak()))
            }
        }
        if (check<Identifier>()) {
            val ident = peak<Identifier>()
            eat()
            return@rule DirectDeclarator(DirectVarDeclarator(ident), declarator_list())
        }
        return@rule null
    }

    // constant_expression
    //	: conditional_expression
    //	;
    fun constant_expression(): Expression? = rule {
        return@rule conditional_expression()
    }

    // identifier_list
    //	: IDENTIFIER
    //	| identifier_list ',' IDENTIFIER
    //	;
    fun identifier_list(): IndentifierList? = rule<IndentifierList> {
        val identifiers = mutableListOf<IdentNode>()
        while (true) {
            if (!check<Identifier>()) {
                return@rule null
            }
            val ident = peak<Identifier>()
            eat()
            identifiers.add(IdentNode(ident))
            if (check(",")) {
                eat()
            } else {
                break
            }
        }
        if (identifiers.isEmpty()) {
            return@rule null
        } else {
            return@rule IndentifierList(identifiers)
        }
    }

    // pointer
    //	: '*'
    //	| '*' type_qualifier_list
    //	| '*' pointer
    //	| '*' type_qualifier_list pointer
    //	;
    //
    fun pointer(): List<NodePointer>? = rule {
        val pointers = mutableListOf<NodePointer>()
        while (check("*")) {
            eat()
            val qualifiers = type_qualifier_list()
            pointers.add(NodePointer(qualifiers))
        }

        return@rule if (pointers.isEmpty()) {
            null
        } else {
            pointers
        }
    }

    // type_qualifier_list
    //	: type_qualifier
    //	| type_qualifier_list type_qualifier
    //	;
    fun type_qualifier_list(): List<TypeQualifierNode> {
        val qualifiers = mutableListOf<TypeQualifierNode>()
        while (true) {
            val qualifier = type_qualifier()?: break
            qualifiers.add(qualifier)
        }
        return qualifiers
    }

    // declarator
    //	: pointer direct_declarator
    //	| direct_declarator
    //	;
    fun declarator(): Declarator? = rule {
        val pointers = pointer()
        if (pointers != null) {
            val directDeclarator = direct_declarator()?: return@rule null
            return@rule Declarator(directDeclarator, pointers)
        }
        val directDeclarator = direct_declarator()?: return@rule null
        return@rule Declarator(directDeclarator, listOf())
    }

    // direct_abstract_declarator
    //	: '(' abstract_declarator ')'
    //	| '[' ']'
    //	| '[' constant_expression ']'
    //	| direct_abstract_declarator '[' ']'
    //	| direct_abstract_declarator '[' constant_expression ']'
    //	| '(' ')'
    //	| '(' parameter_type_list ')'
    //	| direct_abstract_declarator '(' ')'
    //	| direct_abstract_declarator '(' parameter_type_list ')'
    //	;
    fun direct_abstract_declarator(): List<DirectDeclaratorParam>? = rule {
        if (!check("(") && !check("[")) {
            return@rule null
        }

        val abstractDeclarators = mutableListOf<DirectDeclaratorParam>()
        while (true) {
            if (check("(")) {
                eat()
                if (check(")")) {
                    eat()
                    abstractDeclarators.add(ParameterTypeList(listOf()))
                    continue
                }

                val parameters = parameter_type_list()
                if (parameters != null) {
                    if (check(")")) {
                        eat()
                        abstractDeclarators.add(ParameterTypeList(parameters))
                        continue
                    }
                    throw ParserException(InvalidToken("Expected ')'", peak()))
                }
                throw ParserException(InvalidToken("Expected ')'", peak()))
            }
            if (check("[")) {
                eat()
                if (check("]")) {
                    eat()
                    abstractDeclarators.add(ArrayDeclarator(EmptyExpression))
                    continue
                }
                val size = constant_expression()?: throw ParserException(InvalidToken("Expected constant expression", peak()))
                if (check("]")) {
                    eat()
                    abstractDeclarators.add(ArrayDeclarator(size))
                    continue
                }
                throw ParserException(InvalidToken("Expected ']'", peak()))
            }
            break
        }
        return@rule abstractDeclarators
    }

    // conditional_expression
    //	: logical_or_expression
    //	| logical_or_expression '?' expression ':' conditional_expression
    //	;
    fun conditional_expression(): Expression? = rule {
        var logor = logical_or_expression()?: return@rule null
        while (true) {
            if (check("?")) {
                eat()
                val then = expression()?: throw ParserException(InvalidToken("Expected expression", peak()))
                if (!check(":")) {
                    throw ParserException(InvalidToken("Expected ':'", peak()))
                }
                eat()
                val els = conditional_expression()?: throw ParserException(InvalidToken("Expected conditional expression", peak()))
                logor = Conditional(logor, then, els)
                continue
            }
            break
        }

        return@rule logor
    }

    // logical_or_expression
    //	: logical_and_expression
    //	| logical_or_expression '||' logical_and_expression
    //	;
    fun logical_or_expression(): Expression? = rule {
        var logand = logical_and_expression()?: return@rule null
        while (true) {
            if (check("||")) {
                eat()
                val bitor = logical_and_expression()?: throw ParserException(InvalidToken("Expected or expression", peak()))
                logand = BinaryOp(logand, bitor, BinaryOpType.OR)
                continue
            }
            break
        }
        return@rule logand
    }

    // logical_and_expression
    //	: inclusive_or_expression
    //	| logical_and_expression '&&' inclusive_or_expression
    //	;
    fun logical_and_expression(): Expression? = rule {
        var bitor = inclusive_or_expression()?: return@rule null
        while (true) {
            if (check("&&")) {
                eat()
                val bitand = inclusive_or_expression()?: throw ParserException(InvalidToken("Expected and expression", peak()))
                bitor = BinaryOp(bitor, bitand, BinaryOpType.AND)
                continue
            }
            break
        }
        return@rule bitor
    }

    // inclusive_or_expression
    //	: exclusive_or_expression
    //	| inclusive_or_expression '|' exclusive_or_expression
    //	;
    fun inclusive_or_expression(): Expression? = rule {
        var bitxor = exclusive_or_expression()?: return@rule null
        while (true) {
            if (check("|")) {
                eat()
                val bitor = exclusive_or_expression()?: throw ParserException(InvalidToken("Expected inclusive expression", peak()))
                bitxor = BinaryOp(bitxor, bitor, BinaryOpType.BIT_OR)
                continue
            }
            break
        }
        return@rule bitxor
    }

    // exclusive_or_expression
    //	: and_expression
    //	| exclusive_or_expression '^' and_expression
    //	;
    fun exclusive_or_expression(): Expression? = rule {
        var bitand = and_expression()?: return@rule null
        while (true) {
            if (check("^")) {
                eat()
                val xor = and_expression()?: throw ParserException(InvalidToken("Expected exclusive expression", peak()))
                bitand = BinaryOp(bitand, xor, BinaryOpType.BIT_XOR)
                continue
            }
            break
        }
        return@rule bitand
    }

    // and_expression
    //	: equality_expression
    //	| and_expression '&' equality_expression
    //	;
    fun and_expression(): Expression? = rule {
        var equality = equality_expression()?: return@rule null
        while (true) {
            if (check("&")) {
                eat()
                val bitand = equality_expression()?: throw ParserException(InvalidToken("Expected and expression", peak()))
                equality = BinaryOp(equality, bitand, BinaryOpType.BIT_AND)
                continue
            }
            break
        }
        return@rule equality
    }

    // equality_expression
    //	: relational_expression
    //	| equality_expression '==' relational_expression
    //	| equality_expression '!=' relational_expression
    //	;
    fun equality_expression(): Expression? = rule {
        var relational = relational_expression()?: return@rule null
        while (true) {
            if (check("==")) {
                eat()
                val equal = relational_expression()?: throw ParserException(InvalidToken("Expected relational expression", peak()))
                relational = BinaryOp(relational, equal, BinaryOpType.EQ)
                continue
            }
            if (check("!=")) {
                eat()
                val notEqual = relational_expression()?: throw ParserException(InvalidToken("Expected relational expression", peak()))
                relational = BinaryOp(relational, notEqual, BinaryOpType.NE)
                continue
            }
            break
        }
        return@rule relational
    }

    // relational_expression
    //	: shift_expression
    //	| relational_expression '<' shift_expression
    //	| relational_expression '>' shift_expression
    //	| relational_expression '<=' shift_expression
    //	| relational_expression '>=' shift_expression
    //	;
    fun relational_expression(): Expression? = rule {
        var shift = shift_expression()?: return@rule null
        while (true) {
            if (check("<")) {
                eat()
                val less = shift_expression()?: throw ParserException(InvalidToken("Expected shift expression", peak()))
                shift = BinaryOp(shift, less, BinaryOpType.LT)
                continue
            }
            if (check(">")) {
                eat()
                val greater = shift_expression()?: throw ParserException(InvalidToken("Expected shift expression", peak()))
                shift = BinaryOp(shift, greater, BinaryOpType.GT)
                continue
            }
            if (check("<=")) {
                eat()
                val lessEq = shift_expression()?: throw ParserException(InvalidToken("Expected shift expression", peak()))
                shift = BinaryOp(shift, lessEq, BinaryOpType.LE)
                continue
            }
            if (check(">=")) {
                eat()
                val greaterEq = shift_expression()?: throw ParserException(InvalidToken("Expected shift expression", peak()))
                shift = BinaryOp(shift, greaterEq, BinaryOpType.GE)
                continue
            }
            break
        }
        return@rule shift
    }

    // shift_expression
    //	: additive_expression
    //	| shift_expression '<<' additive_expression
    //	| shift_expression '>>' additive_expression
    //	;
    fun shift_expression(): Expression? = rule {
        var additive = additive_expression()?: return@rule null
        while (true) {
            if (check("<<")) {
                eat()
                val shift = additive_expression()?: throw ParserException(InvalidToken("Expected shift expression", peak()))
                additive = BinaryOp(additive, shift, BinaryOpType.SHL)
                continue
            }
            if (check(">>")) {
                eat()
                val shift = additive_expression()?: throw ParserException(InvalidToken("Expected shift expression", peak()))
                additive = BinaryOp(additive, shift, BinaryOpType.SHR)
                continue
            }
            break
        }
        return@rule additive
    }

    // additive_expression
    //	: multiplicative_expression
    //	| additive_expression '+' multiplicative_expression
    //	| additive_expression '-' multiplicative_expression
    //	;
    fun additive_expression(): Expression? = rule {
        var mult = multiplicative_expression()?: return@rule null
        while (true) {
            if (check("+")) {
                eat()
                val add = multiplicative_expression()?: throw ParserException(InvalidToken("Expected additive expression", peak()))
                mult = BinaryOp(mult, add, BinaryOpType.ADD)
                continue
            }
            if (check("-")) {
                eat()
                val add = multiplicative_expression()?: throw ParserException(InvalidToken("Expected additive expression", peak()))
                mult = BinaryOp(mult, add, BinaryOpType.SUB)
                continue
            }
            break
        }
        return@rule mult
    }

    // multiplicative_expression
    //	: cast_expression
    //	| multiplicative_expression '*' cast_expression
    //	| multiplicative_expression '/' cast_expression
    //	| multiplicative_expression '%' cast_expression
    //	;
    fun multiplicative_expression(): Expression? = rule {
        var cast = cast_expression() ?: return@rule null
        while (true) {
            if (check("*")) {
                eat()
                val mul = cast_expression() ?: throw ParserException(InvalidToken("Expected multiplicative expression", peak()))
                cast = BinaryOp(cast, mul, BinaryOpType.MUL)
                continue
            }
            if (check("/")) {
                eat()
                val mul = cast_expression() ?: throw ParserException(InvalidToken("Expected multiplicative expression", peak()))
                cast = BinaryOp(cast, mul, BinaryOpType.DIV)
                continue
            }
            if (check("%")) {
                eat()
                val mul = cast_expression() ?: throw ParserException(InvalidToken("Expected multiplicative expression", peak()))
                cast = BinaryOp(cast, mul, BinaryOpType.MOD)
                continue
            }
            break
        }
        return@rule cast
    }

    // cast_expression
    //	: unary_expression
    //	| '(' type_name ')' cast_expression
    //	;
    fun cast_expression(): Expression? = rule {
        val cast = rule castRule@ {
            if (!check("(")) {
                return@castRule null
            }
            eat()
            val declspec = type_name() ?: return@castRule null
            if (check(")")) {
                eat()
                val cast = cast_expression() ?: throw ParserException(InvalidToken("Expected cast expression", peak()))
                return@castRule Cast(declspec, cast)
            }
            throw ParserException(InvalidToken("Expected ')'", peak()))
        }

        return@rule cast?: unary_expression()
    }

    // type_name
    //	: specifier_qualifier_list
    //	| specifier_qualifier_list abstract_declarator
    //	;
    fun type_name(): TypeName? = rule {
        val specifierQualifierList = specifier_qualifier_list()?: return@rule null
        val abstractDeclarator = abstract_declarator()
        return@rule TypeName(specifierQualifierList, abstractDeclarator)
    }

    // specifier_qualifier_list
    //	: type_specifier specifier_qualifier_list
    //	| type_specifier
    //	| type_qualifier specifier_qualifier_list
    //	| type_qualifier
    //	;
    fun specifier_qualifier_list(): DeclarationSpecifier? = rule {
        val specifiers = mutableListOf<AnyTypeNode>()
        while (true) {
            val type = type_specifier()
            if (type != null) {
                specifiers.add(type)
                continue
            }
            val typeQualifier = type_qualifier()
            if (typeQualifier != null) {
                specifiers.add(typeQualifier)
                continue
            }
            break
        }
        return@rule if (specifiers.isEmpty()) {
            null
        } else {
            DeclarationSpecifier(specifiers)
        }
    }

    // unary_expression
    //	: postfix_expression
    //	| '++' unary_expression
    //	| '--' unary_expression
    //	| unary_operator cast_expression
    //	| sizeof unary_expression
    //	| sizeof '(' type_name ')'
    //	;
    fun unary_expression(): Expression? = rule {
        if (check("sizeof")) {
            eat()
            if (check("(")) {
                eat()
                val type = type_name()?: unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
                if (check(")")) {
                    eat()
                    return@rule SizeOf(type)
                } else {
                    throw ParserException(InvalidToken("Expected ')'", peak()))
                }
            }
            val expr = unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
            return@rule SizeOf(expr)
        }
        if (check("++")) {
            eat()
            val unary = unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
            return@rule UnaryOp(unary, PrefixUnaryOpType.INC)
        }
        if (check("--")) {
            eat()
            val unary = unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
            return@rule UnaryOp(unary, PrefixUnaryOpType.DEC)
        }
        if (check("&")) {
            eat()
            val unary = unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
            return@rule UnaryOp(unary, PrefixUnaryOpType.ADDRESS)
        }
        if (check("*")) {
            eat()
            val unary = unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
            return@rule UnaryOp(unary, PrefixUnaryOpType.DEREF)
        }
        if (check("+")) {
            eat()
            val unary = unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
            return@rule UnaryOp(unary, PrefixUnaryOpType.PLUS)
        }
        if (check("-")) {
            eat()
            val unary = unary_expression()?: let {
                throw ParserException(InvalidToken("Expected unary expression", peak()))
            }
            return@rule UnaryOp(unary, PrefixUnaryOpType.NEG)
        }
        if (check("~")) {
            eat()
            val unary = unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
            return@rule UnaryOp(unary, PrefixUnaryOpType.BIT_NOT)
        }
        if (check("!")) {
            eat()
            val unary = unary_expression()?: throw ParserException(InvalidToken("Expected unary expression", peak()))
            return@rule UnaryOp(unary, PrefixUnaryOpType.NOT)
        }
        return@rule postfix_expression()
    }

    // postfix_expression
    //	: primary_expression
    //	| postfix_expression '[' expression ']'
    //	| postfix_expression '(' ')'
    //	| postfix_expression '(' argument_expression_list ')'
    //	| postfix_expression '.' IDENTIFIER
    //	| postfix_expression '->' IDENTIFIER
    //	| postfix_expression '--'
    //	| postfix_expression '++'
    //	;
    fun postfix_expression(): Expression? = rule {
        val primary_expr: Expression = primary_expression() ?: return@rule null
        var primary = primary_expr
        while (!eof()) {
            val token = peak<CToken>()
            if (token.str() == "[") {
                eat()
                val expr = expression() ?: throw ParserException(InvalidToken("Expected expression", peak()))
                if (check("]")) {
                    eat()
                    primary = ArrayAccess(primary, expr)
                } else {
                    throw ParserException(InvalidToken("Expected ']'", peak()))
                }
            } else if (token.str() == "(") {
                eat()
                val args = argument_expression_list()
                if (check(")")) {
                    eat()
                    primary = FunctionCall(primary, args)
                } else {
                    throw ParserException(InvalidToken("Expected ')'", peak()))
                }
            }
            else if (token.str() == ".") {
                eat()
                val ident = peak<Identifier>()
                eat()
                primary = MemberAccess(primary, ident)
            } else if (token.str() == "->") {
                eat()
                val ident = peak<Identifier>()
                eat()
                primary = ArrowMemberAccess(primary, ident)
            } else if (token.str() == "++") {
                eat()
                primary = UnaryOp(primary, PostfixUnaryOpType.INC)
            } else if (token.str() == "--") {
                eat()
                primary = UnaryOp(primary, PostfixUnaryOpType.DEC)
            } else {
                break
            }
        }

        return@rule primary
    }

    // argument_expression_list
    //	: assignment_expression
    //	| argument_expression_list ',' assignment_expression
    //	;
    fun argument_expression_list(): List<Expression> {
        val arguments = mutableListOf<Expression>()
        while (true) {
            val expr = assignment_expression()?: return arguments
            arguments.add(expr)
            if (check(",")) {
                eat()
            } else {
                return arguments
            }
        }
    }

    //initializer_list
    //	: initializer
    //  | designation initializer
    //	| initializer_list ',' initializer
    //	;
    fun initializer_list(): InitializerList? = rule {
        val initializers = mutableListOf<Initializer>()
        while (true) {
            val designator = designation()
            val initializer = initializer() ?: throw ParserException(InvalidToken("Expected initializer", peak()))
            if (designator != null) {
                initializers.add(DesignationInitializer(designator, initializer))
            } else {
                initializers.add(SingleInitializer(initializer))
            }
            if (check(",")) {
                eat()
            } else {
                return@rule break
            }
        }
        if (initializers.isEmpty()) {
            return@rule null
        } else {
            return@rule InitializerList(initializers)
        }
    }

    // designation
    //	: designator_list '='
    //	;
    fun designation(): Designation? = rule {
        val designators = designator_list() ?: return@rule null
        if (check("=")) {
            eat()
            return Designation(designators)
        }
        throw ParserException(InvalidToken("Expected '='", peak()))
    }

    // designator-list
    //	: designator
    //	| designator-list designator
    //	;
    fun designator_list(): List<Designator>? = rule {
        val designators = mutableListOf<Designator>()
        while (true) {
            val designator = designator()?: break
            designators.add(designator)
        }
        return@rule if (designators.isEmpty()) {
            null
        } else {
            designators
        }
    }

    // designator
    //	: '[' constant_expression ']'
    //	| '.' IDENTIFIER
    //	;
    fun designator(): Designator? = rule {
        if (check("[")) {
            eat()
            val expr = constant_expression() ?: throw ParserException(InvalidToken("Expected constant expression", peak()))
            if (check("]")) {
                eat()
                return@rule ArrayDesignator(expr)
            }
            throw ParserException(InvalidToken("Expected ']'", peak()))
        }
        if (check(".")) {
            eat()
            val ident = peak<Identifier>()
            eat()
            return@rule MemberDesignator(ident)
        }
        return@rule null
    }

    // abstract_declarator
    //	: pointer
    //	| direct_abstract_declarator
    //	| pointer direct_abstract_declarator
    fun abstract_declarator(): AbstractDeclarator? = rule {
        val pointers = pointer()
        if (pointers != null) {
            val declarator = direct_abstract_declarator()
            return@rule AbstractDeclarator(pointers, declarator)
        }

        val declarator = direct_abstract_declarator()?: return@rule null
        return@rule AbstractDeclarator(listOf(), declarator)
    }

    // primary_expression
    //	: IDENTIFIER
    //	| CONSTANT
    //	| STRING_LITERAL
    //	| '(' expression ')'
    //	;
    fun primary_expression(): Expression? = rule {
        if (check("(")) {
            eat()
            val expr = expression()
            if (check(")")) {
                eat()
                return@rule expr
            } else {
                throw ParserException(InvalidToken("Expected ')'", peak()))
            }
        }
        if (check("_Generic")) {
            TODO()
        }
        if (check<Identifier>()) {
            val ident = peak<Identifier>()
            eat()
            return@rule VarNode(ident)
        }
        if (check<StringLiteral>()) {
            val str = peak<StringLiteral>()
            eat()
            val allLiterals = mutableListOf(str)
            while (check<StringLiteral>()) {
                allLiterals.add(peak())
                eat()
            }
            return@rule StringNode(allLiterals)
        }
        if (check<CharLiteral>()) {
            val char = peak<CharLiteral>()
            eat()
            return@rule CharNode(char)
        }
        if (check<Numeric>()) {
            val num = peak<Numeric>()
            eat()
            return@rule NumNode(num)
        }
        return@rule null
    }

    // external_declaration
    //	: function_definition
    //	| declaration
    //	;
    fun external_declaration(): Node? = rule {
        val declaration = declaration()
        if (declaration != null) {
            // Early resolve type.
            declaration.specifyType(typeHolder)
            return@rule declaration
        }
        return@rule function_definition()
    }

    companion object {
        fun build(filename: String, tokens: TokenList): CProgramParser {
            return CProgramParser(filename, tokens)
        }

        fun build(tokens: TokenList): CProgramParser {
            return CProgramParser("no-name", tokens)
        }
    }
}