package club.muimi.backend.entity;

import club.muimi.backend.common.enums.Grade;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_recruitment_group_name", columnNames = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RecruitmentGroup {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "direction_level1_id", nullable = false)
    private Long directionLevel1Id;

    @Column(name = "direction_level2_id", nullable = false)
    private Long directionLevel2Id;

    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    @Enumerated(EnumType.STRING)
    private Grade grade;

    @Column(name = "admission_year", nullable = false)
    private Integer admissionYear;

    @Column(nullable = false)
    private Integer maxSize;

    @Column(name = "leader_user_id")
    private Long leaderUserId;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
