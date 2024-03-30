package pt.ulisboa.tecnico.hdsledger.crypto;

import org.junit.jupiter.api.Test;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

import java.net.URISyntaxException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoUtilsTest {

    @Test
    void getPublicKeyNotNull() {
        var publicKeyAbsPath = getResourceAbsolutePath("public_key.der");

        var publicKey = CryptoUtils.getPublicKey(publicKeyAbsPath);
        assertNotNull(publicKey);
    }

    @Test
    void getPrivateKeyNotNull() {
        var privateKeyAbsPath = getResourceAbsolutePath("private_key.der");

        var privateKey = CryptoUtils.getPrivateKey(privateKeyAbsPath);
        assertNotNull(privateKey);
    }

    @Test
    void readKeyPairNotNull() {
        var publicKeyAbsPath = getResourceAbsolutePath("public_key.der");
        var privateKeyAbsPath = getResourceAbsolutePath("private_key.der");

        var keyPair = CryptoUtils.readKeyPair(privateKeyAbsPath, publicKeyAbsPath);

        assertNotNull(keyPair);
    }

    @Test
    void signatureAndVerification() {
        var publicKeyAbsPath = getResourceAbsolutePath("public_key.der");
        var privateKeyAbsPath = getResourceAbsolutePath("private_key.der");

        var keyPair = CryptoUtils.readKeyPair(privateKeyAbsPath, publicKeyAbsPath);

        var data = "Hello World".getBytes();
        var signature = CryptoUtils.sign(data, keyPair.getPrivate());

        var verified = CryptoUtils.verify(data, signature, keyPair.getPublic());

        assertTrue(verified, "Signature verification failed");
    }


    private String getResourceAbsolutePath(String resourcePath) {
        var resource = Thread.currentThread().getContextClassLoader().getResource(resourcePath);

        if (resource == null) throw new IllegalArgumentException("Resource not found: " + resourcePath);

        try {
            return Paths.get(resource.toURI()).toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid resource path: " + resourcePath, e);
        }
    }

}