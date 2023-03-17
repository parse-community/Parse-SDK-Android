package com.parse

/*
 * Copyright 2020 Appmattus Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.InputStream
import java.io.OutputStream
import java.security.*
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

class AndroidKeyStoreProvider : Provider("AndroidKeyStore", 1.0, "") {
    init {
        put("KeyStore.AndroidKeyStore", AndroidKeyStore::class.java.name)
        put("KeyGenerator.AES", AesKeyGenerator::class.java.name)
        put("KeyGenerator.HmacSHA256", HmacSHA256KeyGenerator::class.java.name)
        put("KeyPairGenerator.RSA", RsaKeyPairGenerator::class.java.name)
    }

    @Suppress("TooManyFunctions")
    class AndroidKeyStore : KeyStoreSpi() {
        override fun engineIsKeyEntry(alias: String?): Boolean = wrapped.isKeyEntry(alias)

        override fun engineIsCertificateEntry(alias: String?): Boolean = wrapped.isCertificateEntry(alias)

        override fun engineGetCertificate(alias: String?): Certificate = wrapped.getCertificate(alias)

        override fun engineGetCreationDate(alias: String?): Date = wrapped.getCreationDate(alias)

        override fun engineDeleteEntry(alias: String?) {
            storedKeys.remove(alias)
        }

        override fun engineSetKeyEntry(alias: String?, key: Key?, password: CharArray?, chain: Array<out Certificate>?) =
            wrapped.setKeyEntry(alias, key, password, chain)

        override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<out Certificate>?) = wrapped.setKeyEntry(alias, key, chain)

        override fun engineStore(stream: OutputStream?, password: CharArray?) = wrapped.store(stream, password)

        override fun engineSize(): Int = wrapped.size()

        override fun engineAliases(): Enumeration<String> = Collections.enumeration(storedKeys.keys)

        override fun engineContainsAlias(alias: String?): Boolean = storedKeys.containsKey(alias)

        override fun engineLoad(stream: InputStream?, password: CharArray?) = wrapped.load(stream, password)

        override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = wrapped.getCertificateChain(alias)

        override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = wrapped.setCertificateEntry(alias, cert)

        override fun engineGetCertificateAlias(cert: Certificate?): String? = wrapped.getCertificateAlias(cert)

        override fun engineGetKey(alias: String?, password: CharArray?): Key? = (storedKeys[alias] as? KeyStore.SecretKeyEntry)?.secretKey

        override fun engineGetEntry(p0: String, p1: KeyStore.ProtectionParameter?): KeyStore.Entry? = storedKeys[p0]

        override fun engineSetEntry(p0: String, p1: KeyStore.Entry, p2: KeyStore.ProtectionParameter?) {
            storedKeys[p0] = p1
        }

        override fun engineLoad(p0: KeyStore.LoadStoreParameter?) = wrapped.load(p0)

        override fun engineStore(p0: KeyStore.LoadStoreParameter?) = wrapped.store(p0)

        override fun engineEntryInstanceOf(p0: String?, p1: Class<out KeyStore.Entry>?) = wrapped.entryInstanceOf(p0, p1)

        companion object {
            private val wrapped = KeyStore.getInstance("BKS", "BC")
            internal val storedKeys = mutableMapOf<String, KeyStore.Entry>()
        }
    }

    class AesKeyGenerator : KeyGeneratorSpi() {
        private val wrapped = KeyGenerator.getInstance("AES", "BC")
        private var lastSpec: AlgorithmParameterSpec? = null

        override fun engineInit(random: SecureRandom?) = wrapped.init(random)

        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) = wrapped.init(random).also {
            lastSpec = params
        }

        override fun engineInit(keysize: Int, random: SecureRandom?) = wrapped.init(keysize, random)

        override fun engineGenerateKey(): SecretKey = wrapped.generateKey().also {
            AndroidKeyStore.storedKeys[lastSpec!!.keystoreAlias] = KeyStore.SecretKeyEntry(it)
        }
    }

    class HmacSHA256KeyGenerator : KeyGeneratorSpi() {
        private val wrapped = KeyGenerator.getInstance("HmacSHA256", "BC")
        private var lastSpec: AlgorithmParameterSpec? = null

        override fun engineInit(random: SecureRandom?) = wrapped.init(random)
        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) = wrapped.init(random).also {
            lastSpec = params
        }

        override fun engineInit(keysize: Int, random: SecureRandom?) = Unit
        override fun engineGenerateKey(): SecretKey = wrapped.generateKey().also {
            AndroidKeyStore.storedKeys[lastSpec!!.keystoreAlias] = KeyStore.SecretKeyEntry(it)
        }
    }

    class RsaKeyPairGenerator : KeyPairGeneratorSpi() {
        private val wrapped = KeyPairGenerator.getInstance("RSA", "BC")

        private var lastSpec: AlgorithmParameterSpec? = null

        override fun generateKeyPair(): KeyPair = wrapped.generateKeyPair().also { keyPair ->
            null
//            AndroidKeyStore.storedKeys[lastSpec!!.keystoreAlias] = KeyStore.PrivateKeyEntry(keyPair.private, arrayOf(keyPair.toCertificate()))
        }

        override fun initialize(p0: Int, p1: SecureRandom?) = Unit

        override fun initialize(p0: AlgorithmParameterSpec?, p1: SecureRandom?) {
            lastSpec = p0
        }
    }
}
