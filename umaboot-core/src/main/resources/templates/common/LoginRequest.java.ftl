package ${basePackage}.security;

<#if validationJakarta>
import ${eeNamespace}.validation.constraints.NotBlank;
</#if>

/**
 * Login request payload. Class form (no record) so it works on Java 8 and 11
 * regardless of Spring Boot major.
 */
public final class LoginRequest {

<#if validationJakarta>
    @NotBlank
</#if>
    private String username;

<#if validationJakarta>
    @NotBlank
</#if>
    private String password;

    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
