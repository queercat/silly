package org.example

import java.util.*

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
}

class Peekable<T>(private val values: List<T>) {
    private var index = 0

    fun peek(): T {
        return if (index > values.count() - 1) throw IndexOutOfBoundsException("Expected a value but instead found EOF.") else values[index]
    }

    fun next(): T {
        return if (index + 1> values.count() - 1) throw IndexOutOfBoundsException("Expected a value but instead found EOF.") else values[++index]
    }
}

object Patterns {
    val numberPattern = Regex.fromLiteral("\\d+")
    val symbolPattern = Regex.fromLiteral("\\+|\\-|\\*|\\-")
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
    }

    return Type.Symbol(token)
}

fun evaluate(ast: Type): Type {
    if (ast is Type.List) {
        if (ast.value.count() <= 1) {
            throw IllegalArgumentException("Expected a list with greater than 1 arguments but instead found ${ast.value.count()} arguments.")
        }

        val first = ast.value.first()
        val arguments = ast.value.slice(1..ast.value.count())

        if (first !is Type.Symbol) throw IllegalArgumentException("Expected a symbol as the first argument but instead found {$first}")

        if (first.value == "'") {
            if (arguments.count() > 1) throw IllegalArgumentException("Too many parameters for quoting expected 1 bunt instead found ${arguments.count()}")
            return arguments[0]
        }

        return Type.List(Vector(arguments.map { evaluate(it) }))
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

fun main() {
    var source: String = readlnOrNull() ?: throw Exception("Unable to read from standard-in.")

    source = source
        .replace("(", " ( ")
        .replace(")", " ) ")

    val tokens = Peekable(source.split(" ").filter { it.isNotEmpty() })
    val ast = parse(tokens)

    println(evaluate(ast))
}