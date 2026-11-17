package com.xuleo.xmplus.md3.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.xuleo.xmplus.md3.core.account.AccountSnapshot
import com.xuleo.xmplus.md3.core.account.AccountSnapshotCodec
import com.xuleo.xmplus.md3.core.account.AccountStore
import com.xuleo.xmplus.md3.core.model.XmlyException
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** AES-GCM authenticated, all-or-nothing private snapshot. No plaintext file or fallback key. */
class KeystoreAccountStore(context: Context, private val keyAlias: String = KeyAlias) : AccountStore {
    private val base = File(context.noBackupFilesDir, "accounts.enc")
    private val file = AtomicFile(base)
    private fun exists() = base.exists() || File(base.path + ".bak").exists()

    override suspend fun read(): AccountSnapshot = withContext(Dispatchers.IO) {
        try {
            if (!exists()) return@withContext AccountSnapshot()
            val encoded = file.openRead().use { input ->
                val buffer = ByteArray(8192)
                val bytes = ByteArrayOutputStream().use { output ->
                    while (true) {
                        val count = input.read(buffer, 0, minOf(buffer.size, MaxFileBytes + 1 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        require(output.size() <= MaxFileBytes)
                    }
                    output.toByteArray()
                }
                require(bytes.size in 30..MaxFileBytes && bytes[0] == FormatVersion)
                bytes
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, encoded, 1, 12))
            cipher.updateAAD(Aad)
            val plaintext = cipher.doFinal(encoded, 13, encoded.size - 13)
            try { AccountSnapshotCodec.decode(plaintext) } finally { plaintext.fill(0) }
        } catch (_: Exception) {
            throw XmlyException("account_read_failed")
        }
    }

    override suspend fun write(snapshot: AccountSnapshot) = withContext(Dispatchers.IO) {
        var output: FileOutputStream? = null
        try {
            val plaintext = AccountSnapshotCodec.encode(snapshot)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(create = !exists()))
            cipher.updateAAD(Aad)
            val ciphertext = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
            require(cipher.iv.size == 12 && ciphertext.size + 13 <= MaxFileBytes)
            output = file.startWrite()
            output.write(byteArrayOf(FormatVersion))
            output.write(cipher.iv)
            output.write(ciphertext)
            file.finishWrite(output)
            output = null
            val expected = byteArrayOf(FormatVersion) + cipher.iv + ciphertext
            check(file.openRead().use { input ->
                val actual = ByteArray(expected.size)
                var offset = 0
                while (offset < actual.size) {
                    val count = input.read(actual, offset, actual.size - offset)
                    check(count > 0)
                    offset += count
                }
                actual.contentEquals(expected) && input.read() == -1
            })
        } catch (_: Exception) {
            output?.let(file::failWrite)
            throw XmlyException("account_write_failed")
        }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        check(create) // Existing ciphertext must never trigger key replacement.
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    private companion object {
        const val KeyAlias = "xmplus.accounts.v1"
        const val FormatVersion: Byte = 1
        const val MaxFileBytes = 16 * 1024 * 1024
        val Aad = "com.xuleo.xmplus.md3/accounts/v1".toByteArray(Charsets.UTF_8)
    }
}
