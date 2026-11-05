package com.v2ray.ang.albion

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionMarkerQueueTest {
    private class MemoryPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()
        var commitsSucceed = true

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = MemoryEditor()

        private inner class MemoryEditor : SharedPreferences.Editor {
            private val changes = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { changes[key] = value; removals.remove(key) }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { changes[key] = values?.toSet(); removals.remove(key) }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { changes[key] = value; removals.remove(key) }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { changes[key] = value; removals.remove(key) }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { changes[key] = value; removals.remove(key) }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { changes[key] = value; removals.remove(key) }
            override fun remove(key: String): SharedPreferences.Editor = apply { changes.remove(key); removals.add(key) }
            override fun clear(): SharedPreferences.Editor = apply { clearRequested = true; changes.clear(); removals.clear() }

            override fun commit(): Boolean {
                // Android SharedPreferences can expose the in-memory edit even when the
                // disk write reports failure. Model that behavior to exercise retry safety.
                if (clearRequested) values.clear()
                removals.forEach(values::remove)
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return commitsSucceed
            }

            override fun apply() { commit() }
        }
    }

    private fun context(prefs: MemoryPreferences): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private fun marker(id: String, event: String = "test"): String =
        JSONObject().put("marker_id", id).put("event", event).toString()

    @Test fun rejectsMalformedMarkersAndAssignsAnIdWhenMissing() {
        val prefs = MemoryPreferences()
        val context = context(prefs)
        assertNull(AlbionMarkerQueue.enqueue(context, "{broken"))
        assertEquals(0, AlbionMarkerQueue.count(context))

        val generatedId = AlbionMarkerQueue.enqueue(context, JSONObject().put("event", "test").toString())
        assertTrue(!generatedId.isNullOrBlank())
        assertEquals(generatedId, AlbionMarkerQueue.pending(context).single().getString("marker_id"))
    }

    @Test fun persistsAndReturnsDefensiveCopiesInFifoOrder() {
        val prefs = MemoryPreferences()
        val context = context(prefs)
        assertEquals("a", AlbionMarkerQueue.enqueue(context, marker("a")))
        assertEquals("b", AlbionMarkerQueue.enqueue(context, marker("b")))
        val pending = AlbionMarkerQueue.pending(context)
        assertEquals(listOf("a", "b"), pending.map { it.getString("marker_id") })
        pending.first().put("event", "mutated")
        assertEquals("test", AlbionMarkerQueue.pending(context).first().getString("event"))
    }

    @Test fun corruptJsonIsNotTreatedAsAnEmptyQueueOrOverwritten() {
        val prefs = MemoryPreferences()
        val context = context(prefs)
        prefs.edit().putString("pending", "{broken").commit()

        assertThrows(IllegalStateException::class.java) { AlbionMarkerQueue.pending(context) }
        assertFalse(AlbionMarkerQueue.acknowledge(context, "a"))
        assertNull(AlbionMarkerQueue.enqueue(context, marker("a")))
        assertEquals("{broken", prefs.getString("pending", null))
        assertEquals(-1, AlbionMarkerQueue.count(context))
    }

    @Test fun malformedEntriesAndDuplicateIdsAreRejectedWithoutDataLoss() {
        val prefs = MemoryPreferences()
        val context = context(prefs)
        prefs.edit().putString("pending", "[1]").commit()
        assertThrows(IllegalStateException::class.java) { AlbionMarkerQueue.pending(context) }
        assertEquals("[1]", prefs.getString("pending", null))

        val duplicateIds = JSONArray().put(JSONObject(marker("same"))).put(JSONObject(marker("same")))
        prefs.edit().putString("pending", duplicateIds.toString()).commit()
        assertFalse(AlbionMarkerQueue.acknowledge(context, "same"))
        assertEquals(duplicateIds.toString(), prefs.getString("pending", null))
    }

    @Test fun enqueueWithExistingIdIsIdempotentButCannotChangePayload() {
        val prefs = MemoryPreferences()
        val context = context(prefs)
        assertEquals("same", AlbionMarkerQueue.enqueue(context, marker("same", "found")))
        assertEquals("same", AlbionMarkerQueue.enqueue(context, marker("same", "found")))
        assertNull(AlbionMarkerQueue.enqueue(context, marker("same", "killed")))
        assertEquals(1, AlbionMarkerQueue.count(context))
        assertEquals("found", AlbionMarkerQueue.pending(context).single().getString("event"))
    }

    @Test fun capacityIsBoundedAndDoesNotOverwriteExistingItems() {
        val prefs = MemoryPreferences()
        val context = context(prefs)
        val full = JSONArray()
        repeat(4096) { full.put(JSONObject(marker("id-$it"))) }
        prefs.edit().putString("pending", full.toString()).commit()

        assertNull(AlbionMarkerQueue.enqueue(context, marker("overflow")))
        assertEquals(4096, AlbionMarkerQueue.count(context))
        assertEquals("id-0", AlbionMarkerQueue.pending(context).first().getString("marker_id"))
    }

    @Test fun failedCommitDoesNotAcknowledgeOrReportAnEnqueueAsDurable() {
        val prefs = MemoryPreferences()
        val context = context(prefs)
        prefs.commitsSucceed = false

        assertNull(AlbionMarkerQueue.enqueue(context, marker("a")))
        // The failed commit may already be visible in memory; retry must commit it again.
        assertEquals(1, AlbionMarkerQueue.count(context))
        prefs.commitsSucceed = true
        assertEquals("a", AlbionMarkerQueue.enqueue(context, marker("a")))

        prefs.commitsSucceed = false
        assertFalse(AlbionMarkerQueue.acknowledge(context, "a"))
        prefs.commitsSucceed = true
        assertTrue(AlbionMarkerQueue.acknowledge(context, "a"))
        assertEquals(0, AlbionMarkerQueue.count(context))
    }

    @Test fun acknowledgeRemovesOnlyTheRequestedIdAndIsIdempotent() {
        val prefs = MemoryPreferences()
        val context = context(prefs)
        AlbionMarkerQueue.enqueue(context, marker("a"))
        AlbionMarkerQueue.enqueue(context, marker("b"))

        assertTrue(AlbionMarkerQueue.acknowledge(context, "a"))
        assertTrue(AlbionMarkerQueue.acknowledge(context, "a"))
        assertEquals(listOf("b"), AlbionMarkerQueue.pending(context).map { it.getString("marker_id") })
    }
}
