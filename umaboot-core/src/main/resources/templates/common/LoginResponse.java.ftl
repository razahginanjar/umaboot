package ${basePackage}.security;

/**
 * Login response payload — wraps the issued JWT.
 */
public final class LoginResponse {

    private final String token;

    public LoginResponse(String token) {
        this.token = token;
    }

    public String getToken() { return token; }
}
