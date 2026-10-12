package com.geoattend.backend.controller;

import com.geoattend.backend.model.ApiResponse;
import com.geoattend.backend.model.Member;
import com.geoattend.backend.model.PagedResponse;
import com.geoattend.backend.model.PendingMemberSignup;
import com.geoattend.backend.service.MemberService;
import com.geoattend.backend.service.MemberSignupApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;
    private final MemberSignupApprovalService signupApprovalService;

    public MemberController(
            MemberService memberService,
            MemberSignupApprovalService signupApprovalService) {
        this.memberService = memberService;
        this.signupApprovalService = signupApprovalService;
    }

    @GetMapping
    public ResponseEntity<?> getAllMembers(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "name") String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            PagedResponse<Member> response = memberService.getPagedMembers(
                    search, batchId, role, sortBy, sortDirection, page, size);
            return ResponseEntity.ok(response);
        } else {
            List<Member> list = memberService.getFilteredMembers(
                    search, batchId, role, sortBy, sortDirection);
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(list.size()))
                    .body(list);
        }
    }

    @GetMapping("/signups")
    public ResponseEntity<?> getSignups(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            PagedResponse<PendingMemberSignup> response = signupApprovalService.getPagedSignups(
                    status, batchId, search, fromDate, toDate, sortBy, sortDirection, page, size);
            return ResponseEntity.ok(response);
        } else {
            List<PendingMemberSignup> list = signupApprovalService.getFilteredSignups(
                    status, batchId, search, fromDate, toDate, sortBy, sortDirection);
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(list.size()))
                    .body(list);
        }
    }

    @PostMapping("/signups/{id}/approve")
    public ResponseEntity<ApiResponse> approveSignup(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        Long batchId = null;
        if (body != null && body.get("batchId") != null) {
            batchId = Long.valueOf(body.get("batchId").toString());
        }
        Long staffId = null;
        if (authentication != null) {
            try {
                staffId = Long.valueOf(authentication.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        signupApprovalService.approve(id, batchId, staffId);
        return ResponseEntity.ok(new ApiResponse(true, "Registration approved."));
    }

    @PostMapping("/signups/{id}/reject")
    public ResponseEntity<ApiResponse> rejectSignup(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String reason = body != null ? body.get("reason") : null;
        Long staffId = null;
        if (authentication != null) {
            try {
                staffId = Long.valueOf(authentication.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        signupApprovalService.reject(id, reason, staffId);
        return ResponseEntity.ok(new ApiResponse(true, "Registration rejected."));
    }

    @PutMapping("/{id}/batch")
    public ResponseEntity<ApiResponse> assignMemberBatch(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long batchId = null;
        if (body != null && body.get("batchId") != null) {
            batchId = Long.valueOf(body.get("batchId").toString());
        }
        memberService.assignBatch(id, batchId);
        return ResponseEntity.ok(new ApiResponse(true, "Member batch updated successfully."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addMember(@RequestBody Member member) {
        int result = memberService.addMember(member);
        if (result > 0) {
            return ResponseEntity.ok(new ApiResponse(true, "Member created successfully"));
        }
        return ResponseEntity.badRequest().body(new ApiResponse(false, "Failed to create member"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateMember(
            @PathVariable Long id,
            @RequestBody Member member) {
        int result = memberService.updateMember(id, member);
        if (result > 0) {
            return ResponseEntity.ok(new ApiResponse(true, "Member updated successfully"));
        }
        return ResponseEntity.badRequest().body(new ApiResponse(false, "Failed to update member"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteMember(@PathVariable Long id) {
        int result = memberService.deleteMember(id);
        if (result > 0) {
            return ResponseEntity.ok(new ApiResponse(true, "Member deleted successfully"));
        }
        return ResponseEntity.badRequest().body(new ApiResponse(false, "Failed to delete member"));
    }
}