package lox

import java.util.Stack

private enum class FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD
}

private enum class ClassType {
    NONE,
    CLASS,
    SUBCLASS
}

internal class Resolver(val interpreter: Interpreter): ExprVisitor<Unit>, StmtVisitor<Unit> {
    private val scopes = Stack<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE

    fun resolve(statements: List<Stmt>) {
        statements.forEach(this::resolve)
    }

    private fun resolve(stmt: Stmt) {
        stmt.accept(this)
    }

    private fun resolve(expr: Expr) {
        expr.accept(this)
    }

    override fun visitBlockStmt(stmt: Block) {
        beginScope()
        resolve(stmt.statements)
        endScope()
    }

    private fun beginScope() {
        scopes.push(mutableMapOf())
    }

    private fun endScope() {
        scopes.pop()
    }

    override fun visitClassStmt(stmt: Class) {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS

        declare(stmt.name)

        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS
            resolve(stmt.superclass)
        }

        define(stmt.name)

        if (stmt.superclass != null) {
            beginScope()
            scopes.peek()["super"] = true
        }

        beginScope()
        scopes.peek()["this"] = true

        stmt.methods.forEach {
            val declaration= if (it.name.lexeme == "init") {
                FunctionType.INITIALIZER
            } else {
                FunctionType.METHOD
            }

            resolveFunction(it, declaration)
        }

        endScope()

        if (stmt.superclass != null) {
            endScope()
        }

        currentClass = enclosingClass
    }

    override fun visitExpressionStmt(stmt: Expression) {
        resolve(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Function) {
        declare(stmt.name)
        define(stmt.name)

        resolveFunction(stmt, FunctionType.FUNCTION)
    }

    override fun visitIfStmt(stmt: If) {
        resolve(stmt.condition)
        resolve(stmt.thenBranch)

        if (stmt.elseBranch != null) {
            resolve(stmt.elseBranch)
        }
    }

    override fun visitPrintStmt(stmt: Print) = resolve(stmt.expression)

    override fun visitReturnStmt(stmt: Return) {

        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Cannot return from top-level code.")
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Cannot return a value from an initializer.")
            }

            resolve(stmt.value)
        }
    }

    override fun visitVarStmt(stmt: Var) {
        declare(stmt.name)

        if (stmt.initializer != null) {
            resolve(stmt.initializer)
        }

        define(stmt.name)
    }

    override fun visitWhileStmt(stmt: While) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }

    override fun visitAssignExpr(expr: Assign) {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
    }

    override fun visitBinaryExpr(expr: Binary) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitCallExpr(expr: Call) {
        resolve(expr.callee)
        expr.arguments.forEach(this::resolve)
    }

    override fun visitGetExpr(expr: Get) = resolve(expr.obj)

    override fun visitGroupingExpr(expr: Grouping) = resolve(expr.expression)

    override fun visitLiteralExpr(expr: Literal) {}

    override fun visitLogicalExpr(expr: Logical) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitSetExpr(expr: Set) {
        resolve(expr.value)
        resolve(expr.obj)
    }

    override fun visitSuperExpr(expr: Super) {
        when (currentClass) {
            ClassType.NONE ->
                Lox.error(expr.keyword, "Cannot use 'super' outside of a class.")
            ClassType.SUBCLASS ->
                Lox.error(expr.keyword, "Cannot use 'super' in a class with no superclass.")
            else ->
                resolveLocal(expr, expr.keyword)
        }
    }

    override fun visitThisExpr(expr: This) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Cannot use 'this' outside of a class.")
        } else {
            resolveLocal(expr, expr.keyword)
        }
    }

    override fun visitUnaryExpr(expr: Unary) = resolve(expr.right)

    override fun visitVariableExpr(expr: Variable) {
        if (!scopes.isEmpty() &&
            scopes.peek()[expr.name.lexeme] == false) {
            Lox.error(expr.name, "Cannot read local variable in its own initializer.")
        }

        resolveLocal(expr, expr.name)
    }

    private fun resolveFunction(function: Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type

        beginScope()
        function.params.forEach {
            declare(it)
            define(it)
        }

        resolve(function.body)
        endScope()
        currentFunction = enclosingFunction
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) {
            return
        }

        val scope = scopes.peek()
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Variable with this name already declared in this scope.")
        }

        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) {
            return
        }

        scopes.peek()[name.lexeme] = true
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }
    }

}
