package lox

import lox.TokenType.*

private class ParseError : Exception()

class Parser(val tokens: List<Token>) {
    var current = 0

    internal fun parse(): List<Stmt?> {
        val statements = mutableListOf<Stmt?>()

        while (!isAtEnd()) {
            statements.add(declaration())
        }

        return statements
    }

    private fun declaration(): Stmt? {
        return try {
            when {
                match(CLASS) -> classDeclaration()
                match(FUN) -> function("function")
                match(VAR) -> varDeclaration()
                else -> statement()
            }
        } catch (error: ParseError) {
            synchronize()
            null
        }
    }

    private fun classDeclaration(): Stmt? {
        val name = consume(IDENTIFIER, "Expect class name.")

        if (match(LESS)) {
          consume(IDENTIFIER, "Expect superclass name.")
        }
        val superclass = Variable(previous())

        consume(LEFT_BRACE, "Expect '{' before class body.")

        val methods = mutableListOf<Function>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
          methods.add(function("method"))
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.")

        return Class(name, superclass, methods)
    }

    private fun statement(): Stmt {
        return when {
            match(FOR) -> forStatement()
            match(IF) -> ifStatement()
            match(PRINT) -> printStatement()
            match(RETURN) -> returnStatement()
            match(WHILE) -> whileStatement()
            match(LEFT_BRACE) -> Block(block())
            else -> expressionStatement()
        }
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun forStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'for'.")

        val initializer = when {
            match(SEMICOLON) -> null
            match(VAR) -> varDeclaration()
            else -> expressionStatement()
        }

        var condition: Expr? = null
        if (!check(SEMICOLON)) {
            condition = expression()
        }
        consume(SEMICOLON, "Expect ';' after loop condition.")

        var increment: Expr? = null
        if (!check(RIGHT_PAREN)) {
            increment = expression()
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.")

        var body = statement()
        if (increment != null) {
            body = Block(listOf(body, Expression(increment)))
        }

        // todo: 是否能上移
        if (condition == null) {
            condition = Literal(true)
        }

        body = While(condition, body)

        if (initializer != null) {
            body = Block(listOf(initializer, body))
        }

        return body
    }

    private fun whileStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after condition.")

        val body = statement()

        return While(condition, body)
    }

    private fun ifStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after if condition.") // [parens]

        val thenBranch = statement()
        val elseBranch = if (match(ELSE)) statement() else null

        return If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(SEMICOLON, "Expect ';' after value.")
        return Print(value)
    }

    private fun returnStatement(): Stmt {
        val keyword = previous()
        val value = if (!check(SEMICOLON)) expression() else null

        consume(SEMICOLON, "Expect ';' after return value.")
        return Return(keyword, value)
    }

    private fun varDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect variable name.")
        val initializer = if (match(EQUAL)) expression() else null

        consume(SEMICOLON, "Expect ';' after variable declaration.")

        return Var(name, initializer)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(SEMICOLON, "Expect ';' after expression.")
        return Expression(expr)
    }

    private fun function(kind: String): Function {
        val name = consume(IDENTIFIER, "Expect $kind name.")

        consume(LEFT_PAREN, "Expect '(' after $kind name.")

        val parameters = ArrayList<Token>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size >= 8) {
                    error(peek(), "Cannot have more than 8 parameters.")
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name."))
            } while (match(COMMA))
        }

        consume(RIGHT_PAREN, "Expect ')' after parameters.")

        consume(LEFT_BRACE, "Expect '{' before $kind body.")

        val body = block()
        return Function(name, parameters, body)
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            // 可能有空指针异常
            statements.add(declaration()!!)
        }

        consume(RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun assignment(): Expr {
        val expr = or()

        return if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()

            when (expr) {
                is Variable -> Assign(expr.name, value)
                is Get -> Set(expr.obj, expr.name, value)
                else -> {
                    error(equals, "Invalid assignment target.") // [no-throw]
                    expr
                }
            }
        } else {
            expr
        }
    }

    private fun or(): Expr {
        var expr = and()

        while (match(OR)) {
            val operator = previous()
            val right = and()
            expr = Logical(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr = equality()

        while (match(AND)) {
            val operator = previous()
            val right = equality()
            expr = Logical(expr, operator, right)
        }

        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun comparison(): Expr {
        var expr = addition()

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val operator = previous()
            val right = addition()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun addition(): Expr {
        var expr = multiplication()

        while (match(MINUS, PLUS)) {
            val operator = previous()
            val right = multiplication()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun multiplication(): Expr {
        var expr = unary()

        while (match(SLASH, STAR)) {
            val operator = previous()
            val right = unary()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = previous()
            val right = unary()
            return Unary(operator, right)
        }
        return call()
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = ArrayList<Expr>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size >= 8) {
                    error(peek(), "Cannot have more than 8 arguments.")
                }
                arguments.add(expression())
            } while (match(COMMA))
        }

        val paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")

        return Call(callee, paren, arguments)
    }

    private fun call(): Expr {
        var expr = primary()

        loop@ while (true) {
            expr = when {
                match(LEFT_PAREN) -> finishCall(expr)
                match(DOT) -> {
                    val name = consume(IDENTIFIER, "Expect property name after '.'." )
                    Get(expr, name)
                }
                else -> break@loop
            }
        }

        return expr
    }

    private fun primary(): Expr {
        return when {
            match(FALSE) -> Literal(false)
            match(TRUE) -> Literal(true)
            match(NIL) -> Literal(null)
            match(NUMBER, STRING) -> Literal(previous().literal)
            match(SUPER) -> {
                val keyword = previous()
                consume(DOT, "Expect '.' after 'super'.")
                val method = consume(IDENTIFIER, "Expect superclass method name.")
                Super(keyword, method)
            }
            match(THIS) -> This(previous())
            match(IDENTIFIER) -> Variable(previous())
            match(LEFT_PAREN) -> {
                val expr = expression()
                consume(RIGHT_PAREN, "Expect ')' after expression.")
                Grouping(expr)
            }
            else -> throw error(peek(), "Expect expression.")
        }
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }

        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) {
            return advance()
        }

        throw error(peek(), message);
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) {
            return false
        }

        return peek().type == type
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun advance(): Token {
        if (!isAtEnd()) {
            current++
        }
        return previous()
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }

    private fun isAtEnd(): Boolean {
        return peek().type == EOF
    }

    private fun error(token: Token, message: String): ParseError {
        reportError(token, message)
        return ParseError()
    }

    private fun synchronize() {
        advance()

        while (!isAtEnd()) {
            if (previous().type === SEMICOLON) return

            when (peek().type) {
                CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN -> return
            }

            advance()
        }
    }
}