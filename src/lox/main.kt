package lox

import java.io.File

var hadError = false
var hadRuntimeError = false

class RuntimeError(message: String, val token: Token): Exception(message)

fun main(args: Array<String>) {
    when (args.size) {
        0 -> runPrompt()
        1 -> runFile(args[0])
        else -> {
            println("Usage: jlox [script]")
            System.exit(64) // [64]
        }
    }
}

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

fun report(line: Int, where: String, message: String) {
    System.err.println("[line $line] Error$where: $message")
    hadError = true
}

fun reportError(token: Token, message: String) {
    if (token.type === TokenType.EOF) {
        report(token.line, " at end", message)
    } else {
        report(token.line, " at '" + token.lexeme + "'", message)
    }
}

fun runtimeError(error: RuntimeError) {
    System.err.println("${error.message} \n[line ${error.token.line}]")
    hadRuntimeError = true
}
