package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class TeacherResponse {
    private TeacherResponse() {}

    // CREATE REQUEST
    @Getter
    @Setter
    public static class CreateRequest {

        @NotNull(message = "AppUserId không được để trống") private Integer appUserId;

        @NotNull(message = "BranchId không được để trống") private Integer branchId;

        @NotBlank(message = "Tên (FirstName) không được để trống")
        @Pattern(regexp = "^[\\p{L} ]+$", message = "Tên chỉ được chứa chữ cái và khoảng trắng") private String firstName;

        @NotBlank(message = "Họ và tên đệm (LastName) không được để trống")
        @Pattern(regexp = "^[\\p{L} ]+$", message = "Họ chỉ được chứa chữ cái và khoảng trắng") private String lastName;

        private LocalDate dateOfBirth;
        private Boolean gender;

        @Pattern(regexp = "^\\d{9}(\\d{3})?$", message = "CCCD phải có 12 số")
        private String idCardNo;

        @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Số điện thoại không hợp lệ")
        private String phone;

        private String address;
        private LocalDate hireDate;
        private String employmentType; // FULL_TIME | PART_TIME | CONTRACT
        private String status; // ACTIVE nếu để trống
        private List<CertificateRequest> certificates;
        private ContractRequest contract;
    }

    // UPDATE REQUEST

    @Getter
    @Setter
    public static class UpdateRequest {

        @NotNull(message = "BranchId không được để trống") private Integer branchId;

        @NotBlank(message = "Tên (FirstName) không được để trống")
        @Pattern(regexp = "^[\\p{L} ]+$", message = "Tên chỉ được chứa chữ cái và khoảng trắng") private String firstName;

        @NotBlank(message = "Họ và tên đệm (LastName) không được để trống")
        @Pattern(regexp = "^[\\p{L} ]+$", message = "Họ chỉ được chứa chữ cái và khoảng trắng") private String lastName;

        @NotBlank(message = "Trạng thái không được để trống") private String status; // ACTIVE | RETIRED | SUSPENDED

        private String employmentType;
        private LocalDate dateOfBirth;
        private Boolean gender;

        @Pattern(regexp = "^\\d{9}(\\d{3})?$", message = "CCCD phải có 12 số")
        private String idCardNo;

        @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Số điện thoại không hợp lệ")
        private String phone;

        private String address;
        private LocalDate hireDate;
    }

    // CERTIFICATE — 1 GV có NHIỀU chứng chỉ=========================================

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertificateDTO {
        private Integer id;
        private String name; // Tin học | English | STEM-AI | Kỹ năng sống
        private String issuer;
        private LocalDate issueDate;
        private LocalDate expiryDate;
        private String fileUrl; // ảnh/scan chứng chỉ
    }

    @Getter
    @Setter
    public static class CertificateRequest {
        @NotBlank(message = "Tên chứng chỉ không để trống") private String name;

        private String issuer;
        private LocalDate issueDate;
        private LocalDate expiryDate;
        private String fileUrl;
    }

    // CONTRACT — 1 GV chỉ có TỐI ĐA 1 hợp đồng active (ràng buộc DB)

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractDTO {
        private Integer id;
        private String contractNo;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal baseSalary;
        private BigDecimal allowance;
        private String status; // ACTIVE | EXPIRED | TERMINATED
        private String fileUrl; // ảnh/scan hợp đồng
    }

    @Getter
    @Setter
    public static class ContractRequest {
        @NotBlank(message = "Số hợp đồng không được để trống") private String contractNo;

        @NotNull(message = "Ngày bắt đầu không được để trống") private LocalDate startDate;

        private LocalDate endDate;
        private BigDecimal baseSalary;
        private BigDecimal allowance;
        private String fileUrl;
    }

    // RESPONSE — trả ra cho danh sách + chi tiết
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Integer appUserId;
        private Integer branchId;

        private String firstName;
        private String lastName;
        private String fullName; // lastName + " " + firstName

        private String PasswordHash;
        private String email;
        private String username;
        private LocalDate dateOfBirth;
        private Boolean gender; // true = Nam, false = Nữ
        private String idCardNo;
        private String phone;
        private String address;
        private LocalDate hireDate;

        private String employmentType; // FULL_TIME | PART_TIME | CONTRACT
        private String status; // ACTIVE | RETIRED | SUSPENDED

        // Chỉ có khi xem CHI TIẾT 1 GV
        private List<CertificateDTO> certificates;
        private ContractDTO contract; // null nếu GV chưa có hợp đồng
    }

    // HISTORY — GV nằm trong "thùng rác" (đã xóa mềm)======================================================
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        private Integer id;
        private String fullName;
        private Integer branchId;
        private String employmentType;
        private String status;
        private Instant deletedAt;
        private Integer deletedBy;
        private String phone;
        private String idCardNo;
    }
}
