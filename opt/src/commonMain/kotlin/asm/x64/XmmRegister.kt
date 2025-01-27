package asm.x64

import asm.Register


enum class XmmRegister : Register {
    xmm0 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm0"
                16 -> "%xmm0"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 0
    },
    xmm1 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm1"
                16 -> "%xmm1"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 1
    },

    xmm2 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm2"
                16 -> "%xmm2"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 2
    },

    xmm3 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm3"
                16 -> "%xmm3"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 3
    },

    xmm4 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm4"
                16 -> "%xmm4"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 4
    },

    xmm5 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm5"
                16 -> "%xmm5"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 5
    },

    xmm6 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm6"
                16 -> "%xmm6"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 6
    },

    xmm7 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm7"
                16 -> "%xmm7"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 7
    },

    xmm8 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm8"
                16 -> "%xmm8"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 8
    },

    xmm9 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm9"
                16 -> "%xmm9"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 9
    },

    xmm10 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm10"
                16 -> "%xmm10"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 10
    },

    xmm11 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm11"
                16 -> "%xmm11"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 11
    },

    xmm12 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm12"
                16 -> "%xmm12"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 12
    },

    xmm13 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm13"
                16 -> "%xmm13"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 13
    },

    xmm14 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm14"
                16 -> "%xmm14"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 14
    },

    xmm15 {
        override fun toString(size: Int): String {
            val string = when (size) {
                32 -> "%ymm15"
                16 -> "%xmm15"
                else -> throw IllegalArgumentException("size=$size")
            }
            return string
        }

        override fun encoding(): Int = 15
    },
}