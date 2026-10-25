package com.applylabpremium7.rakuchinder.loan;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/** Local signature validation. No entitlement is accepted from JavaScript or plain preferences. */
public final class PurchaseVerifier {
    private PurchaseVerifier() {}

    public static boolean verify(byte[] publicKey, String signedData, byte[] signature) {
        if (publicKey == null || publicKey.length == 0 || signedData == null || signedData.trim().isEmpty()
                || signature == null || signature.length == 0) return false;
        try {
            Signature verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(publicKey)));
            verifier.update(signedData.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception ignored) {
            return false;
        }
    }
}
