package lox

import java.io.File

class RuntimeError(message: String, val token: Token): Exception(message)

object Lox {
    var hadError = false
    var hadRuntimeError = false

    fun runFile(path: String) {
        val file = File(path)

        if(!file.exists()) {
            System.err.println("$path not found.")
            System.exit(127)
        }

    //    println(.readText())

        run(File(path).readText())

        if (hadError) {
            System.exit(65)
        }

        if (hadRuntimeError) {
            System.exit(70)
        }
    }

    fun runPrompt() {
        println("Run prompt")
    }

    fun run(source: String) {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)

        val stmts = parser.parse()

        stmts.forEach {
            println(it.toString())
        }
    }

    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    fun error(token: Token, message: String) {
        if (token.type === TokenType.EOF) {
            report(token.line, " at end", message)
        } else {
            report(token.line, " at '" + token.lexeme + "'", message)
        }
    }

    fun report(line: Int, where: String, message: String) {
        System.err.println("[line $line] Error$where: $message")
        hadError = true
    }

    fun runtimeError(error: RuntimeError) {
        System.err.println("${error.message} \n[line ${error.token.line}]")
        hadRuntimeError = true
    }
}
