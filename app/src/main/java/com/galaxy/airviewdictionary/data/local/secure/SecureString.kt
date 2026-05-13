package com.galaxy.airviewdictionary.data.local.secure

import kotlin.random.Random

/**
 * secret 을 메모리에 노출시키지 않기 위하여 사용하는 String 변수 wrapper.
 * 사용 후 clear() 를 반드시 호출한다.
 */
class SecureString {
    private var length: Int = 0
    private var data0: CharArray? = null
    private var data1: CharArray? = null

    constructor()

    constructor(value: String) {
        set(value)
    }

    fun set(value: String) {
        val data = value.toCharArray()
        length = data.size
        val randomFactor = Random.nextFloat() * 0.3f + 0.3f // 0.3 to 0.6
        val splitIndex = (data.size * randomFactor).toInt()

        data0 = data.copyOfRange(0, splitIndex)
        data1 = data.copyOfRange(splitIndex, data.size)

        // Clear the original char array
        data.fill('\u0000')
    }

    fun get(): String {
        return if (data0 != null && data1 != null) {
            val result = CharArray(length)
            data0?.copyInto(result, 0, 0, data0!!.size)
            data1?.copyInto(result, data0!!.size, 0, data1!!.size)
            String(result)
        } else {
            ""
        }
    }

    fun clear() {
        data0?.fill('\u0000')
        data1?.fill('\u0000')
        length = 0
    }

    val isEmpty: Boolean
        get() = length == 0

    override fun toString(): String {
        return get()
    }
}