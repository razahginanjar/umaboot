package ${basePackage}.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ${eeNamespace}.servlet.FilterChain;
import ${eeNamespace}.servlet.ServletException;
import ${eeNamespace}.servlet.http.HttpServletRequest;
import ${eeNamespace}.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads the {@code Authorization} header on each request, validates the JWT
 * with {@link JwtTokenService}, and populates the {@link SecurityContextHolder}
 * with a {@link UsernamePasswordAuthenticationToken}.
 *
 * <p>Requests without a token (or with an invalid one) pass through with no
 * authenticated principal — the {@code SecurityFilterChain} is responsible for
 * deciding which routes that's allowed on (typically only {@code /api/auth/login}).</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtService;
    private final UserDetailsService userDetailsService;
    private final String header;
    private final String prefix;

    public JwtAuthenticationFilter(JwtTokenService jwtService,
                                   UserDetailsService userDetailsService,
                                   @Value("${r"${umaboot.security.jwt.header:Authorization}"}") String header,
                                   @Value("${r"${umaboot.security.jwt.prefix:Bearer }"}") String prefix) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.header = header;
        this.prefix = prefix;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String raw = request.getHeader(header);
        if (raw != null && raw.startsWith(prefix)) {
            String token = raw.substring(prefix.length());
            try {
                Claims claims = jwtService.parse(token);
                String username = claims.getSubject();
                UserDetails user = userDetailsService.loadUserByUsername(username);
                @SuppressWarnings("unchecked")
                List<String> roleNames = (List<String>) claims.getOrDefault("roles", List.of());
                List<SimpleGrantedAuthority> authorities = roleNames.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException ignored) {
                // Invalid token — leave SecurityContext empty, downstream handlers
                // will return 401 if the route requires authentication.
            }
        }
        chain.doFilter(request, response);
    }
}
