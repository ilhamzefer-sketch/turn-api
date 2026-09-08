package az.turn.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class EpointSignature {
    private EpointSignature() {
    }

    public static String sign(String data, String privateKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((privateKey + data + privateKey).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate Epoint signature.", exception);
        }
    }

    public static boolean isValid(String data, String signature, String privateKey) {
        if (data == null || signature == null || privateKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(data, privateKey).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
