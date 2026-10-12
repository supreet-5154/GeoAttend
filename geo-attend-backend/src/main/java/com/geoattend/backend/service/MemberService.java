package com.geoattend.backend.service;

import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.Member;
import com.geoattend.backend.model.PagedResponse;
import com.geoattend.backend.repository.BatchRepository;
import com.geoattend.backend.repository.MemberRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final BatchRepository batchRepository;
    private final AuthService authService;

    public MemberService(
            MemberRepository memberRepository,
            BatchRepository batchRepository,
            AuthService authService) {
        this.memberRepository = memberRepository;
        this.batchRepository = batchRepository;
        this.authService = authService;
    }

    public List<Member> getAllMembers() {
        return memberRepository.getAllMembers();
    }

    public List<Member> getMembersByBatch(Long batchId) {
        if (batchId == null) {
            return memberRepository.getAllMembers();
        }
        return memberRepository.getMembersByBatchId(batchId);
    }

    public PagedResponse<Member> getPagedMembers(String search, Long batchId, String role,
                                                String sortBy, String sortDirection,
                                                int page, int size) {
        int pageNum = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        int offset = pageNum * pageSize;

        long total = memberRepository.countMembers(search, batchId, role);
        List<Member> content = memberRepository.findMembers(search, batchId, role, sortBy, sortDirection, offset, pageSize);
        return new PagedResponse<>(content, pageNum, pageSize, total);
    }

    public List<Member> getFilteredMembers(String search, Long batchId, String role,
                                          String sortBy, String sortDirection) {
        return memberRepository.findMembers(search, batchId, role, sortBy, sortDirection, null, null);
    }

    public int addMember(Member member) {
        if (member.getRole() == null || member.getRole().isBlank()) {
            member.setRole("MEMBER");
        }

        if (member.getBatchId() != null) {
            validateActiveBatch(member.getBatchId());
        }

        if (member.getPassword() != null && !member.getPassword().isBlank()) {
            member.setPasswordHash(authService.hashPassword(member.getPassword()));
        }

        return memberRepository.addMember(member);
    }

    public int updateMember(Long id, Member member) {
        if (member.getRole() == null || member.getRole().isBlank()) {
            member.setRole("MEMBER");
        }

        if (member.getBatchId() != null) {
            validateActiveBatch(member.getBatchId());
        }

        if (member.getPassword() != null && !member.getPassword().isBlank()) {
            member.setPasswordHash(authService.hashPassword(member.getPassword()));
        }

        return memberRepository.updateMember(id, member);
    }

    @Transactional
    public void assignBatch(Long memberId, Long batchId) {
        if (!memberRepository.memberExists(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found with ID: " + memberId);
        }

        if (batchId != null) {
            validateActiveBatch(batchId);
        }

        int updated = memberRepository.updateMemberBatch(memberId, batchId);
        if (updated != 1) {
            throw new IllegalStateException("Failed to update member batch assignment.");
        }
    }

    public int deleteMember(Long id) {
        return memberRepository.deleteMember(id);
    }

    public Member getMemberById(Long id) {
        return memberRepository.getMemberById(id);
    }

    private void validateActiveBatch(Long batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid batch ID: batch does not exist."));
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot assign member to an archived batch.");
        }
    }
}