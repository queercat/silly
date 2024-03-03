package org.example

import java.util.*

open class Type {
    class Number(val value: kotlin.Float) : Type() {
        override fun toString(): String {
            return value.toString()
        }
    }
    class Symbol(val value: kotlin.String) : Type() {
        override fun toString(): String {
            return value
        }
    }
    class Null() : Type() {
        override fun toString(): String {
            return "null"
        }
    }
    class List(val value: Vector<Type>) : Type() {
        override fun toString(): String {
            val text = value.map {
                it.toString()
            }.joinToString()

            return "($text)"
        }
    }
}

class Peekable<T>(val values: List<T>) {
    var index = 0

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
    var list = Vector<Type>()

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

fun parse(tokens: Peekable<String>): Type {
    var token = tokens.peek();

    val ast = when (token) {
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

    var tokens = Peekable(source.split(" ").filter { it.isNotEmpty() })
    var ast = parse(tokens);

    println(ast)
}