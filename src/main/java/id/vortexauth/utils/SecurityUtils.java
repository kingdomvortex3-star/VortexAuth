
package id.vortexauth.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import java.util.Base64;
import java.util.regex.Pattern;

public final class SecurityUtils {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final String HMAC_ALGORITHM =
            "HmacSHA256";

    private static final String TOKEN_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            + "abcdefghijklmnopqrstuvwxyz"
            + "0123456789";

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private SecurityUtils() {
    }

    public static String generateToken() {
        return generateToken(48);
    }

    public static String generateToken(int length) {
        if (length < 16 || length > 256) {
            throw new IllegalArgumentException(
                    "Token length must be between 16 and 256."
            );
        }

        StringBuilder token = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(
                    TOKEN_CHARACTERS.length()
            );

            token.append(TOKEN_CHARACTERS.charAt(index));
        }

        return token.toString();
    }

    public static byte[] generateSecret(int length) {
        if (length < 32 || length > 128) {
            throw new IllegalArgumentException(
                    "Secret length must be between 32 and 128 bytes."
            );
        }

        byte[] secret = new byte[length];

        RANDOM.nextBytes(secret);

        return secret;
    }

    public static String encodeSecret(byte[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException(
                    "Secret cannot be empty."
            );
        }

        return Base64.getEncoder().encodeToString(secret);
    }

    public static String hashIp(
            String ipAddress,
            byte[] secret
    ) {
        if (ipAddress == null
                || ipAddress.isBlank()
                || secret == null
                || secret.length < 32) {
            throw new IllegalArgumentException(
                    "Invalid IP hash parameters."
            );
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            SecretKeySpec key = new SecretKeySpec(
                    secret,
                    HMAC_ALGORITHM
            );

            mac.init(key);

            byte[] hash = mac.doFinal(
                    ipAddress.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to generate IP hash.",
                    exception
            );
        }
    }

    public static boolean constantTimeEquals(
            String first,
            String second
    ) {
        if (first == null || second == null) {
            return false;
        }

        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }

        return USERNAME_PATTERN.matcher(username).matches();
    }

    public static boolean isValidInput(String input) {
        return input != null
                && !input.isBlank()
                && input.length() <= 256;
    }

    public static boolean isValidXuid(String xuid) {
        if (xuid == null || xuid.isBlank()) {
            return false;
        }

        return xuid.matches("^[0-9]{1,20}$");
    }
}
