package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.BranchSummary;
import com.kdc.tsdms.repository.BranchRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Danh sách chi nhánh (chỉ đọc) để frontend dựng dropdown khi tạo tài khoản GV/trường.
 * Cần đăng nhập (SecurityConfig: anyRequest authenticated).
 */
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchRepository branchRepo;

    public BranchController(BranchRepository branchRepo) {
        this.branchRepo = branchRepo;
    }

    @GetMapping
    public List<BranchSummary> list() {
        return branchRepo.findAll().stream()
                .map(b -> new BranchSummary(b.getId(), b.getName()))
                .toList();
    }
}
