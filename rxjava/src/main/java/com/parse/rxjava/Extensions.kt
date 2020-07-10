@file:JvmName("ParseRxJavaUtils")
@file:Suppress("unused")

package com.parse.rxjava

import com.parse.boltsinternal.Task
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

fun <T> Task<T>.toSingle(): Single<T> {
    return Single.defer {
        this.waitForCompletion()
        if (isFaulted) {
            throw error
        }
        Single.just(result)
    }
}

fun Task<Void>.toCompletable(): Completable {
    return Completable.defer {
        this.waitForCompletion()
        if (isFaulted) {
            throw error
        }
        Completable.complete()
    }
}