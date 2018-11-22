package lox

import lox.TokenType.*

internal class Interpreter: ExprVisitor<Any?>, StmtVisitor<Unit> {
    private val globals = Environment()
    private var environment = globals
    private val locals = mutableMapOf<Expr, Int>()

    fun init() {
        globals.define("clock", object : LoxCallable {
            override fun arity() = 0

            override fun call(interpreter: Interpreter, arguments: List<Any?>) =
                System.currentTimeMillis().toDouble() / 1000.0

            override fun toString() = "<native fn>"
        })
    }

    fun interpret(statements: List<Stmt>) {
        try {
            statements.forEach(this::execute)
        } catch (error: RuntimeError) {
            Lox.runtimeError(error)
        }
    }

    private fun evaluate(expr: Expr) = expr.accept(this)

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    fun executeBlock(statements: List<Stmt>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            statements.forEach(this::execute)
        } finally {
            this.environment = previous
        }
    }

    override fun visitBlockStmt(stmt: Block) {
        executeBlock(stmt.statements, Environment(environment))
    }

    override fun visitClassStmt(stmt: Class) {
        var superClass: Any? = null

        if (stmt.superclass != null) {
            superClass = evaluate(stmt.superclass)

            if (superClass !is LoxClass) {
                throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
            }
        }

        environment.define(stmt.name.lexeme, null)

        if (superClass != null) {
            environment = Environment(environment)
            environment.define("super", superClass)
        }

        val methods = stmt.methods.map {
            it.name.lexeme to LoxFunction(it, environment, it.name.lexeme == "init")
        }.toMap()

        val klass = LoxClass(stmt.name.lexeme, superClass as LoxClass, methods)

        if (superClass != null) {
            environment = environment.enclosing!!
        }

        environment.assign(stmt.name, klass)
    }

    override fun visitExpressionStmt(stmt: Expression) {
        evaluate(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Function) {
        environment.define(stmt.name.lexeme, LoxFunction(stmt, environment, false))
    }

    override fun visitIfStmt(stmt: If) {
        when {
            isTruthy(evaluate(stmt.condition)) -> execute(stmt.thenBranch)
            stmt.elseBranch != null -> execute(stmt.elseBranch)
        }
    }

    override fun visitPrintStmt(stmt: Print) {
        val value = evaluate(stmt.expression)
        println(stringify(value))
    }

    override fun visitReturnStmt(stmt: Return) {
        throw ReturnError(
            if (stmt.value != null) evaluate(stmt.value) else null
        )
    }

    override fun visitVarStmt(stmt: Var) {
        environment.define(
            stmt.name.lexeme,
            if (stmt.initializer != null) evaluate(stmt.initializer) else null
        )
    }

    override fun visitWhileStmt(stmt: While) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
    }

    override fun visitAssignExpr(expr: Assign): Any? {
        val value = evaluate(expr.value)

        val distance = locals[expr]
        if (distance != null) {
            environment.assignAt(distance, expr.name, value)
        } else {
            globals.assign(expr.name, value)
        }

        return value
    }

    override fun visitBinaryExpr(expr: Binary): Any {
        val left = evaluate(expr.left)!!
        val right = evaluate(expr.right)!!

        return when (expr.operator.type) {
            BANG_EQUAL -> !isEqual(left, right)
            EQUAL_EQUAL -> isEqual(left, right)
            GREATER -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double > right as Double
            }
            GREATER_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double >= right as Double
            }
            LESS -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) < right as Double
            }
            LESS_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double <= right as Double
            }
            MINUS -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double - right as Double
            }
            PLUS -> when {
                left is Double && right is Double -> left + right
                left is String && right is String -> left + right
                else -> throw RuntimeError(expr.operator, "Operands must be two numbers or two strings.")
            }
            SLASH -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double / right as Double
            }
            STAR -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double * right as Double
            }
            else -> throw Exception("unavailable")
        }
    }

    override fun visitCallExpr(expr: Call): Any? {
        val callee = evaluate(expr.callee)
        val arguments = expr.arguments.map { evaluate(it) }

        if (callee !is LoxCallable) {
            throw RuntimeError(expr.paren, "Can only call functions and classes.")
        }

        if (arguments.size != callee.arity()) {
          throw RuntimeError(
              expr.paren,
              "Expected ${callee.arity()} arguments but got ${arguments.size }.")
        }

        return callee.call(this, arguments)
    }

    override fun visitThisExpr(expr: This) = lookUpVariable(expr.keyword, expr)

    override fun visitGroupingExpr(expr: Grouping) = evaluate(expr.expression)

    override fun visitLiteralExpr(expr: Literal) = expr.value

    override fun visitLogicalExpr(expr: Logical): Any? {
        val left = evaluate(expr.left)

        return when {
            expr.operator.type == OR && isTruthy(left) -> left
            expr.operator.type == AND && !isTruthy(left) -> left
            else -> evaluate(expr.right)
        }
    }

    override fun visitGetExpr(expr: Get): Any? {
        val obj = evaluate(expr.obj)
        if (obj is LoxInstance) {
            return obj.get(expr.name)
        }

        throw RuntimeError(expr.name, "Only instances have properties.")
    }

    override fun visitSetExpr(expr: Set): Any? {
        val obj = evaluate(expr.obj) as? LoxInstance ?: throw RuntimeError(expr.name, "Only instances have fields.")
        val value = evaluate(expr.value)

        obj.set(expr.name, value)
        return value
    }

    override fun visitSuperExpr(expr: Super): Any? {
        val distance = locals.getValue(expr)
        val superClass = environment.getAt(distance, "super") as LoxClass
        val instance = environment.getAt(distance - 1, "this") as LoxInstance
        val method = superClass.findMethod(instance, expr.method.lexeme)

        if (method == null) {
            throw RuntimeError(expr.method, "Undefined property '${expr.method.lexeme}'.")
        }

        return method
    }

   override fun visitUnaryExpr(expr: Unary): Any? {
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            BANG -> !isTruthy(right)
            MINUS -> {
                checkNumberOperand(expr.operator, right)
                return -(right as Double)
            }
            else -> null
        }
    }

    override fun visitVariableExpr(expr: Variable) = lookUpVariable(expr.name, expr)

    private fun lookUpVariable(name: Token, expr: Expr): Any? {
        val distance = locals.get(expr)

        return if (distance != null) {
            environment.getAt(distance, name.lexeme)
        } else {
            globals.get(name)
        }
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand is Double) {
            return
        }
        throw RuntimeError(operator, "Operand must be a number.")
    }

    private fun checkNumberOperands(operator: Token, left: Any, right: Any) {
        if (left is Double && right is Double) {
            return
        }
        throw RuntimeError(operator, "Operands must be numbers.")
    }

    private fun isTruthy(obj: Any?): Boolean {
        return when (obj) {
            null -> false
            is Boolean -> obj
            else -> true
        }
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        return when {
            a == null && b == null -> true
            a == null -> false
            else -> a == b
        }
    }

    private fun stringify(obj: Any?): String {
        return when (obj) {
            null -> "nil"
            is Double -> {
                val text = obj.toString()
                if (text.endsWith(".0")) text.substring(0, text.length - 2) else text
            }
            else -> obj.toString()

        }
    }
}
