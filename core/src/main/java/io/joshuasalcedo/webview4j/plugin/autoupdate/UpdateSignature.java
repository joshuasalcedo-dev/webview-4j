package io.joshuasalcedo.webview4j.plugin.autoupdate;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519 signatures of a release's manifest: made at release time with the private key (kept in a secret, never in
 * the repository), checked by the app with the public key built into it. Keys as base64 text: the public one in
 * X.509 form, the private one in PKCS#8.
 */
public final class UpdateSignature {

    private UpdateSignature() {}

    /** A new key pair: {@code [public, private]}, base64. */
    public static String[] newKeys() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            return new String[] {Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())};
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 is not there", e);
        }
    }

    /** The signature of {@code data}, base64. */
    public static String sign(byte[] data, String privateKey) {
        try {
            PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey.trim())));
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(data);
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("the manifest could not be signed: is the private key an Ed25519 one?", e);
        }
    }

    /** Whether {@code signature} (base64) is {@code data}'s, by the holder of {@code publicKey}. */
    public static boolean verify(byte[] data, String signature, String publicKey) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.trim())));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(Base64.getDecoder().decode(signature.trim()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
