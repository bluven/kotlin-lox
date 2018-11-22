package lox

class ReturnError(val value: Any?): RuntimeException(null, null, false, false)

internal interface LoxCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}

internal class LoxInstance(private val klass: LoxClass) {
    private val fields = mutableMapOf<String, Any?>()

    fun get(name: Token): Any? {
        if (fields.containsKey(name.lexeme)) {
          return fields.get(name.lexeme)
        }

        val method= klass.findMethod(this, name.lexeme)
        if (method != null) {
            return method
        }

        throw RuntimeError(name, "Undefined property '${name.lexeme }'.")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString() = klass.name + " instance"
}

internal class LoxFunction constructor(
    private val declaration: Function,
    private val closure: Environment,
    private val isInitializer: Boolean
) : LoxCallable {

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment, isInitializer)
    }

    override fun arity() = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)

        declaration.params.forEachIndexed { i, token ->
            environment.define(token.lexeme, arguments[i])
        }

        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: ReturnError) {
            return if (isInitializer) closure.getAt(0, "this") else returnValue.value
        }

        return if (isInitializer) closure.getAt(0, "this") else null
    }

    override fun toString() = "<fn ${declaration.name.lexeme}>"
}

internal class LoxClass constructor(
    val name: String,
    private val superClass: LoxClass?,
    private val methods: Map<String, LoxFunction>): LoxCallable {

    fun findMethod(instance: LoxInstance, name: String): LoxFunction? {
        return when {
            methods.containsKey(name) -> methods.getValue(name).bind(instance)
            superClass != null -> superClass.findMethod(instance, name)
            else -> null
        }
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val instance = LoxInstance(this)
        methods["init"]?.bind(instance)?.call(interpreter, arguments)
        return instance
    }

    override fun arity() = methods["init"]?.arity() ?: 0

    override fun toString() = name
}
