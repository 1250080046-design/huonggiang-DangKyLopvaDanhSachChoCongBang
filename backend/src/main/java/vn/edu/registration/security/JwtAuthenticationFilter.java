package vn.edu.registration.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final vn.edu.registration.repository.AccountRepository accounts;
    public JwtAuthenticationFilter(JwtService jwtService, vn.edu.registration.repository.AccountRepository accounts) { this.jwtService = jwtService; this.accounts = accounts; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (AuthCookieService.NAME.equals(cookie.getName())) { token = cookie.getValue(); break; }
        }
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);
                if (email != null && role != null && accounts.findByEmailIgnoreCase(email).filter(a -> a.isActivated() && a.getStatus() == vn.edu.registration.domain.AccountStatus.ACTIVE && a.getRole().name().equals(role)).isPresent()) {
                    var auth = new UsernamePasswordAuthenticationToken(email, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (RuntimeException ignored) { // Invalid/expired JWT: protected endpoints will reject the request.
            }
        }
        chain.doFilter(request, response);
    }
}
