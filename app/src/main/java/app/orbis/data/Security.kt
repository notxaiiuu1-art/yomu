package app.orbis.data

import java.security.MessageDigest
import java.security.SecureRandom

object Security {
    private val random = SecureRandom()

    fun newSalt(): String = ByteArray(16).also { random.nextBytes(it) }.toHex()

    fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var bytes = (salt + pin).toByteArray()
        // A few thousand rounds make brute-forcing a stolen backup slower.
        repeat(5000) { bytes = md.digest(bytes) }
        return bytes.toHex()
    }

    fun check(pin: String, account: Account): Boolean = hash(pin, account.pinSalt) == account.pinHash

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
