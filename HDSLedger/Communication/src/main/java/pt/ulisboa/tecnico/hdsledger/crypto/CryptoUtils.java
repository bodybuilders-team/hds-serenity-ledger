package pt.ulisboa.tecnico.hdsledger.crypto;

import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class CryptoUtils {
    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private CryptoUtils() {
    }

    public static KeyPair readKeyPair(String privateKeyPath, String publicKeyPath) {
        final PrivateKey privateKey = getPrivateKey(privateKeyPath);
        final PublicKey publicKey = getPublicKey(publicKeyPath);
        return new KeyPair(publicKey, privateKey);
    }

    public static PublicKey getPublicKey(String publicKeyPath) {
        try {
            byte[] keyBytes = Files.readAllBytes(new File(publicKeyPath).toPath());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePublic(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new HDSSException(ErrorMessage.PublicKeyLoadError);
        }

    }

    public static PrivateKey getPrivateKey(String privateKeyPath) {
        try {
            byte[] keyBytes = Files.readAllBytes(new File(privateKeyPath).toPath());
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePrivate(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new HDSSException(ErrorMessage.PrivateKeyLoadError);
        }
    }

    public static byte[] sign(byte[] data, PrivateKey key) {
        try {
            final Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new HDSSException(ErrorMessage.SignatureError);
        }
    }

    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            final Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.update(data);

            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | SignatureException e) {
            throw new HDSSException(ErrorMessage.InvalidSignatureError);
        }
    }
}
