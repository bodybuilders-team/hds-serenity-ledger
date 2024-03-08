package pt.ulisboa.tecnico.hdsledger.crypto;

import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * The {@code CryptoUtils} class provides utility methods for cryptographic operations.
 * It supports key pair generation, reading public and private keys from files,
 * signing data with a private key, and verifying signatures with a public key.
 */
public class CryptoUtils {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CryptoUtils() {
        // Empty constructor
    }

    /**
     * Reads a key pair from the specified files.
     *
     * @param privateKeyPath the path to the private key file
     * @param publicKeyPath  the path to the public key file
     * @return the key pair
     * @throws HDSSException if there is an error loading the keys
     */
    public static KeyPair readKeyPair(String privateKeyPath, String publicKeyPath) {
        final PublicKey publicKey = getPublicKey(publicKeyPath);
        final PrivateKey privateKey = getPrivateKey(privateKeyPath);
        if (publicKey == null || privateKey == null)
            throw new HDSSException(ErrorMessage.KeyPairLoadError);

        return new KeyPair(publicKey, privateKey);
    }

    /**
     * Retrieves the public key from the specified file.
     *
     * @param publicKeyPath the path to the public key file
     * @return the public key
     * @throws HDSSException if there is an error loading the public key
     */
    public static PublicKey getPublicKey(String publicKeyPath) {
        File publicKeyFile = new File(publicKeyPath);
        if (!publicKeyFile.exists())
            throw new HDSSException(ErrorMessage.PublicKeyLoadError);

        try {
            byte[] keyBytes = Files.readAllBytes(publicKeyFile.toPath());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePublic(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new HDSSException(ErrorMessage.PublicKeyLoadError);
        }
    }

    /**
     * Retrieves the private key from the specified file.
     *
     * @param privateKeyPath the path to the private key file
     * @return the private key
     * @throws HDSSException if there is an error loading the private key
     */
    public static PrivateKey getPrivateKey(String privateKeyPath) {
        File privateKeyFile = new File(privateKeyPath);
        if (!privateKeyFile.exists())
            throw new HDSSException(ErrorMessage.PrivateKeyLoadError);

        try {
            byte[] keyBytes = Files.readAllBytes(privateKeyFile.toPath());
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePrivate(spec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new HDSSException(ErrorMessage.PrivateKeyLoadError);
        }
    }

    /**
     * Signs the provided data with the given private key.
     *
     * @param data the data to sign
     * @param key  the private key
     * @return the signature
     * @throws HDSSException if there is an error signing the data
     */
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

    /**
     * Verifies the signature of the provided data using the given public key.
     *
     * @param data      the data
     * @param signature the signature to verify
     * @param publicKey the public key
     * @return {@code true} if the signature is valid, {@code false} otherwise
     * @throws HDSSException if there is an error verifying the signature
     */
    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            final Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);

            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new HDSSException(ErrorMessage.InvalidSignatureError);
        }
    }
}

