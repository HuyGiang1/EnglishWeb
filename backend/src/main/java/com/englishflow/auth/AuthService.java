package com.englishflow.auth;

import com.englishflow.auth.dto.LoginRequest;
import com.englishflow.auth.dto.RegisterRequest;
import com.englishflow.auth.dto.UserResponse;
import com.englishflow.auth.exception.EmailAlreadyRegisteredException;
import com.englishflow.auth.exception.InvalidCredentialsException;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException("An account with this email address already exists.");
        }

        String safeTimezone = validateTimezone(request.timezone());
        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
            normalizedEmail,
            passwordHash,
            request.displayName().trim(),
            safeTimezone,
            20
        );

        User savedUser = userRepository.save(user);

        // Auto-authenticate registered user and establish session
        establishSession(savedUser.getEmail(), httpRequest, httpResponse);

        return UserResponse.fromUser(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        if (!user.isActive()) {
            throw new InvalidCredentialsException("Account is disabled.");
        }

        establishSession(user.getEmail(), httpRequest, httpResponse);

        return UserResponse.fromUser(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BadCredentialsException("Unauthenticated");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("User not found."));

        return UserResponse.fromUser(user);
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, auth);

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        ResponseCookie cookie = ResponseCookie.from("JSESSIONID", "")
            .path("/")
            .maxAge(0)
            .httpOnly(true)
            .sameSite("Lax")
            .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void establishSession(String email, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Prevent session fixation by rotating session ID
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            httpRequest.changeSessionId();
        } else {
            httpRequest.getSession(true);
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            email,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }

    private String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "UTC";
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (Exception e) {
            return "UTC";
        }
    }
}
