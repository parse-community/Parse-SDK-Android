package com.parse

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object RobolectricKeyStore {

    val setup by lazy {
        Security.removeProvider("AndroidKeyStore")
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.removeProvider("AndroidOpenSSL")

        Security.addProvider(AndroidKeyStoreProvider())
        Security.addProvider(BouncyCastleProvider())
        Security.addProvider(AndroidOpenSSLProvider())
    }
}
