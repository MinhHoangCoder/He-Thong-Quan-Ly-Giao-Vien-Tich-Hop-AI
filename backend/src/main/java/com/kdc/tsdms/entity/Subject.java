package com.kdc.tsdms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "Subject")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SubjectId")
    private Integer subjectId;

    @Column(name = "Code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "Name", nullable = false, length = 150)
    private String name;

    @Column(name = "Category", length = 50)
    private String category;

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @Column(name = "IsDeleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;

    @Column(name = "DeletedBy")
    private Integer deletedBy;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CreatedBy")
    private Integer createdBy;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "UpdatedBy")
    private Integer updatedBy;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = "ACTIVE";
        }

        if (isDeleted == null) {
            isDeleted = false;
        }
    }
}
