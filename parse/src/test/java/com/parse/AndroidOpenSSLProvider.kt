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

import android.annotation.SuppressLint
import java.security.AlgorithmParameters
import java.security.Key
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherSpi

class AndroidOpenSSLProvider : Provider("AndroidOpenSSL", 1.0, "") {
    init {
        put("Cipher.RSA/ECB/PKCS1Padding", RsaCipher::class.java.name)
    }

    @Suppress("TooManyFunctions")
    class RsaCipher : CipherSpi() {
        @SuppressLint("GetInstance")
        private val wrapped = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC")

        override fun engineSetMode(p0: String?) = Unit

        override fun engineInit(p0: Int, p1: Key?, p2: SecureRandom?) = wrapped.init(p0, p1, p2)

        override fun engineInit(p0: Int, p1: Key?, p2: AlgorithmParameterSpec?, p3: SecureRandom?) = wrapped.init(p0, p1, p2, p3)

        override fun engineInit(p0: Int, p1: Key?, p2: AlgorithmParameters?, p3: SecureRandom?) = wrapped.init(p0, p1, p2, p3)

        override fun engineGetIV(): ByteArray = wrapped.iv

        override fun engineDoFinal(p0: ByteArray?, p1: Int, p2: Int): ByteArray = wrapped.doFinal(p0, p1, p2)

        override fun engineDoFinal(p0: ByteArray?, p1: Int, p2: Int, p3: ByteArray?, p4: Int) = wrapped.doFinal(p0, p1, p2, p3, p4)

        override fun engineSetPadding(p0: String?) = Unit

        override fun engineGetParameters(): AlgorithmParameters = wrapped.parameters

        override fun engineUpdate(p0: ByteArray?, p1: Int, p2: Int): ByteArray = wrapped.update(p0, p1, p2)

        override fun engineUpdate(p0: ByteArray?, p1: Int, p2: Int, p3: ByteArray?, p4: Int): Int = wrapped.update(p0, p1, p2, p3, p4)

        override fun engineGetBlockSize(): Int = wrapped.blockSize

        override fun engineGetOutputSize(p0: Int): Int = wrapped.getOutputSize(p0)
    }
}
