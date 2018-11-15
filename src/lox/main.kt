package lox

fun main(args: Array<String>) {
    when (args.size) {
        0 -> Lox.runPrompt()
        1 -> Lox.runFile(args[0])
        else -> {
            println("Usage: jlox [script]")
            System.exit(64) // [64]
        }
    }
}

