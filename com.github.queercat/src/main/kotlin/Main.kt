package org.example

import java.util.*

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

    fun find(symbol: String): Environment? {
        return if (this.values.containsKey(symbol)) this else this.outer?.find(symbol)
    }
}

open class Type() {
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

    class Function(
        val body: (Type.List) -> Type,
    ) : Type() {
        override fun toString(): String {
            return "Function {$body}"
        }
    }

    class Boolean(val value: kotlin.Boolean) : Type() {
        override fun toString(): String {
            return value.toString()
        }
    }

    class Atom(var value: Type) : Type() {
        override fun toString(): String {
            return "@ -> ${value.toString()}"
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
    val symbolPattern = Regex("[+\\-/*^@]")
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

fun apply(ast: Type, environment: Environment): Type {
    if (ast is Type.List) {
        return Type.List(Vector(ast.value.map { evaluate(it, environment) }))
    }

    if (ast is Type.Symbol) {
        return environment.find(ast.value)?.values?.get(ast.value)
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
        }
        else if (first.value == "if") {
            if (arguments.value.count() != 3) throw IllegalArgumentException("Expected 3 arguments but instead found ${arguments.value.count()}")

            val bool = evaluate(arguments.value[0], environment)

            if (bool !is Type.Boolean) throw IllegalArgumentException("Expected boolean value but instead found ${bool::class.qualifiedName} $bool")

            return if (bool.value) evaluate(arguments.value[1], environment) else evaluate(arguments.value[2], environment)
        }
        else if (first.value == "let") {
            if (arguments.value.count() != 2) throw IllegalArgumentException("Expected 2 arguments but instead found ${arguments.value.count()}")

            val symbol = arguments.value[0]

            if (symbol !is Type.Symbol) throw IllegalArgumentException("Expected symbol to assign to but instead found ${symbol::class.qualifiedName} $symbol")

            environment.values[symbol.value] = evaluate(arguments.value[1], environment)

            return environment.values[symbol.value] ?: throw ClassNotFoundException("Unable to bind value to symbol. $environment")
        }
        else if (first.value == "^") {
            return Type.Function(
                fun (lambdaArguments: Type.List): Type {
                    val lambdaParameters = arguments.value[0] as Type.List

                    val closure = Environment(lambdaParameters, lambdaArguments, environment)

                    return evaluate(arguments.value[1], closure)
                })
        }

        arguments = apply(arguments, environment).assert<Type.List>()
        val lambda = environment.find(first.value)?.values?.get(first.value)?.assert<Type.Function>() ?: throw ClassNotFoundException("Unable to find symbol. ")

        return lambda.body(arguments)
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
        .replace("^", " ^ ")
        .replace("@", " @ ")
        .replace("+", " + ")
        .replace("'", " ' ")

    return source
}

fun eval(source: String, environment: Environment): Type {
    val tokens = Peekable(source.split(" ").filter { it.isNotEmpty() })
    val ast = parse(tokens)

    return evaluate(ast, environment)
}

inline fun <reified T : Type> Type.assert(): T {
    if (this !is T) throw IllegalArgumentException("Expected a number type but found ${this::class.qualifiedName} $this instead.")

    return this
}

fun Type.assertNumber(): Type.Number {
    if (this !is Type.Number) throw IllegalArgumentException("Expected a number type but found ${this::class.qualifiedName} $this instead.")

    return this
}


fun createStandardEnvironment(): Environment {
    val environment = Environment()

    environment.values["+"] = Type.Function(fun(list: Type.List): Type {
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