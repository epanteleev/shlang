package collections

inline fun <T, R, V> Iterable<T>.forEachWith(other: Iterable<R>, transform: (a: T, b: R) -> V) {
    val first = iterator()
    val second = other.iterator()
    while (first.hasNext() && second.hasNext()) {
        transform(first.next(), second.next())
    }
}