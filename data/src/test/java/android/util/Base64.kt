@file:Suppress("unused")

package android.util

/**
 * Test stub for android.util.Base64 that delegates to java.util.Base64.
 */
object Base64 {
    const val NO_WRAP = 2

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String =
        java.util.Base64.getEncoder().encodeToString(input)
}
