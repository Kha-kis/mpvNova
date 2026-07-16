package app.mpvnova.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvRuntimeOwnershipTest {
    @Test
    fun onlyOneOwnerCanAcquireRuntime() {
        val first = Any()
        val second = Any()
        try {
            assertTrue(MpvRuntimeOwnership.tryAcquire(first))
            assertFalse(MpvRuntimeOwnership.tryAcquire(second))
            assertTrue(MpvRuntimeOwnership.isOwnedBy(first))
        } finally {
            MpvRuntimeOwnership.release(first)
        }
    }

    @Test
    fun aDifferentOwnerCannotReleaseRuntime() {
        val owner = Any()
        val other = Any()
        try {
            assertTrue(MpvRuntimeOwnership.tryAcquire(owner))
            MpvRuntimeOwnership.release(other)
            assertTrue(MpvRuntimeOwnership.isOwnedBy(owner))
        } finally {
            MpvRuntimeOwnership.release(owner)
        }
    }
}
