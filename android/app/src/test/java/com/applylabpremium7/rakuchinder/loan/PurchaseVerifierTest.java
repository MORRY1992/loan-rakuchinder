package com.applylabpremium7.rakuchinder.loan;

import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

public class PurchaseVerifierTest {
    @Test public void acceptsAuthenticButRejectsModifiedUnsignedAndWrongKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair key = generator.generateKeyPair();
        String json = "{\"productId\":\"remove_ads\",\"purchaseState\":0}";
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initSign(key.getPrivate());
        signer.update(json.getBytes(StandardCharsets.UTF_8));
        byte[] signature = signer.sign();
        byte[] publicKey = key.getPublic().getEncoded();
        assertTrue(PurchaseVerifier.verify(publicKey, json, signature));
        assertFalse(PurchaseVerifier.verify(publicKey, json.replace("remove_ads", "other"), signature));
        assertFalse(PurchaseVerifier.verify(publicKey, json, new byte[0]));
        assertFalse(PurchaseVerifier.verify(new byte[0], json, signature));
        assertFalse(PurchaseVerifier.verify(new byte[]{1,2,3}, json, signature));
        byte[] wrongKey = generator.generateKeyPair().getPublic().getEncoded();
        assertFalse(PurchaseVerifier.verify(wrongKey, json, signature));
    }
}
