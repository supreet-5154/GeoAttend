package com.geoattend.backend.controller;

import com.geoattend.backend.model.LoginRequest;
import com.geoattend.backend.model.LoginResponse;
import com.geoattend.backend.repository.ClubUserRepository;
import com.geoattend.backend.repository.MemberRepository;
import com.geoattend.backend.security.ClubUser;
import com.geoattend.backend.security.JwtService;
import com.geoattend.backend.security.LoginUser;
import com.geoattend.backend.service.AuthService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ClubUserRepository clubUserRepository;
    private final MemberRepository memberRepository;
    private final JwtService jwtService;

    public AuthController(
            AuthService authService,
            ClubUserRepository clubUserRepository,
            MemberRepository memberRepository,
            JwtService jwtService) {

        this.authService = authService;
        this.clubUserRepository = clubUserRepository;
        this.memberRepository = memberRepository;
        this.jwtService = jwtService;
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