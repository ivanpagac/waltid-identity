package id.walt.crypto

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

abstract class IosKey: Key() {
    abstract suspend fun signJws(bodyJson: ByteArray, headersJson: ByteArray): String
}
@Suppress("unused")
class IosKeys {

    companion object {
        @Throws(Exception::class)
        fun load(kid: String, type: KeyType): Key = when (type) {
            KeyType.RSA -> RSAKey.load(kid)
            KeyType.secp256r1 -> P256Key.load(kid)
            KeyType.Ed25519 -> Ed25519Key.load(kid)
            else -> TODO("Not implemented")
        }

        @Throws(Exception::class)
        fun delete(kid: String, type: KeyType) = when (type) {
            KeyType.RSA -> RSAKey.delete(kid)
            KeyType.secp256r1 -> P256Key.delete(kid)
            KeyType.Ed25519 -> Ed25519Key.delete(kid)
            else -> TODO("Not implemented")
        }

        @Throws(Exception::class)
        fun load(kid: String): Key? = when {
            RSAKey.exist(kid) -> RSAKey.load(kid)
            P256Key.exist(kid) -> P256Key.load(kid)
            Ed25519Key.exist(kid) -> Ed25519Key.load(kid)
            else -> null
        }
    }
}

// utility functions for swift
fun String.ExportedToByteArray(
    startIndex: Int, endIndex: Int, throwOnInvalidSequence: Boolean
): ByteArray {
    return this.encodeToByteArray()
}

fun ByteArray.ExportedToString(
): String {
    return this.decodeToString()
}