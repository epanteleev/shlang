package common


// Ordinary linked list with leaked abstraction
open class LeakedLinkedList<T: LListNode> : List<T> {
    private var head: T? = null
    private var tail: T? = null
    override var size = 0

    override fun get(index: Int): T {
        var current: LListNode? = head
        for (i in 0 until index) {
            current = current!!.next
        }
        return current as T
    }

    override fun indexOf(element: T): Int {
        var current: LListNode? = head
        var index = 0
        while (current != null) {
            if (current == element) {
                return index
            }
            current = current.next
            index++
        }
        return -1
    }

    fun add(index: Int, value: T) {
        if (index == size) {
            add(value)
            return
        }
        var current: LListNode? = head
        for (i in 0 until index) {
            current = current!!.next
        }
        value.next = current
        value.prev = current!!.prev
        current.prev = value
        if (value.prev != null) {
            value.prev!!.next = value
        } else {
            head = value
        }
        size++
    }

    operator fun set(index: Int, value: T) {
        var current: LListNode? = head
        for (i in 0 until index) {
            current = current!!.next
        }
        value.next = current!!.next
        value.prev = current.prev
        if (value.prev != null) {
            value.prev!!.next = value
        } else {
            head = value
        }
        if (value.next != null) {
            value.next!!.prev = value
        } else {
            tail = value
        }
    }


    fun removeIf(predicate: (T) -> Boolean): Boolean {
        var current: LListNode? = head
        while (current != null) {
            if (predicate(current as T)) {
                val next = current.next
                remove(current)
                current = next
                continue
            }
            current = current.next
        }
        return false
    }

    fun removeAt(index: Int): T {
        var current: LListNode? = head
        for (i in 0 until index) {
            current = current!!.next
        }
        remove(current as T)
        return current
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        for (element in elements) {
            if (!contains(element)) {
                return false
            }
        }
        return true
    }

    override fun contains(element: T): Boolean {
        var current: LListNode? = head
        while (current != null) {
            if (current == element) {
                return true
            }
            current = current.next
        }
        return false
    }

    fun add(value: T) {
        if (head == null) {
            head = value
            tail = value
        } else {
            tail!!.next = value
            value.prev = tail
            tail = value
        }
        size++
    }

    fun remove(node: T): T {
        if (node.prev != null) {
            node.prev!!.next = node.next
        } else {
            head = node.next as T?
        }
        if (node.next != null) {
            node.next!!.prev = node.prev
        } else {
            tail = node.prev as T?
        }
        size--
        node.next = null
        node.prev = null
        return node
    }

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private var current = head
            override fun hasNext(): Boolean = current != null
            override fun next(): T {
                val result = current
                current = current!!.next as T?
                return result!!
            }
        }
    }

    override fun listIterator(): ListIterator<T> {
        return object : ListIterator<T> {
            private var current = head
            private var index = 0
            override fun hasNext(): Boolean = current != null
            override fun hasPrevious(): Boolean = current != null
            override fun next(): T {
                val result = current
                current = current!!.next as T?
                index++
                return result!!
            }
            override fun nextIndex(): Int = index
            override fun previous(): T {
                val result = current
                current = current!!.prev as T?
                index--
                return result!!
            }
            override fun previousIndex(): Int = index - 1
        }
    }

    override fun listIterator(index: Int): ListIterator<T> {
        return object : ListIterator<T> {
            private var current = head
            private var currentIndex = 0
            init {
                for (i in 0 until index) {
                    current = current!!.next as T?
                    currentIndex++
                }
            }
            override fun hasNext(): Boolean = current != null
            override fun hasPrevious(): Boolean = current != null
            override fun next(): T {
                val result = current
                current = current!!.next as T?
                currentIndex++
                return result!!
            }
            override fun nextIndex(): Int = currentIndex
            override fun previous(): T {
                val result = current
                current = current!!.prev as T?
                currentIndex--
                return result!!
            }
            override fun previousIndex(): Int = currentIndex - 1
        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        val result = mutableListOf<T>()
        var current = head
        for (i in 0 until fromIndex) {
            current = current!!.next as T?
        }
        for (i in fromIndex until toIndex) {
            result.add(current!!)
            current = current.next as T?
        }
        return result
    }

    override fun lastIndexOf(element: T): Int {
        var current = tail
        var index = size - 1
        while (current != null) {
            if (current == element) {
                return index
            }
            current = current.prev as T?
            index--
        }
        return -1
    }

    override fun toString(): String {
        return buildString {
            append("[")
            var current = head
            while (current != null) {
                append(current.toString())
                current = current.next as T?
                if (current != null) {
                    append(", ")
                }
            }
            append("]")
        }
    }

    fun first(): LListNode = head!!

    fun firstOrNull(): LListNode? = head

    fun last(): LListNode = tail!!

    fun lastOrNull(): LListNode? = tail

    fun clear() {
        head = null
        tail = null
        size = 0
    }
}

abstract class LListNode {
    internal var next: LListNode? = null
    internal var prev: LListNode? = null
}