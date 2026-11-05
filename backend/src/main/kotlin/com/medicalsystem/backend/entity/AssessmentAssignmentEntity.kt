package com.medicalsystem.backend.entity

import com.medicalsystem.backend.model.AssessmentStatus
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "assessment_assignments",
    indexes = [
        Index(name = "idx_assignment_student", columnList = "student_id"),
        Index(name = "idx_assignment_status", columnList = "status"),
        Index(name = "idx_assignment_battery_code", columnList = "battery_code")
    ]
)
class AssessmentAssignmentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: StudentEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id", nullable = false)
    var assignedByUser: UserEntity,

    @Column(name = "battery_code", nullable = false)
    var batteryCode: String,

    @Column(nullable = false)
    var status: AssessmentStatus = AssessmentStatus.PENDING,

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    @Column(name = "answers_json", columnDefinition = "TEXT")
    var answersJson: String? = null,
    
    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatch_id")
    var dispatch: AssessmentDispatchEntity? = null,

    @Column(name = "severity_id")
    var severity: com.medicalsystem.backend.model.ResultSeverity = com.medicalsystem.backend.model.ResultSeverity.PENDING,

    /** Optimistic-lock version: guarantees only one submit transaction can transition a row to COMPLETED. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)
