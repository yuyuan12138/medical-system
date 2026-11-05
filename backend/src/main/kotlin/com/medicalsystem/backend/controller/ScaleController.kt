package com.medicalsystem.backend.controller

import com.medicalsystem.backend.config.AssessmentCatalogLoader
import com.medicalsystem.backend.dto.*
import com.medicalsystem.backend.entity.AssessmentAssignmentEntity
import com.medicalsystem.backend.exception.ConflictException
import com.medicalsystem.backend.exception.NotFoundException
import com.medicalsystem.backend.exception.UnauthorizedException
import com.medicalsystem.backend.model.*
import com.medicalsystem.backend.repository.AssessmentAssignmentJpaRepository
import com.medicalsystem.backend.repository.StudentJpaRepository
import com.medicalsystem.backend.repository.UserJpaRepository
import com.medicalsystem.backend.security.CurrentUser
import com.medicalsystem.backend.service.AssessmentService
import com.medicalsystem.backend.service.ScaleDraftService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/scales")
class ScaleController(
    private val catalogLoader: AssessmentCatalogLoader,
    private val assignmentJpaRepository: AssessmentAssignmentJpaRepository,
    private val studentJpaRepository: StudentJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val assessmentService: AssessmentService,
    private val scaleDraftService: ScaleDraftService
) {

    @GetMapping
    fun listScales(@CurrentUser user: User?): ResponseEntity<List<ScaleSummaryDto>> {
        val currentUser = user ?: throw UnauthorizedException("Unauthorized")
        val scale = catalogLoader.getScaleDetail("comprehensive_student_intake_survey")
            ?: return ResponseEntity.ok(emptyList())

        val assignments = assignmentJpaRepository.findByStudentId(currentUser.id)
        val surveyAssignment = assignments.firstOrNull { it.batteryCode == "comprehensive_student_intake_survey" }

        val (status, answeredCount, percentage) = when {
            surveyAssignment?.status == AssessmentStatus.COMPLETED -> {
                Triple("COMPLETED", scale.questions.size, 100)
            }
            else -> {
                val draft = scaleDraftService.getDraft(currentUser.id, "comprehensive_student_intake_survey")
                val nonEmptyCount = draft?.answers?.values?.count { v ->
                    when (v) {
                        is Collection<*> -> v.isNotEmpty()
                        else -> v.toString().isNotBlank()
                    }
                } ?: 0
                if (nonEmptyCount > 0) {
                    val pct = ((nonEmptyCount.toDouble() / scale.questions.size.coerceAtLeast(1)) * 100).toInt().coerceAtMost(100)
                    Triple("IN_PROGRESS", nonEmptyCount, pct)
                } else {
                    Triple("NOT_STARTED", 0, 0)
                }
            }
        }

        val summary = ScaleSummaryDto(
            code = scale.code,
            title = scale.title,
            subtitle = scale.subtitle,
            description = scale.description ?: "",
            questionCount = scale.questions.size,
            estimatedMinutes = scale.estimatedMinutes,
            status = status,
            answeredCount = answeredCount,
            completionPercentage = percentage
        )

        return ResponseEntity.ok(listOf(summary))
    }

    @GetMapping("/{code}")
    fun getScaleDetail(
        @PathVariable code: String,
        @CurrentUser user: User?
    ): ResponseEntity<AssessmentScale> {
        user ?: throw UnauthorizedException("Unauthorized")
        val scale = catalogLoader.getScaleDetail(code)
            ?: throw NotFoundException("Scale $code not found")
        return ResponseEntity.ok(scale)
    }

    @PostMapping("/{code}/submit")
    fun submitScale(
        @PathVariable code: String,
        @RequestBody request: SubmitScaleRequest,
        @CurrentUser user: User?
    ): ResponseEntity<SubmitScaleResponse> {
        val currentUser = user ?: throw UnauthorizedException("Unauthorized")
        var assignment = assignmentJpaRepository.findByStudentId(currentUser.id)
            .firstOrNull { it.batteryCode == code && it.status != AssessmentStatus.COMPLETED }

        if (assignment == null) {
            val completed = assignmentJpaRepository.findByStudentId(currentUser.id)
                .firstOrNull { it.batteryCode == code && it.status == AssessmentStatus.COMPLETED }
            if (completed != null) {
                // Consistent with POST /api/assessments/{id}/submit: a completed assignment must
                // not silently accept (and drop) new answers — surface 409 instead of 200.
                throw ConflictException("问卷测评已完成，不可重复提交")
            }

            val student = studentJpaRepository.findById(currentUser.id).orElseThrow {
                NotFoundException("Student not found for id ${currentUser.id}")
            }
            val userEntity = userJpaRepository.findById(currentUser.id).orElseThrow {
                NotFoundException("User not found for id ${currentUser.id}")
            }
            val newAssignment = AssessmentAssignmentEntity(
                student = student,
                assignedByUser = userEntity,
                batteryCode = code,
                status = AssessmentStatus.PENDING,
                assignedAt = LocalDateTime.now()
            )
            assignment = assignmentJpaRepository.save(newAssignment)
        }

        val submitReq = SubmitAssessmentRequest(answers = request.answers)
        assessmentService.submitAssessment(assignment.id!!, submitReq, currentUser)

        return ResponseEntity.ok(
            SubmitScaleResponse(
                scaleCode = code,
                status = "COMPLETED",
                completedAt = LocalDateTime.now().toString()
            )
        )
    }

    @GetMapping("/{code}/submission")
    fun getSubmission(
        @PathVariable code: String,
        @CurrentUser user: User?
    ): ResponseEntity<Map<String, Any>> {
        val currentUser = user ?: throw UnauthorizedException("Unauthorized")
        val assignment = assignmentJpaRepository.findByStudentId(currentUser.id)
            .firstOrNull { it.batteryCode == code && it.status == AssessmentStatus.COMPLETED }
            ?: return ResponseEntity.notFound().build()

        val parsedAnswers: Map<String, Any> = assignment.answersJson?.takeIf { it.isNotBlank() }?.let { json ->
            try {
                com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    json,
                    object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any>>() {}
                )
            } catch (_: Exception) {
                emptyMap()
            }
        } ?: emptyMap()

        return ResponseEntity.ok(
            mapOf(
                "scaleCode" to code,
                "status" to "COMPLETED",
                "completedAt" to (assignment.completedAt?.toString() ?: LocalDateTime.now().toString()),
                "answers" to parsedAnswers
            )
        )
    }

    @GetMapping("/{code}/draft")
    fun getDraft(
        @PathVariable code: String,
        @CurrentUser user: User?
    ): ResponseEntity<ScaleDraftDto> {
        val currentUser = user ?: throw UnauthorizedException("Unauthorized")
        val draft = scaleDraftService.getDraft(currentUser.id, code)
            ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(draft)
    }

    @PutMapping("/{code}/draft")
    fun saveDraft(
        @PathVariable code: String,
        @RequestBody request: SaveDraftRequest,
        @CurrentUser user: User?
    ): ResponseEntity<Void> {
        val currentUser = user ?: throw UnauthorizedException("Unauthorized")
        scaleDraftService.saveDraft(currentUser.id, code, request)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{code}/draft")
    fun deleteDraft(
        @PathVariable code: String,
        @CurrentUser user: User?
    ): ResponseEntity<Void> {
        val currentUser = user ?: throw UnauthorizedException("Unauthorized")
        scaleDraftService.purgeDraft(currentUser.id, code)
        return ResponseEntity.noContent().build()
    }
}
