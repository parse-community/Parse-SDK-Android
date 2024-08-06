package com.parse

import com.parse.boltsinternal.Task
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class ParseObjectStoreMigratorTest {

    private lateinit var store: ParseObjectStore<ParseObject>
    private lateinit var legacy: ParseObjectStore<ParseObject>
    private lateinit var migrator: ParseObjectStoreMigrator<ParseObject>

    @BeforeEach
    fun setUp() {
        store = mock(ParseObjectStore::class.java) as ParseObjectStore<ParseObject>
        legacy = mock(ParseObjectStore::class.java) as ParseObjectStore<ParseObject>
        migrator = ParseObjectStoreMigrator(store, legacy)
    }

    @Test
    fun testGetAsyncWhenStoreHasData() {
        val parseObject = mock(ParseObject::class.java)
        `when`(store.getAsync()).thenReturn(Task.forResult(parseObject))

        val result = migrator.getAsync().result

        assertEquals(parseObject, result)
        verify(store, times(1)).getAsync()
        verify(legacy, never()).getAsync()
    }

    @Test
    fun testGetAsyncWhenStoreIsEmptyAndLegacyHasData() {
        val parseObject = mock(ParseObject::class.java)
        `when`(store.getAsync()).thenReturn(Task.forResult(null))
        `when`(legacy.getAsync()).thenReturn(Task.forResult(parseObject))
        `when`(legacy.deleteAsync()).thenReturn(Task.forResult(null))
        `when`(store.setAsync(parseObject)).thenReturn(Task.forResult(null))

        val result = migrator.getAsync().result

        assertEquals(parseObject, result)
        verify(store, times(1)).getAsync()
        verify(legacy, times(1)).getAsync()
        verify(legacy, times(1)).deleteAsync()
        verify(store, times(1)).setAsync(parseObject)
    }

    @Test
    fun testSetAsync() {
        val parseObject = mock(ParseObject::class.java)
        `when`(store.setAsync(parseObject)).thenReturn(Task.forResult(null))

        migrator.setAsync(parseObject).waitForCompletion()

        verify(store, times(1)).setAsync(parseObject)
    }

    @Test
    fun testExistsAsyncWhenStoreHasData() {
        `when`(store.existsAsync()).thenReturn(Task.forResult(true))

        val result = migrator.existsAsync().result

        assertTrue(result)
        verify(store, times(1)).existsAsync()
        verify(legacy, never()).existsAsync()
    }

    @Test
    fun testExistsAsyncWhenStoreIsEmptyAndLegacyHasData() {
        `when`(store.existsAsync()).thenReturn(Task.forResult(false))
        `when`(legacy.existsAsync()).thenReturn(Task.forResult(true))

        val result = migrator.existsAsync().result

        assertTrue(result)
        verify(store, times(1)).existsAsync()
        verify(legacy, times(1)).existsAsync()
    }

    @Test
    fun testDeleteAsync() {
        `when`(store.deleteAsync()).thenReturn(Task.forResult(null))
        `when`(legacy.deleteAsync()).thenReturn(Task.forResult(null))

        migrator.deleteAsync().waitForCompletion()

        verify(store, times(1)).deleteAsync()
        verify(legacy, times(1)).deleteAsync()
    }
}
