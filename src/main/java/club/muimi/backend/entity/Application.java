package club.muimi.backend.entity;

import club.muimi.backend.common.enums.ApplicationStatus;
import club.muimi.backend.common.enums.Grade;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_application_user_direction",
                        columnNames = {"user_id", "direction_level2_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String realName;

    @Column(nullable = false, length = 32)
    private String phoneNumber;

    @Column(nullable = false, length = 128)
    private String college;

    @Column(nullable = false, length = 128)
    private String major;

    @Column(nullable = false, length = 128)
    private String className;

    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    @Enumerated(EnumType.STRING)
    private Grade grade;

    @Column(name = "admission_year", nullable = false)
    private Integer admissionYear;

    @Column(name = "direction_level1_id", nullable = false)
    private Long directionLevel1Id;

    @Column(name = "direction_level2_id", nullable = false)
    private Long directionLevel2Id;

    @Column(length = 1000)
    private String introduction;

    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(name = "status_remark", length = 255)
    private String statusRemark;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
