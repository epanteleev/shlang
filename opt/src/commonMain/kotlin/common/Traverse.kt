package common

inline fun <T, R, V> Iterable<T>.forEachWith(other: Iterable<R>, transform: (a: T, b: R) -> V) {
    val first = iterator()
    val second = other.iterator()
    while (first.hasNext() && second.hasNext()) {
        transform(first.next(), second.next())
    }
}

inline fun <T, R, V> Iterable<T>.forEachWith(other: Array<R>, transform: (a: T, b: R) -> V) {
    val first = iterator()
    val second = other.iterator()
    while (first.hasNext() && second.hasNext()) {
        transform(first.next(), second.next())
    }
}

inline fun <T, R, V> Array<T>.forEachWith(other: Array<R>, transform: (a: T, b: R) -> V) {
    val first = iterator()
    val second = other.iterator()
    while (first.hasNext() && second.hasNext()) {
        transform(first.next(), second.next())
    }
}

inline fun <T, R, V> Iterable<T>.forEachWith(other: Iterable<R>, transform: (a: T, b: R, idx: Int) -> V) {
    val first = iterator()
    val second = other.iterator()
    var idx = 0
    while (first.hasNext() && second.hasNext()) {
        transform(first.next(), second.next(), idx++)
    }
}

inline fun <T, R, V> Iterable<T>.forEachWith(other: Array<R>, transform: (a: T, b: R, idx: Int) -> V) {
    val first = iterator()
    val second = other.iterator()
    var idx = 0
    while (first.hasNext() && second.hasNext()) {
        transform(first.next(), second.next(), idx++)
    }
}

inline fun <T, R, V> Array<T>.forEachWith(other: Array<R>, transform: (a: T, b: R, idx: Int) -> V) {
    val first = iterator()
    val second = other.iterator()
    var idx = 0
    while (first.hasNext() && second.hasNext()) {
        transform(first.next(), second.next(), idx++)
    }
}

inline fun <reified R> Iterable<*>.hasInstance(): Boolean {
    return any { it is R }
}