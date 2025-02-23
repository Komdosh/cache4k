package io.github.reactivecircus.cache4k

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

class CacheExpiryTest {

    private val fakeTimeSource = FakeTimeSource()

    @Test
    fun noWriteOrAccessExpiry_cacheNeverExpires() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .build<Long, String>()

        cache.put(1, "dog")
        cache.put(2, "cat")

        assertEquals("dog", cache.get(1))
        assertEquals("cat", cache.get(2))
    }

    @Test
    fun expiredAfterWrite_cacheEntryEvicted() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .expireAfterWrite(1.minutes)
            .build<Long, String>()

        cache.put(1, "dog")

        // just before expiry
        fakeTimeSource += 1.minutes - 1.nanoseconds

        assertEquals("dog", cache.get(1))

        // now expires
        fakeTimeSource += 1.nanoseconds

        assertNull(cache.get(1))
    }

    @Test
    fun replaceCacheValue_writeExpiryTimeReset() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .expireAfterWrite(1.minutes)
            .build<Long, String>()

        cache.put(1, "dog")

        // just before expiry
        fakeTimeSource += 1.minutes - 1.nanoseconds

        // update cache
        cache.put(1, "cat")

        // should not expire yet as cache was just updated
        fakeTimeSource += 1.nanoseconds

        assertEquals("cat", cache.get(1))

        // should now expire
        fakeTimeSource += 1.minutes - 1.nanoseconds

        assertNull(cache.get(1))
    }

    @Test
    fun readCacheEntry_doesNotResetWriteExpiryTime() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .expireAfterWrite(1.minutes)
            .build<Long, String>()

        cache.put(1, "dog")

        // just before expiry
        fakeTimeSource += 1.minutes - 1.nanoseconds

        // read cache before expected write expiry
        assertEquals("dog", cache.get(1))

        // should expire despite cache just being read
        fakeTimeSource += 1.nanoseconds

        assertNull(cache.get(1))
    }

    @Test
    fun expiredAfterAccess_cacheEntryEvicted() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .expireAfterAccess(2.minutes)
            .build<Long, String>()

        cache.put(1, "dog")

        // read cache immediately
        assertEquals("dog", cache.get(1))

        // now expires
        fakeTimeSource += 2.minutes

        assertNull(cache.get(1))
    }

    @Test
    fun replaceCacheValue_accessExpiryTimeReset() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .expireAfterAccess(2.minutes)
            .build<Long, String>()

        cache.put(1, "dog")

        // just before expiry
        fakeTimeSource += 2.minutes - 1.nanoseconds

        // update cache
        cache.put(1, "cat")

        // should not expire yet as cache was just updated
        fakeTimeSource += 1.nanoseconds

        assertEquals("cat", cache.get(1))

        // should now expire
        fakeTimeSource += 2.minutes

        assertNull(cache.get(1))
    }

    @Test
    fun readCacheEntry_accessExpiryTimeReset() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .expireAfterAccess(2.minutes)
            .build<Long, String>()

        cache.put(1, "dog")

        // just before expiry
        fakeTimeSource += 2.minutes - 1.nanoseconds

        // read cache before expected access expiry
        assertEquals("dog", cache.get(1))

        // should not expire yet as cache was just read (accessed)
        fakeTimeSource += 1.nanoseconds

        assertEquals("dog", cache.get(1))

        // should now expire
        fakeTimeSource += 2.minutes

        assertNull(cache.get(1))
    }

    @Test
    fun expiryRespectsBothExpireAfterWriteAndExpireAfterAccess() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .expireAfterWrite(2.minutes)
            .expireAfterAccess(1.minutes)
            .build<Long, String>()

        cache.put(1, "dog")

        // expires due to access expiry
        fakeTimeSource += 1.minutes

        assertNull(cache.get(1))

        // cache a new value
        cache.put(1, "cat")

        // before new access expiry
        fakeTimeSource += 1.minutes - 1.nanoseconds

        // this should reset access expiry time but not write expiry time
        assertEquals("cat", cache.get(1))

        // should now expire due to write expiry
        fakeTimeSource += 1.minutes

        assertNull(cache.get(1))
    }

    @Test
    fun onlyExpiredCacheEntriesAreEvicted() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .expireAfterWrite(1.minutes)
            .build<Long, String>()

        cache.put(1, "dog")
        cache.put(2, "cat")

        // cache a new value
        fakeTimeSource += 1.minutes / 2
        cache.put(3, "bird")

        // now first 2 entries should expire, 3rd entry should not expire yet
        fakeTimeSource += 1.minutes / 2

        assertNull(cache.get(1))
        assertNull(cache.get(2))
        assertEquals("bird", cache.get(3))

        // just before 3rd entry expires
        fakeTimeSource += 1.minutes / 2 - 1.nanoseconds

        assertEquals("bird", cache.get(3))

        // 3rd entry should now expire
        fakeTimeSource += 1.nanoseconds

        assertNull(cache.get(3))
    }

    @Test
    fun maxSizeLimitExceededBeforeExpectedExpiry_cacheEntryEvicted() {
        val cache = Cache.Builder()
            .timeSource(fakeTimeSource)
            .maximumCacheSize(2)
            .expireAfterWrite(1.minutes)
            .build<Long, String>()

        cache.put(1, "dog")
        cache.put(2, "cat")

        // add a new cache entry before first entry is expected to expire
        fakeTimeSource += 1.minutes / 2
        cache.put(3, "bird")

        // first entry should be evicted despite not being expired
        assertNull(cache.get(1))
        assertEquals("cat", cache.get(2))
        assertEquals("bird", cache.get(3))
    }
}
