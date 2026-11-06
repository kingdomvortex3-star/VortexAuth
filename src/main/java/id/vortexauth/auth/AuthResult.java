
package id.vortexauth.auth;

public enum AuthResult {

    SUCCESS,

    ALREADY_REGISTERED,

    NOT_REGISTERED,

    INVALID_PASSWORD,

    PASSWORD_TOO_SHORT,

    PASSWORD_TOO_LONG,

    PASSWORD_MISMATCH,

    INVALID_USERNAME,

    INVALID_XUID,

    ALREADY_LOGGED_IN,

    NOT_LOGGED_IN,

    ACCOUNT_LOCKED,

    TOO_MANY_ATTEMPTS,

    SESSION_EXPIRED,

    PREMIUM_NOT_VERIFIED,

    BEDROCK_NOT_SUPPORTED,

    VPN_BLOCKED,

    BOT_DETECTED,

    DATABASE_ERROR,

    INTERNAL_ERROR;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isFailure() {
        return this != SUCCESS;
    }
}
