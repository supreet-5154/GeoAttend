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
            LoginUser user = null;

            try {
                user = clubUserRepository.getLoginUserByEmail(request.getEmail());
            } catch (EmptyResultDataAccessException ignored) {
                user = null;
            }

            if (user == null) {
                try {
                    user = memberRepository.getLoginUserByEmail(request.getEmail());
                } catch (EmptyResultDataAccessException ignored) {
                    // Check if user has a pending or rejected registration request
                    Optional<MemberSignup> signup = memberSignupRepository.findByEmail(request.getEmail());
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
}