package com.tmuxmobile.phase0

import net.schmizz.sshj.common.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class TofuHostKeyVerifierTest {

    @Test
    fun `no known fingerprint means first connect -- always matches`() {
        assertTrue(fingerprintMatches(known = null, presented = "AA:BB"))
    }

    @Test
    fun `matching fingerprint passes`() {
        assertTrue(fingerprintMatches(known = "AA:BB", presented = "AA:BB"))
    }

    @Test
    fun `mismatched fingerprint fails`() {
        assertFalse(fingerprintMatches(known = "AA:BB", presented = "CC:DD"))
    }

    /**
     * An empty-string fingerprint is a corrupt/truncated stored value, not "unknown".
     * Treating it as known-and-matching would accept any host key -- exactly the
     * behaviour TOFU exists to remove.
     */
    @Test
    fun `empty known fingerprint is not treated as first connect`() {
        assertFalse(fingerprintMatches(known = "", presented = "AA:BB"))
    }

    @Test
    fun `reports the presented fingerprint on first connect`() {
        val seen = mutableListOf<String>()
        val verifier = TofuHostKeyVerifier(knownFingerprint = null, onFirstConnect = { seen.add(it) })

        val ok = verifier.verify("box", 22, testPublicKey())

        assertTrue(ok)
        assertEquals(1, seen.size)
        assertFalse(verifier.mismatch)
    }

    @Test
    fun `does not report a fingerprint and rejects when one is already known and differs`() {
        val seen = mutableListOf<String>()
        val verifier = TofuHostKeyVerifier(knownFingerprint = "AA:BB", onFirstConnect = { seen.add(it) })

        val ok = verifier.verify("box", 22, testPublicKey())

        assertFalse(ok)
        assertTrue(seen.isEmpty())
        assertTrue(verifier.mismatch)
    }

    /**
     * Pins the fingerprint contract this class depends on: sshj's
     * SecurityUtils.getFingerprint returns a lowercase colon-separated hex MD5 of the
     * SSH wire encoding. The expected value was computed independently (outside the
     * app, from the DER of this same throwaway key) -- if sshj's format ever changes,
     * stored fingerprints silently stop matching every host, so this failing is the
     * signal that a migration is needed.
     */
    @Test
    fun `sshj fingerprint format is the lowercase colon-separated md5 we persist`() {
        assertEquals(
            "a6:6a:94:82:b2:3a:1b:2f:69:9f:a9:ec:17:cc:19:f2",
            SecurityUtils.getFingerprint(testPublicKey()),
        )
    }

    /** A deterministic throwaway RSA key, generated once for these tests. */
    private fun testPublicKey(): PublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(TEST_RSA_PUBLIC_KEY_DER)))

    private companion object {
        const val TEST_RSA_PUBLIC_KEY_DER =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn/sRgvV3hRatXFloWpmB/ibY" +
                "QlSsG74RV+QbFq3Q4Nb9tx9S4ZO9tqoPhMSRvD9JPsbJIit4qqrOL7vfhhypFzlyTkcU" +
                "DaVMvI0VAMnAHkLhMp1/6nBxa7xLIYSBP4OA/Twdh7ukcm2sABsNuSgv9KnjESE5f9J" +
                "M8OlfHFdkfuo6TW1ObiB7cdMTJ4XKP2o8uiIaPntf8c1ZOE7N7wLviEGnTabOm8wp3lJ" +
                "07p6jC6x6lc3gWDPD0eGjgczYe/WqTev5Oi9FjmAcCDV6Bh+70vmqmaUT3BdNPsHPbM6" +
                "XPbsy904oyHyEwZ/kNGS2VGhSsr+/BqaPXdz1+ePbN6LwKwIDAQAB"
    }
}
