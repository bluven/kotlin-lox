package lox

internal class Environment(val enclosing : Environment? = null) {
    private val values = mutableMapOf<String, Any?>()

    fun get(name: Token): Any? {
        return when {
            values.containsKey(name.lexeme) -> values[name.lexeme]
            enclosing != null -> enclosing.get(name)
            else -> throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
        }
    }

    fun assign(name: Token, value: Any?) {
        when  {
            values.containsKey(name.lexeme) -> values[name.lexeme] = value
            enclosing != null -> enclosing.assign(name, value)
            else -> throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
        }
    }

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    private fun ancestor(distance: Int): Environment {
        var env: Environment = this

        for (i in 0 until distance) {
            env = env.enclosing!!
        }

        return env
    }

    fun getAt(distance: Int, name: String): Any? {
        return ancestor(distance).values[name]
    }

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance).values[name.lexeme] = value
    }

    override fun toString(): String {
        var result = values.toString()
        if (enclosing != null) {
            result += " -> " + enclosing.toString()
        }

        return result
    }
}