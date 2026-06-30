package com.chingfordmosque.prayertimes.domain

/**
 * An explicit success/failure value with a typed error channel.
 *
 * The standard Kotlin [kotlin.Result] only carries a [Throwable]; the design calls for a
 * domain-typed error (notably [ProviderError]), so this lightweight sealed type is used
 * across the provider/refresh boundaries instead.
 */
sealed class Result<out T, out E> {

    data class Ok<out T>(val value: T) : Result<T, Nothing>()

    data class Err<out E>(val error: E) : Result<Nothing, E>()

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    /** The success value, or null when this is an [Err]. */
    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    /** The error, or null when this is an [Ok]. */
    fun errorOrNull(): E? = when (this) {
        is Ok -> null
        is Err -> error
    }

    /** The success value, or [fallback] when this is an [Err]. */
    fun getOrElse(fallback: @UnsafeVariance T): T = when (this) {
        is Ok -> value
        is Err -> fallback
    }

    /** The success value, or throw [IllegalStateException] when this is an [Err]. */
    fun getOrThrow(): T = when (this) {
        is Ok -> value
        is Err -> throw IllegalStateException("Result was Err: $error")
    }

    /** Transform the success value, leaving any error untouched. */
    fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    /** Transform the error, leaving any success value untouched. */
    fun <F> mapError(transform: (E) -> F): Result<T, F> = when (this) {
        is Ok -> this
        is Err -> Err(transform(error))
    }

    /** Chain a fallible computation onto the success value. */
    fun <R> flatMap(transform: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> = when (this) {
        is Ok -> transform(value)
        is Err -> this
    }

    /** Collapse both branches to a single value. */
    fun <R> fold(onOk: (T) -> R, onErr: (E) -> R): R = when (this) {
        is Ok -> onOk(value)
        is Err -> onErr(error)
    }
}
