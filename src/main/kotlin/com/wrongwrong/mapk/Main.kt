@file:Suppress("UNUSED_VARIABLE")

package com.wrongwrong.mapk

import com.mapk.kmapper.KMapper
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.system.measureTimeMillis
import org.modelmapper.ModelMapper

@Data
data class Dst(
    val hoge: Byte,
    val fuga: Short,
    val piyo: Int,
    val fizz: Long,
    val buzz: BigInteger,
    val fizzBuzz: Float,
    val foo: Double,
    val bar: BigDecimal
)

data class Src(
    val hoge: Byte,
    val fuga: Short,
    val piyo: Int,
    val fizz: Long,
    val buzz: BigInteger,
    val fizzBuzz: Float,
    val foo: Double,
    val bar: BigDecimal,
    val baz: String
)

const val limit = 1000

fun main() {
    val src = Src(
        0,
        1,
        2,
        3L,
        BigInteger.valueOf(4L),
        5.0f,
        6.0,
        BigDecimal.valueOf(7.0),
        "8"
    )

    val kMapper = KMapper(Dst::class)
    val modelMapper = ModelMapper()

    println("HandWright:\t\t${measureTimeMillis { for (i in 1..limit) Dst(
        hoge = src.hoge,
        fuga = src.fuga,
        piyo = src.piyo ,
        fizz = src.fizz,
        buzz = src.buzz,
        fizzBuzz = src.fizzBuzz,
        foo = src.foo,
        bar = src.bar
    ) }}")

    println("KMapper:\t\t${measureTimeMillis { for (i in 1..limit) kMapper.map(src) }}")

    println("ModelMapper:\t${measureTimeMillis { for (i in 1..limit) modelMapper.map(src, Dst::class.java) }}")
}
