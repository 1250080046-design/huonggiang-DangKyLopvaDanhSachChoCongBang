package vn.edu.registration.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.AccountRepository;
import java.time.LocalDateTime;

@Component
@Order(10)
public class BootstrapAdmin implements CommandLineRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;

    public BootstrapAdmin(AccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        String email = System.getenv("BOOTSTRAP_ADMIN_EMAIL");
        String password = System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
        if (email == null || password == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
                || password.length() < 12)
            return;
        if (accounts.existsByEmailIgnoreCase(email))
            return;
        Account a = new Account();
        a.setEmail(email);
        a.setRole(Role.ADMIN);
        a.setActivated(true);
        a.setPasswordHash(encoder.encode(password));
        a.setStatus(AccountStatus.ACTIVE);
        a.setActivatedAt(LocalDateTime.now());
        a.setMustChangePassword(false);
        accounts.save(a);
    }
}
