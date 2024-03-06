package org.example

import java.io.Console
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.*
import kotlin.concurrent.thread

class Environment() {
    val values = HashMap<String, Type>()
    var outer: Environment? = null

    constructor(parameters: Type.List, arguments: Type.List, outer: Environment?) : this() {
        if (parameters.value.count() != arguments.value.count()) throw IllegalArgumentException("Parameters")

        this.outer = outer

        parameters.value.zip(arguments.value).forEach {
            val (parameter, argument) = it
            if (parameter !is Type.Symbol) throw IllegalArgumentException("Expected all parameters to be symbol but instead found ${it.first::class.qualifiedName} ${it.first}")

            values[parameter.value] = argument
        }
    }

    fun find(symbol: String): Environment {
        return if (this.values.containsKey(symbol)) this else this.outer?.find(symbol)
            ?: throw ClassNotFoundException("Unable to find symbol $symbol in environment $this")
    }
}

open class Type {
    class Number(val value: Float) : Type() {
        override fun toString(): kotlin.String {
            return value.toString()
        }
    }

    class Symbol(val value: kotlin.String) : Type() {
        override fun toString(): kotlin.String {
            return value
        }
    }

    class Null : Type() {
        override fun toString(): kotlin.String {
            return "null"
        }
    }

    class List(val value: Vector<Type>) : Type() {
        override fun toString(): kotlin.String {
            val text = value.joinToString {
                it.toString()
            }

            return "($text)"
        }
    }

    class String(val value: kotlin.String) : Type() {
        override fun toString(): kotlin.String {
            return value
        }
    }

    class Function(
        val body: (Type.List) -> Type,
    ) : Type() {
        override fun toString(): kotlin.String {
            return "Function {$body}"
        }
    }

    class Boolean(val value: kotlin.Boolean) : Type() {
        override fun toString(): kotlin.String {
            return value.toString()
        }
    }

    class Atom(var value: Type) : Type() {
        override fun toString(): kotlin.String {
            return "@ -> ${value.toString()}"
        }
    }

    class Thread(var ast: Type.List, var environment: Environment) : Type() {
        operator fun invoke(): Type.Thread {
            thread(block = {
                evaluate(ast, environment)
            })

            return this
        }
    }
}

class Peekable<T>(private val values: List<T>) {
    private var index = 0

    fun peek(): T {
        return if (index > values.count() - 1) throw IndexOutOfBoundsException("Expected a value but instead found EOF.") else values[index]
    }

    fun next(): T {
        return if (index + 1 > values.count() - 1) throw IndexOutOfBoundsException("Expected a value but instead found EOF.") else values[++index]
    }
}

object Patterns {
    val numberPattern = Regex("\\d+")
    val unaryPattern = Regex("[()+\\-/*^@']")
    val symbolPattern = Regex("[a-zA-Z\\-\\d]+")
}

fun parseList(tokens: Peekable<String>): Type.List {
    val list = Vector<Type>()

    var token = tokens.next()

    while (token != ")") {
        list.addElement(parse(tokens))
        token = tokens.next()
    }

    return Type.List(list)
}

fun parseAtom(tokens: Peekable<String>): Type {
    val token = tokens.peek()

    if (token[0] == '\"') {
        return Type.String(token.slice(1 until token.length - 1))
    }

    if (Patterns.numberPattern.matches(token)) {
        return Type.Number(token.toFloat())
    } else if (Patterns.unaryPattern.matches(token)) {
        return Type.Symbol(token)
    } else if (token == "true" || token == "false") {
        return Type.Boolean(token == "true")
    }

    return Type.Symbol(token)
}

fun apply(ast: Type, environment: Environment): Type {
    if (ast is Type.List) {
        return Type.List(Vector(ast.value.map { evaluate(it, environment) }))
    }

    if (ast is Type.Symbol) {
        return environment.find(ast.value).values[ast.value]
            ?: throw IllegalAccessException("Unable to find ${ast.value} in environment.")
    }

    return ast
}

fun evaluate(ast: Type, environment: Environment): Type {
    if (ast is Type.List) {
        if (ast.value.isEmpty()) return ast

        val first = ast.value.first()

        var arguments = Type.List(Vector(ast.value.slice(1..<ast.value.count())))

        if (first !is Type.Symbol) throw IllegalArgumentException("Expected a symbol as the first argument but instead found {$first}")

        if (first.value == "'") {
            if (arguments.value.count() > 1) throw IllegalArgumentException("Too many parameters for quoting, expected 1 but instead found ${arguments.value.count()}")
            return arguments.value[0]
        } else if (first.value == "if") {
            if (arguments.value.count() != 3) throw IllegalArgumentException("Expected 3 arguments but instead found ${arguments.value.count()}")

            val bool = evaluate(arguments.value[0], environment)

            if (bool !is Type.Boolean) throw IllegalArgumentException("Expected boolean value but instead found ${bool::class.qualifiedName} $bool")

            return if (bool.value) evaluate(arguments.value[1], environment) else evaluate(
                arguments.value[2], environment
            )
        } else if (first.value == "let") {
            if (arguments.value.count() != 2) throw IllegalArgumentException("Expected 2 arguments but instead found ${arguments.value.count()}")

            val symbol = arguments.value[0]

            if (symbol !is Type.Symbol) throw IllegalArgumentException("Expected symbol to assign to but instead found ${symbol::class.qualifiedName} $symbol")

            environment.values[symbol.value] = evaluate(arguments.value[1], environment)

            return environment.values[symbol.value]
                ?: throw ClassNotFoundException("Unable to bind value to symbol. $environment")
        } else if (first.value == "^") {
            return Type.Function(fun(lambdaArguments: Type.List): Type {
                val lambdaParameters = arguments.value[0] as Type.List

                val closure = Environment(lambdaParameters, lambdaArguments, environment)

                return evaluate(arguments.value[1], closure)
            })
        } else if (first.value == "thread") {
            return Type.Thread(arguments.value[0].assert(), environment)
        }

        arguments = apply(arguments, environment).assert<Type.List>()
        val lambda = environment.find(first.value).values[first.value]!!

        if (lambda is Type.Function) {
            return lambda.body(arguments)
        }

        return lambda.assert<Type.Thread>().invoke()
    } else if (ast is Type.Symbol) {
        return environment.find(ast.value).values[ast.value]
            ?: throw IllegalArgumentException("Unable to find symbol ${ast.value} in environment.")
    }

    return ast
}


fun parse(tokens: Peekable<String>): Type {
    val ast = when (val token = tokens.peek()) {
        "(" -> parseList(tokens)
        else -> parseAtom(tokens)
    }

    return ast
}

fun tokenize(source: String): List<String> {
    var start = 0
    var end = 0
    var char = source[start]

    val tokens = mutableListOf<String>()

    while (start < source.length) {
        char = source[start]

        if (Patterns.unaryPattern.matches(char.toString())) {
            end++
        }

        else if (Patterns.numberPattern.matches(char.toString())) {
            while (end < source.length && Patterns.numberPattern.matches(source[end].toString())) {
                end++
            }
        }

        else if (Patterns.symbolPattern.matches(char.toString())) {
            while (end < source.length && Patterns.symbolPattern.matches(source[end].toString())) {
                end++
            }
        }

        else if (char == '"') {
            if (end == source.length - 1) {
                throw IllegalArgumentException("Expected a closing quote but instead found EOF.")
            }
            while (source[++end] != '"'){}
            end++
        }

        else if (char == ' ' || char == '\n' || char == '\t' || char == '\r') {
            if (end == source.length - 1) {
                break
            }
            while (char == ' ' || char == '\n' || char == '\t' || char == '\r') {
                char = source[++end]
            }

            start = end
            continue
        }

        else {
            throw IllegalArgumentException("Unable to tokenize character ${source[start]}")
        }

        tokens += source.slice(start until end)
        start = end
    }

    return tokens
}

fun read(): String {
    val source: String = readlnOrNull() ?: throw Exception("Unable to read from standard-in.")

    return source
}

fun eval(source: String, environment: Environment): Type {
    val tokens = Peekable(tokenize(source))
    val ast = parse(tokens)

    return evaluate(ast, environment)
}

inline fun <reified T : Type> Type.assert(): T {
    if (this !is T) throw IllegalArgumentException("Expected a number type but found ${this::class.qualifiedName} $this instead.")

    return this
}


fun createStandardEnvironment(): Environment {
    val environment = Environment()

    environment.values["+"] = Type.Function(fun(list: Type.List): Type {
        if (list.value.count() != 2 || list.value[0]::class != list.value[1]::class) throw IllegalArgumentException("Expected 2 numbers but instead found ${list.value.count()}")

        if (list.value[0] is Type.String) {
            return Type.String(list.value[0].assert<Type.String>().value + list.value[1].assert<Type.String>().value)
        }

        return Type.Number(list.value[0].assert<Type.Number>().value + list.value[1].assert<Type.Number>().value)
    })

    environment.values["atom"] = Type.Function(fun(list: Type.List): Type {
        return Type.Atom(list.value[0])
    })

    environment.values["@"] = Type.Function(fun(list: Type.List): Type {
        return list.value[0].assert<Type.Atom>().value
    })

    environment.values["$"] = Type.Function(fun(list: Type.List): Type {
        val atom = list.value[0].assert<Type.Atom>()
        val lambda = list.value[1].assert<Type.Function>()

        val value = atom.value

        atom.value = lambda.body(Type.List(Vector(arrayOf(value).toList())))

        return atom
    })

    environment.values["sleep"] = Type.Function(fun(list: Type.List): Type {
        val duration = list.value[0].assert<Type.Number>().value

        Thread.sleep(duration.toLong())
        return Type.Null()
    })

    environment.values["do"] = Type.Function(fun(list: Type.List): Type {
        var result: Type = Type.Null()

        list.value.forEach {
            result = evaluate(it, environment)
        }

        return result
    })

    environment.values["slurp"] = Type.Function(fun(list: Type.List): Type {
        val filename = list.value[0].assert<Type.String>().value

        return Type.String(java.io.File(filename).readText())
    })

    environment.values["eval"] = Type.Function(fun(list: Type.List): Type {
        val source = list.value[0].assert<Type.String>().value

        return eval(source, environment)
    })

    return environment
}

fun print(ast: Type) {
    println(ast)
}

fun main() {
    val environment = createStandardEnvironment()

    val cursed = "(eval (+ (+ \"(do\" (slurp \"core/core.silly\")) \")\")))"
    evaluate(parseList(Peekable(tokenize(cursed))), environment)

    while (true) {
        try {
            print(eval(read(), environment))
        } catch (exception: Exception) {
            println(exception)
        }
    }
}