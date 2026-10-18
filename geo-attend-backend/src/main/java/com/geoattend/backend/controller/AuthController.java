package com.geoattend.backend.controller;

import com.geoattend.backend.model.ApiResponse;
import com.geoattend.backend.model.LoginRequest;
import com.geoattend.backend.model.LoginResponse;
import com.geoattend.backend.model.MemberSignup;
import com.geoattend.backend.model.RegisterRequest;
import com.geoattend.backend.repository.ClubUserRepository;
import com.geoattend.backend.repository.MemberRepository;
import com.geoattend.backend.repository.MemberSignupRepository;
import com.geoattend.backend.security.ClubUser;
import com.geoattend.backend.security.JwtService;
import com.geoattend.backend.security.LoginUser;
import com.geoattend.backend.service.AuthService;
import com.geoattend.backend.service.MemberSignupApprovalService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ClubUserRepository clubUserRepository;
    private final MemberRepository memberRepository;
    private final JwtService jwtService;
    private final MemberSignupApprovalService signupApprovalService;
    private final MemberSignupRepository memberSignupRepository;

    public AuthController(
            AuthService authService,
            ClubUserRepository clubUserRepository,
            MemberRepository memberRepository,
            JwtService jwtService,
            MemberSignupApprovalService signupApprovalService,
            MemberSignupRepository memberSignupRepository) {

        this.authService = authService;
        this.clubUserRepository = clubUserRepository;
        this.memberRepository = memberRepository;
        this.jwtService = jwtService;
        this.signupApprovalService = signupApprovalService;
        this.memberSignupRepository = memberSignupRepository;
    }

    @PostMapping({"/register", "/signup"})
    public ResponseEntity<ApiResponse> register(@RequestBody RegisterRequest request) {
        try {
            signupApprovalService.register(request);
            return ResponseEntity.ok(new ApiResponse(true,
                    "Registration request submitted. Your account is pending review by club staff."));
        } catch (ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode())
                    .body(new ApiResponse(false, rse.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage() != null ? e.getMessage() : "Unable to process registration."));
        }
    }

    @PostMapping({"/login", "/member/login"})
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        try {
            if (request == null || request.getEmail() == null || request.getPassword() == null) {
                return ResponseEntity.badRequest().body("Invalid email or password");
            }
            String email = request.getEmail().trim();
            LoginUser user = null;

            try {
                user = clubUserRepository.getLoginUserByEmail(email);
            } catch (EmptyResultDataAccessException ignored) {
                user = null;
            }

            if (user == null) {
                try {
                    user = memberRepository.getLoginUserByEmail(email);
                } catch (EmptyResultDataAccessException ignored) {
                    // Check if user has a pending or rejected registration request
                    Optional<MemberSignup> signup = memberSignupRepository.findByEmail(email);
                    if (signup.isPresent()) {
                        String status = signup.get().status();
                        if ("PENDING".equalsIgnoreCase(status)) {
                            return ResponseEntity.badRequest().body("Your registration request is pending approval by club staff.");
                        } else if ("REJECTED".equalsIgnoreCase(status)) {
                            String reason = signup.get().rejectionReason();
                            String msg = "Your registration request was declined by club staff.";
                            if (reason != null && !reason.isBlank()) {
                                msg += " Reason: " + reason;
                            }
                            return ResponseEntity.badRequest().body(msg);
                        }
                    }
                    return ResponseEntity.badRequest().body("Invalid email or password");
                }
            }

            if (user.getPasswordHash() == null || !authService.verifyPassword(request.getPassword(), user.getPasswordHash())) {
                return ResponseEntity.badRequest().body("Invalid email or password");
            }

            ClubUser clubUser = new ClubUser(
                    user.getId(),
                    user.getName(),
                    user.getRole()
            );

            String token = jwtService.generateToken(clubUser);

            LoginResponse response = new LoginResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole(),
                    token
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid email or password");
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getServerStatus() {
        java.time.ZonedDateTime nowKolkata = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        return ResponseEntity.ok(java.util.Map.of(
                "status", "UP",
                "jvmTimezone", java.util.TimeZone.getDefault().getID(),
                "serverTimeKolkata", nowKolkata.toString(),
                "localDateTimeNow", java.time.LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        try {
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            } else if (body != null && body.containsKey("token")) {
                token = body.get("token");
            }

            if (token == null || token.isBlank()) {
                return ResponseEntity.badRequest().body("Token is required to refresh session.");
            }

            if (!jwtService.canTokenBeRefreshed(token)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .body("Session has expired beyond refresh window. Please log in again.");
            }

            io.jsonwebtoken.Claims claims = jwtService.getClaimsEvenIfExpired(token);
            if (claims == null || claims.getSubject() == null) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .body("Invalid token payload.");
            }

            Long userId = Long.parseLong(claims.getSubject());
            String role = claims.get("role", String.class);

            String name = claims.get("name", String.class);
            String email = null;

            if ("MEMBER".equals(role)) {
                com.geoattend.backend.model.Member member = memberRepository.getMemberById(userId);
                name = member.getName();
                email = member.getEmail();
            } else {
                ClubUser staffUser = clubUserRepository.getUserById(userId);
                name = staffUser.getName();
            }

            ClubUser userToSign = new ClubUser(userId, name, role);
            String newToken = jwtService.generateToken(userToSign);

            LoginResponse response = new LoginResponse(
                    userId,
                    name,
                    email,
                    role,
                    newToken
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body("Unable to refresh session. Please log in again.");
        }
    }
}