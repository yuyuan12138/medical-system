package com.medicalsystem.backend.model

import com.medicalsystem.backend.event.AssessmentAssignedEvent
import com.medicalsystem.backend.event.AssessmentCompletedEvent
import com.medicalsystem.backend.exception.ConflictException
import com.medicalsystem.backend.exception.ValidationException
import java.time.LocalDate
import java.time.LocalDateTime

data class AssessmentAssignment(
    val id: Long? = null,
    val studentId: Long,
    val assignedByUserId: Long,
    val batteryCode: BatteryId,
    val dispatchId: Long? = null,
    var status: AssessmentStatus = AssessmentStatus.PENDING,
    val assignedAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
    val dueDate: LocalDate? = null,
    var answers: Map<String, Any>? = null,
    var revokedAt: LocalDateTime? = null,
    var severity: ResultSeverity = ResultSeverity.PENDING
) : AggregateRoot() {

    fun initAssignedEvent() {
        if (this.id != null) {
            registerEvent(
                AssessmentAssignedEvent(
                    assignmentId = this.id,
                    studentId = this.studentId,
                    assignedByUserId = this.assignedByUserId,
                    batteryCode = this.batteryCode.value
                )
            )
        }
    }

    fun complete(
        responses: Map<String, Any>,
        completedTests: List<PsychometricTest>
    ) {
        if (status == AssessmentStatus.COMPLETED) {
            throw ConflictException("问卷测评已完成，不可重复提交")
        }
        if (status == AssessmentStatus.EXPIRED) {
            throw ValidationException("问卷测评已过期，无法提交")
        }

        this.answers = responses
        this.status = AssessmentStatus.COMPLETED
        this.completedAt = LocalDateTime.now()

        registerEvent(
            AssessmentCompletedEvent(
                assignmentId = this.id ?: 0L,
                studentId = this.studentId,
                batteryCode = this.batteryCode.value,
                completedTests = completedTests,
                completedAt = this.completedAt!!
            )
        )
    }

    fun isExpired(currentDate: LocalDate = LocalDate.now()): Boolean {
        return dueDate != null && currentDate.isAfter(dueDate) && status == AssessmentStatus.PENDING
    }

    fun revoke(revokerId: Long) {
        if (this.status != AssessmentStatus.PENDING) {
            throw ConflictException("Only pending assignments can be revoked")
        }
        if (this.assignedByUserId != revokerId) {
            throw com.medicalsystem.backend.exception.ForbiddenException("Only the original assigner can revoke this assignment")
        }
        this.status = AssessmentStatus.REVOKED
        this.revokedAt = LocalDateTime.now()

        if (this.id != null) {
            registerEvent(
                com.medicalsystem.backend.event.AssessmentRevokedEvent(
                    studentId = this.studentId,
                    batteryCode = this.batteryCode.value,
                    revokerId = revokerId
                )
            )
        }
    }

    fun recordProgress(newAnswers: Map<String, Any>, currentUserId: Long) {
        if (this.studentId != currentUserId) {
            throw com.medicalsystem.backend.exception.ForbiddenException("You can only record progress for your own assessments")
        }
        if (this.status != AssessmentStatus.PENDING) {
            throw IllegalStateException("Cannot record progress for a non-pending assessment")
        }
        this.answers = newAnswers
    }

    fun calculateProgress(totalQuestions: Int): Int {
        if (this.status == AssessmentStatus.COMPLETED) return 100
        val safeTotal = if (totalQuestions > 0) totalQuestions else 1
        val answered = this.answers?.size ?: 0
        return ((answered.toDouble() / safeTotal) * 100).toInt()
    }
}
