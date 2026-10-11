package com.geoattend.backend.service;

import com.geoattend.backend.repository.ClubUserRepository;
import com.geoattend.backend.security.ClubUser;
import org.springframework.stereotype.Service;

@Service
public class ClubUserService {

    private final ClubUserRepository clubUserRepository;

    public ClubUserService(ClubUserRepository clubUserRepository) {
        this.clubUserRepository = clubUserRepository;
    }

    public ClubUser getClubUser(Long userId) {
        return clubUserRepository.getUserById(userId);
    }

    public boolean canMarkManualAttendance(ClubUser clubUser) {

        if (clubUser == null) {
            return false;
        }

        return "ADMIN".equals(clubUser.getRole())
                || "CLUB_USER".equals(clubUser.getRole());
    }
}