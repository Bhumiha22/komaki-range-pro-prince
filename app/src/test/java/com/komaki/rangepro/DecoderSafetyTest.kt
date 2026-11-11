package com.komaki.rangepro

import com.komaki.rangepro.bms.BmsDecoders
import com.komaki.rangepro.bms.UnknownProtocolDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the "never invent protocol bytes / never write unverified commands" rule. */
class DecoderSafetyTest {
    @Test fun unknownDecoderDecodesNothingAndSendsNothing() {
        val d = UnknownProtocolDecoder
        assertFalse(d.isVerified)
        assertTrue(d.pollRequests.isEmpty())
        assertNull(d.writeCharacteristic)
        assertTrue(d.feed(ByteArray(20), 0L).isEmpty())
    }

    @Test fun activeDecoderThatIsUnverifiedHasNoRequests() {
        val d = BmsDecoders.active
        if (!d.isVerified) assertEquals(0, d.pollRequests.size)
    }
}
