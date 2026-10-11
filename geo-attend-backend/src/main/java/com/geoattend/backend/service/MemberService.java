package com.geoattend.backend.service;

import com.geoattend.backend.model.Member;
import com.geoattend.backend.repository.MemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final AuthService authService;

    public MemberService(MemberRepository memberRepository, AuthService authService) {
        this.memberRepository = memberRepository;
        this.authService = authService;
    }

    public List<Member> getAllMembers() {
        return memberRepository.getAllMembers();
    }

    public int addMember(Member member) {
        if (member.getRole() == null || member.getRole().isBlank()) {
            member.setRole("MEMBER");
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

        if (member.getPassword() != null && !member.getPassword().isBlank()) {
            member.setPasswordHash(authService.hashPassword(member.getPassword()));
        }

        return memberRepository.updateMember(id, member);
    }

    public int deleteMember(Long id) {
        return memberRepository.deleteMember(id);
    }

    public Member getMemberById(Long id) {
        return memberRepository.getMemberById(id);
    }
}