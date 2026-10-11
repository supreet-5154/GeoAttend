package com.geoattend.backend.controller;

import com.geoattend.backend.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import com.geoattend.backend.model.Member;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.geoattend.backend.model.ApiResponse;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<Member> getAllMembers() {
        return memberService.getAllMembers();
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addMember(@RequestBody Member member) {

        int result = memberService.addMember(member);

        if (result > 0) {
            return ResponseEntity.ok(
                    new ApiResponse(true, "Member created successfully")
            );
        }

        return ResponseEntity.badRequest()
                .body(new ApiResponse(false, "Failed to create member"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateMember(
            @PathVariable Long id,
            @RequestBody Member member) {

        int result = memberService.updateMember(id, member);

        if (result > 0) {
            return ResponseEntity.ok(
                    new ApiResponse(true, "Member updated successfully")
            );
        }

        return ResponseEntity.badRequest()
                .body(new ApiResponse(false, "Failed to update member"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteMember(@PathVariable Long id) {

        int result = memberService.deleteMember(id);

        if (result > 0) {
            return ResponseEntity.ok(
                    new ApiResponse(true, "Member deleted successfully")
            );
        }

        return ResponseEntity.badRequest()
                .body(new ApiResponse(false, "Failed to delete member"));
    }
}