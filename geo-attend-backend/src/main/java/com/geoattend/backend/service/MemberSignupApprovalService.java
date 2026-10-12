package com.geoattend.backend.service;

import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.Member;
import com.geoattend.backend.model.MemberSignup;
import com.geoattend.backend.model.PagedResponse;
import com.geoattend.backend.model.PendingMemberSignup;
import com.geoattend.backend.model.RegisterRequest;
import com.geoattend.backend.repository.BatchRepository;
import com.geoattend.backend.repository.ClubUserRepository;
import com.geoattend.backend.repository.MemberRepository;
import com.geoattend.backend.repository.MemberSignupRepository;
import com.geoattend.backend.security.LoginUser;
import com.geoattend.backend.util.DateTimeFilterUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemberSignupApprovalService {

    private final MemberSignupRepository signupRepository;
    private final MemberRepository memberRepository;
    private final BatchRepository batchRepository;
    private final ClubUserRepository clubUserRepository;
    private final AuthService authService;

    public MemberSignupApprovalService(
            MemberSignupRepository signupRepository,
            MemberRepository memberRepository,
            ClubUserRepository clubUserRepository,
            AuthService authService) {
        this(signupRepository, memberRepository, null, clubUserRepository, authService);
    }

    @Autowired
    public MemberSignupApprovalService(
            MemberSignupRepository signupRepository,
            MemberRepository memberRepository,
            BatchRepository batchRepository,
            ClubUserRepository clubUserRepository,
            AuthService authService) {
        this.signupRepository = signupRepository;
        this.memberRepository = memberRepository;
        this.batchRepository = batchRepository;
        this.clubUserRepository = clubUserRepository;
        this.authService = authService;
    }

    public List<PendingMemberSignup> getPendingSignups() {
        return signupRepository.findPending();
    }

    public List<PendingMemberSignup> getSignups(String status) {
        return signupRepository.findAll(status);
    }

    public PagedResponse<PendingMemberSignup> getPagedSignups(
            String status, Long batchId, String search,
            String fromDate, String toDate,
            String sortBy, String sortDirection,
            int page, int size) {
        LocalDateTime start = DateTimeFilterUtils.parseStartBoundary(fromDate);
        LocalDateTime end = DateTimeFilterUtils.parseEndBoundary(toDate);
        int pageNum = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        int offset = pageNum * pageSize;

        long total = signupRepository.countSignups(status, batchId, search, start, end);
        List<PendingMemberSignup> content = signupRepository.findSignups(
                status, batchId, search, start, end, sortBy, sortDirection, offset, pageSize);

        return new PagedResponse<>(content, pageNum, pageSize, total);
    }

    public List<PendingMemberSignup> getFilteredSignups(
            String status, Long batchId, String search,
            String fromDate, String toDate,
            String sortBy, String sortDirection) {
        LocalDateTime start = DateTimeFilterUtils.parseStartBoundary(fromDate);
        LocalDateTime end = DateTimeFilterUtils.parseEndBoundary(toDate);
        return signupRepository.findSignups(
                status, batchId, search, start, end, sortBy, sortDirection, null, null);
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration payload is missing.");
        }
        String name = request.getName() != null ? request.getName().trim() : "";
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";

        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required.");
        }
        if (email.isEmpty() || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required.");
        }
        if (password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters long.");
        }

        LoginUser existing = findUserByEmail(email);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account with this email already exists.");
        }

        if (signupRepository.findPendingByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A registration request with this email is already pending staff review.");
        }

        Long requestedBatchId = request.getRequestedBatchId();
        if (requestedBatchId != null && batchRepository != null) {
            Batch batch = batchRepository.findById(requestedBatchId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected batch does not exist."));
            if (!"ACTIVE".equals(batch.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request assignment to an archived batch.");
            }
        }

        String studentId = (request.getStudentId() != null && !request.getStudentId().trim().isEmpty())
                ? request.getStudentId().trim()
                : null;

        String passwordHash = authService.hashPassword(password);
        signupRepository.insert(name, email, passwordHash, requestedBatchId, studentId);
    }

    @Transactional
    public void approve(Long id) {
        approve(id, null, null);
    }

    @Transactional
    public void approve(Long id, Long batchId, Long reviewedByStaffId) {
        MemberSignup signup = getPendingSignupForUpdate(id);

        Long finalBatchId = batchId != null ? batchId : signup.requestedBatchId();
        if (finalBatchId != null && batchRepository != null) {
            Batch batch = batchRepository.findById(finalBatchId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Selected batch does not exist."));
            if (!"ACTIVE".equals(batch.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Cannot assign member to an archived batch.");
            }
        }

        LoginUser existingUser = findUserByEmail(signup.email());
        if (existingUser == null) {
            Member member = new Member();
            member.setName(signup.name());
            member.setEmail(signup.email());
            member.setRole("MEMBER"); // strictly enforced backend role
            member.setBatchId(finalBatchId);
            member.setStudentId(signup.studentId());
            member.setPasswordHash(signup.passwordHash()); // Preserve user's registered hashed password!

            if (memberRepository.addMember(member) != 1) {
                throw new IllegalStateException("Unable to create the approved member account.");
            }
        } else {
            if ("MEMBER".equals(existingUser.getRole()) && finalBatchId != null) {
                memberRepository.updateMemberBatch(existingUser.getId(), finalBatchId);
            }
        }

        int updated = (reviewedByStaffId != null)
                ? signupRepository.approve(id, reviewedByStaffId)
                : signupRepository.updateStatus(id, "APPROVED");

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This registration request has already been reviewed.");
        }
    }

    @Transactional
    public void reject(Long id) {
        reject(id, null, null);
    }

    @Transactional
    public void reject(Long id, String reason, Long reviewedByStaffId) {
        getPendingSignupForUpdate(id);

        String sanitizedReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : "Registration request declined by staff.";
        if (sanitizedReason.length() > 500) {
            sanitizedReason = sanitizedReason.substring(0, 500);
        }

        int updated = (reviewedByStaffId != null || reason != null)
                ? signupRepository.reject(id, sanitizedReason, reviewedByStaffId)
                : signupRepository.updateStatus(id, "REJECTED");

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This registration request has already been reviewed.");
        }
    }

    private MemberSignup getPendingSignupForUpdate(Long id) {
        return signupRepository.findPendingByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pending registration request not found."));
    }

    private LoginUser findUserByEmail(String email) {
        LoginUser user = null;
        try {
            user = clubUserRepository.getLoginUserByEmail(email);
        } catch (EmptyResultDataAccessException ignored) {
            user = null;
        }
        if (user != null) {
            return user;
        }
        try {
            return memberRepository.getLoginUserByEmail(email);
        } catch (EmptyResultDataAccessException notFound) {
            return null;
        }
    }
}
