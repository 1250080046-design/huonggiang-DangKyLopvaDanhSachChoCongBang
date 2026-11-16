package vn.edu.registration.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {
    public static final String NAME = "hl_auth";
    private final long maxAgeSeconds;
    private final boolean secure;

    public AuthCookieService(@Value("${app.jwt.expiration-minutes:240}") long minutes,
                             @Value("${app.cookie.secure:false}") boolean secure) {
        this.maxAgeSeconds = minutes * 60;
        this.secure = secure;
    }
    public void login(HttpServletResponse response, String jwt) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(jwt, maxAgeSeconds).toString());
    }
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }
    private ResponseCookie cookie(String value, long seconds) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/").maxAge(seconds).build();
    }
}
