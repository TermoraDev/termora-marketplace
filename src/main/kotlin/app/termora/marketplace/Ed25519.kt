package app.termora.marketplace

import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object Ed25519 {
    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(data)
        return signer.sign()
    }

    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
        return runCatching {
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            verifier.update(data)
            verifier.verify(signature)
        }.getOrNull() ?: false
    }


    fun generatePublic(publicKey: ByteArray): PublicKey {
        return KeyFactory.getInstance("Ed25519")
            .generatePublic(X509EncodedKeySpec(publicKey))
    }

    fun generatePrivate(privateKey: ByteArray): PrivateKey {
        return KeyFactory.getInstance("Ed25519")
            .generatePrivate(PKCS8EncodedKeySpec(privateKey))
    }

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("Ed25519")
        return generator.generateKeyPair()
    }
}