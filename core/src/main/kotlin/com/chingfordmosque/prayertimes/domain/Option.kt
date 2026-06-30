package com.chingfordmosque.prayertimes.domain

/**
 * An explicit optional value. Used where the design models optionality directly — e.g.
 * `iqamahAt: Option<Time>`, the cached schedule, and the next-prayer result — so that
 * "absent" is a first-class, non-null domain concept.
 */
sealed class Option<out T> {

    data class Some<out T>(val value: T) : Option<T>()

    data object None : Option<Nothing>()

    val isSome: Boolean get() = this is Some
    val isNone: Boolean get() = this is None

    fun getOrNull(): T? = when (this) {
        is Some -> value
        is None -> null
    }

    fun getOrElse(fallback: @UnsafeVariance T): T = when (this) {
        is Some -> value
        is None -> fallback
    }

    fun <R> map(transform: (T) -> R): Option<R> = when (this) {
        is Some -> Some(transform(value))
        is None -> None
    }

    fun <R> flatMap(transform: (T) -> Option<R>): Option<R> = when (this) {
        is Some -> transform(value)
        is None -> None
    }

    fun filter(predicate: (T) -> Boolean): Option<T> = when (this) {
        is Some -> if (predicate(value)) this else None
        is None -> None
    }

    fun <R> fold(onSome: (T) -> R, onNone: () -> R): R = when (this) {
        is Some -> onSome(value)
        is None -> onNone()
    }

    companion object {
        /** Wrap a nullable into an [Option]: null becomes [None], otherwise [Some]. */
        fun <T> ofNullable(value: T?): Option<T> = if (value == null) None else Some(value)
    }
}
