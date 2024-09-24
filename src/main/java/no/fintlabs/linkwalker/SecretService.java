package no.fintlabs.linkwalker;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Slf4j
@Service
public class SecretService {

    @Value("${fint.linkwalker.private-key}")
    private String privateKeyString;

    private PublicKey publicKey;
    private PrivateKey privateKey;

    public SecretService() {

    }

    @PostConstruct
    public void init() throws NoSuchAlgorithmException, InvalidKeySpecException {

        String encoded = privateKeyString.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "")
                .trim();

        byte[] keyDecoded = Base64.getMimeDecoder().decode(encoded);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyDecoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        privateKey = keyFactory.generatePrivate(keySpec);

        RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(((RSAPrivateCrtKey) privateKey).getModulus(), ((RSAPrivateCrtKey) privateKey).getPublicExponent());
        publicKey = keyFactory.generatePublic(rsaPublicKeySpec);
    }

    public String decrypt(String encryptedPassword) {
        if (StringUtils.isEmpty(encryptedPassword)) return null;
        log.info("EncryptedPassword: {}", encryptedPassword);

        try {
            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

            return new String(
                    decryptCipher.doFinal(
                            Base64.getDecoder().decode(encryptedPassword.getBytes(StandardCharsets.UTF_8))),
                    StandardCharsets.UTF_8);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            log.error("{}", e.getMessage());
            throw new RuntimeException(e);
        }

    }

    public String getPublicKeyString() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }


}
