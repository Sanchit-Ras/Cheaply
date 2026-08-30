package com.cheaply.security.jwt;

/**
 * Distinguishes the two kinds of token this application issues.
 *
 * <p>Both are signed with the same key, so without an explicit type claim an
 * access token would be indistinguishable from a refresh token and could be
 * replayed against {@code /api/auth/refresh} to mint an endless supply of new
 * credentials. The type is written into every token and verified on use.
 */
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    public static final String CLAIM = "typ";

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    public static TokenType fromClaim(String value) {
        for (TokenType type : values()) {
            if (type.claimValue.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
