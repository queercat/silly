package org.example

import java.util.*

class Environment() {
    val values = HashMap<String, Type>()
    var outer: Environment? = null

    constructor(parameters: Type.List, arguments: Type.List) : this() {
        if (parameters.value.count() != arguments.value.count()) throw IllegalArgumentException("Parameters ")

        arguments.value.zip(parameters.value).forEach {
            val (parameter, argument) = it
            if (parameter !is Type.Symbol) throw IllegalArgumentException("Expected all parameters to be symbol but instead found ${it.first::class.qualifiedName} ${it.first}")

            values[parameter.value] = argument
        }
    }

    fun find(symbol: String): Environment? {
        return if (this.values.containsKey(symbol)) this else this.outer?.find(symbol)
    }
}

open class Type {
    class Number(val value: Float) : Type() {
        override fun toString(): String {
            return value.toString()
        }
    }

    class Symbol(val value: String) : Type() {
        override fun toString(): String {
            return value
        }
    }

    class Null : Type() {
        override fun toString(): String {
            return "null"
        }
    }

    class List(val value: Vector<Type>) : Type() {
        override fun toString(): String {
            val text = value.joinToString {
                it.toString()
            }

            return "($text)"
        }
    }

    class Function(val body: Type.List, Type>, val parameters: Type.List, val environment: Environment) : Type() {
        override fun toString(): String {
            return "Function {$body}"
        }

        operator fun invoke(arguments: Type.List): Type {
            if (arguments.value.count() != parameters.value.count()) throw IllegalArgumentException("Expected ${parameters.value.count()} arguments but instead found ${arguments.value.count()}.")

            return evaluate(parameters, Environment(arguments, parameters))
        }
    }

    class Boolean(val value: kotlin.Boolean) : Type() {
        override fun toString(): String {
            return value.toString()
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
    val symbolPattern = Regex("[+\\-/*]")
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

fun parseAtom(token: String): Type {
    if (Patterns.numberPattern.matches(token)) {
        return Type.Number(token.toFloat())
    } else if (Patterns.symbolPattern.matches(token)) {
        return Type.Symbol(token)
    } else if (token == "true" || token == "false") {
        return Type.Boolean(token == "true")
    }

    return Type.Symbol(token)
}

fun evaluate(ast: Type, environment: Environment): Type {
    if (ast is Type.List) {
        if (ast.value.count() <= 1) throw IllegalArgumentException("Expected a list with greater than 1 arguments but instead found ${ast.value.count()} arguments.")

        val first = ast.value.first()
        var arguments = ast.value.slice(1..<ast.value.count())

        if (first !is Type.Symbol) throw IllegalArgumentException("Expected a symbol as the first argument but instead found {$first}")

        if (first.value == "'") {
            if (arguments.count() > 1) throw IllegalArgumentException("Too many parameters for quoting, expected 1 but instead found ${arguments.count()}")
            return arguments[0]
        } else if (first.value == "if") {
            if (arguments.count() != 3) throw IllegalArgumentException("Expected 3 arguments but instead found ${arguments.count()}")

            val bool = evaluate(arguments[0], environment)

            if (bool !is Type.Boolean) throw IllegalArgumentException("Expected boolean value but instead found ${bool::class.qualifiedName} $bool")

            return if (bool.value) evaluate(arguments[1], environment) else evaluate(arguments[2], environment)
        } else if (first.value == "let") {
            if (arguments.count() != 2) throw IllegalArgumentException("Expected 2 arguments but instead found ${arguments.count()}")

            val symbol = arguments[0]

            if (symbol !is Type.Symbol) throw IllegalArgumentException("Expected symbol to assign to but instead found ${symbol::class.qualifiedName} $symbol")

            environment.values[symbol.value] = evaluate(arguments[1], environment)

            return Type.Null()
        } else if (first.value == "^") {
            return Type.Function()
        }

        arguments.map { evaluate(it, environment) }.also { arguments = it }

        val function = environment.values[first.value]
            ?: throw ClassNotFoundException("Unable to find symbol ${first.value} in environment.")

        if (function !is Type.Function) throw IllegalArgumentException("Expected function stored but found something else. {$function}")

        val result = function(Type.List(Vector(arguments)))

        return result
    } else if (ast is Type.Symbol) {
        return environment.find(ast.value)?.values?.get(ast.value)
            ?: throw IllegalArgumentException("Unable to find symbol ${ast.value} in environment.")
    }

    return ast
}


fun parse(tokens: Peekable<String>): Type {

    val ast = when (val token = tokens.peek()) {
        "(" -> parseList(tokens)
        else -> parseAtom(token)
    }

    return ast
}

fun read(): String {
    var source: String = readlnOrNull() ?: throw Exception("Unable to read from standard-in.")

    source = source
        .replace("(", " ( ")
        .replace(")", " ) ")

    return source
}

fun eval(source: String, environment: Environment): Type {
    val tokens = Peekable(source.split(" ").filter { it.isNotEmpty() })
    val ast = parse(tokens)

    return evaluate(ast, environment)
}

fun createStandardEnvironment(): Environment {
    val environment = Environment()

    environment.values["+"] = Type.Function(
        fun(arguments: Type): Type {
            if (arguments !is Type.List) throw IllegalArgumentException("Expected function arguments but instead found ${arguments}")

            return Type.Number(arguments.value.fold(0f,
                fun(acc: Float, type: Type): Float {
                    if (type !is Type.Number) throw IllegalArgumentException("Expected number type but instead found ${type::class.qualifiedName} ${type}")

                    return acc + type.value
                }
            ))
        }, Type.List(Vector(arrayListOf(Type.Symbol("a"), Type.Symbol("b")))), environment
    )

    return environment
}

fun print(ast: Type) {
    println(ast)
}

fun main() {
    val environment = createStandardEnvironment()

    while (true) {
        try {
            print(eval(read(), environment))
        } catch (exception: Exception) {
            println(exception)
        }
    }
}