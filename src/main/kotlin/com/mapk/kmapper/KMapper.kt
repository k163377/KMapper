package com.mapk.kmapper

import com.mapk.annotations.KConstructor
import com.mapk.annotations.KGetterAlias
import com.mapk.annotations.KGetterIgnore
import com.mapk.core.ArgumentBucket
import com.mapk.core.EnumMapper
import com.mapk.core.KFunctionForCall
import com.mapk.core.getAliasOrName
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaGetter

class KMapper<T : Any> private constructor(
    private val function: KFunctionForCall<T>,
    propertyNameConverter: (String) -> String = { it }
) {
    constructor(function: KFunction<T>, propertyNameConverter: (String) -> String = { it }) : this(
        KFunctionForCall(function), propertyNameConverter
    )

    constructor(clazz: KClass<T>, propertyNameConverter: (String) -> String = { it }) : this(
        getTarget(clazz), propertyNameConverter
    )

    private val parameterMap: Map<String, ParameterForMap<*>> = function.parameters
        .filter { it.kind != KParameter.Kind.INSTANCE }
        .associate { (propertyNameConverter(it.getAliasOrName()!!)) to ParameterForMap.newInstance(it) }

    private fun bindArguments(argumentBucket: ArgumentBucket, src: Any) {
        src::class.memberProperties.forEach outer@{ property ->
            // propertyが公開されていない場合は処理を行わない
            if (property.visibility != KVisibility.PUBLIC) return@outer

            // ゲッターが取れない場合は処理を行わない
            val javaGetter: Method = property.javaGetter ?: return@outer

            var alias: String? = null
            // NOTE: IgnoreとAliasが同時に指定されるようなパターンを考慮してaliasが取れてもbreakしていない
            javaGetter.annotations.forEach {
                if (it is KGetterIgnore) return@outer // ignoreされている場合は処理を行わない
                if (it is KGetterAlias) alias = it.value
            }

            parameterMap[alias ?: property.name]?.let {
                // javaGetterを呼び出す方が高速
                javaGetter.isAccessible = true
                argumentBucket.putIfAbsent(it.param, javaGetter.invoke(src)?.let { value -> mapObject(it, value) })
                // 終了判定
                if (argumentBucket.isInitialized) return
            }
        }
    }

    private fun bindArguments(argumentBucket: ArgumentBucket, src: Map<*, *>) {
        src.forEach { (key, value) ->
            parameterMap[key]?.let { param ->
                // 取得した内容がnullでなければ適切にmapする
                argumentBucket.putIfAbsent(param.param, value?.let { mapObject(param, it) })
                // 終了判定
                if (argumentBucket.isInitialized) return
            }
        }
    }

    private fun bindArguments(argumentBucket: ArgumentBucket, srcPair: Pair<*, *>) {
        parameterMap[srcPair.first.toString()]?.let {
            argumentBucket.putIfAbsent(it.param, srcPair.second?.let { value -> mapObject(it, value) })
        }
    }

    fun map(srcMap: Map<String, Any?>): T {
        val bucket: ArgumentBucket = function.getArgumentBucket()
        bindArguments(bucket, srcMap)

        return function.call(bucket)
    }

    fun map(srcPair: Pair<String, Any?>): T {
        val bucket: ArgumentBucket = function.getArgumentBucket()
        bindArguments(bucket, srcPair)

        return function.call(bucket)
    }

    fun map(src: Any): T {
        val bucket: ArgumentBucket = function.getArgumentBucket()
        bindArguments(bucket, src)

        return function.call(bucket)
    }

    fun map(vararg args: Any): T {
        val bucket: ArgumentBucket = function.getArgumentBucket()

        listOf(*args).forEach { arg ->
            when (arg) {
                is Map<*, *> -> bindArguments(bucket, arg)
                is Pair<*, *> -> bindArguments(bucket, arg)
                else -> bindArguments(bucket, arg)
            }
        }

        return function.call(bucket)
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> getTarget(clazz: KClass<T>): KFunctionForCall<T> {
    val factoryConstructor: List<KFunctionForCall<T>> =
        clazz.companionObjectInstance?.let { companionObject ->
            companionObject::class.functions
                .filter { it.annotations.any { annotation -> annotation is KConstructor } }
                .map { KFunctionForCall(
                    it,
                    companionObject
                ) as KFunctionForCall<T> }
        } ?: emptyList()

    val constructors: List<KFunctionForCall<T>> = factoryConstructor + clazz.constructors
        .filter { it.annotations.any { annotation -> annotation is KConstructor } }
        .map { KFunctionForCall(it) }

    if (constructors.size == 1) return constructors.single()

    if (constructors.isEmpty()) return KFunctionForCall(clazz.primaryConstructor!!)

    throw IllegalArgumentException("Find multiple target.")
}

private fun <T : Any, R : Any> mapObject(param: ParameterForMap<R>, value: T): Any? {
    val valueClazz: KClass<*> = value::class

    // パラメータに対してvalueが代入可能（同じもしくは親クラス）であればそのまま用いる
    if (param.clazz.isSuperclassOf(valueClazz)) return value

    val creator: KFunction<*>? = param.getCreator(valueClazz)

    return when {
        // creatorに一致する組み合わせが有れば設定されていればそれを使う
        creator != null -> creator.call(value)
        // 要求された値がenumかつ元が文字列ならenum mapperでマップ
        param.javaClazz.isEnum && value is String -> EnumMapper.getEnum(param.clazz.java, value)
        // 要求されているパラメータがStringならtoStringする
        param.clazz == String::class -> value.toString()
        else -> throw IllegalArgumentException("Can not convert $valueClazz to ${param.clazz}")
    }
}
