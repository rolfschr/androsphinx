package org.hsbp.androsphinx

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@Suppress("SpellCheckingInspection")
const val EXPECTED_BASIC_TEST = "Dnw7PR+5GmrE/t6RtaF12gPIQSWaIGaSje7RgQvasy4="

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun sphinxBasicTest() {
        Sphinx.Challenge("shitty password\u0000".toCharArray()).use { c ->
            val secret = ByteArray(32) { ' '.toByte() }
            val resp = Sphinx.respond(c.challenge, secret)
            val rwd = c.finish(resp)
            assertArrayEquals(Base64.decode(EXPECTED_BASIC_TEST, Base64.DEFAULT), rwd)
        }
    }

    @Test
    fun storageTest() {
        NaCl.sodium()
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = AndroidCredentialStore(appContext)
        val key = storage.key
        assertEquals(Sodium.crypto_sign_secretkeybytes(), key.size)
        assertArrayEquals(storage.key, key)
        assertEquals(SALT_BYTES, storage.salt.size)

        val host1 = ByteArray(16) { 1 }
        val host2 = ByteArray(16) { 2 }
        val host1user1 = "foo"
        val host1user2 = "bar"
        val host2user1 = "baz"

        assert(storage.getUsers(host1).isEmpty())
        storage.deleteUser(host1, host1user1)

        storage.cacheUser(host1, host1user1)
        assertEquals(storage.getUsers(host1).size, 1)
        assert(storage.getUsers(host1).contains(host1user1))

        storage.cacheUser(host1, host1user2)
        assertEquals(storage.getUsers(host1).size, 2)
        assert(storage.getUsers(host1).containsAll(listOf(host1user1, host1user2)))

        storage.cacheUser(host2, host2user1)
        assertEquals(2, storage.getUsers(host1).size)
        assert(storage.getUsers(host1).containsAll(listOf(host1user1, host1user2)))
        assertEquals(1, storage.getUsers(host2).size)
        assert(storage.getUsers(host1).contains(host1user2))

        storage.deleteUser(host1, host1user1)
        assertEquals(1, storage.getUsers(host1).size)
        assert(storage.getUsers(host1).contains(host1user2))

        assertEquals("", storage.host)
        assertEquals(0, storage.port)
        assert(storage.serverPublicKey.isEmpty())

        val host = "example.tld"
        val port = 31337
        val serverPublicKey = ByteArray(32) { 3 }
        appContext.storeServerInfo(host, port, serverPublicKey)

        assertEquals(host, storage.host)
        assertEquals(port, storage.port)
        assertArrayEquals(serverPublicKey, storage.serverPublicKey)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun sphinxNetworkTest() {
        NaCl.sodium()
        val cs = MockCredentialStore()
        val username = "network"
        val hostname = "test${System.currentTimeMillis()}.tld"
        val realm = Protocol.Realm(username, hostname)
        val charClasses = setOf(CharacterClass.LOWER, CharacterClass.DIGITS)
        val size = 18
        val callback = object : Protocol.PasswordCallback {
            var gotPassword: CharArray? = null

            override fun passwordReceived(password: CharArray) {
                gotPassword = password
            }
        }

        Protocol.create("sphinxNetworkTestMasterPassword".toCharArray(), realm, charClasses, cs, callback, size)
        assertNotNull(callback.gotPassword)
        val pw = callback.gotPassword!!
        assertEquals(size, pw.size)
        assert(pw.all { pwChar -> charClasses.any { it.range.contains(pwChar) } })

        callback.gotPassword = null

        Protocol.get("sphinxNetworkTestMasterPassword".toCharArray(), realm, cs, callback)
        assertArrayEquals(pw, callback.gotPassword)
    }
}
