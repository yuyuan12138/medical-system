package com.medicalsystem.backend.service

import com.medicalsystem.backend.dto.*
import com.medicalsystem.backend.exception.ForbiddenException
import com.medicalsystem.backend.exception.NotFoundException
import com.medicalsystem.backend.mapper.AssessmentMapper.toCatalogItemDto
import com.medicalsystem.backend.mapper.AssessmentMapper.toDetailsDto
import com.medicalsystem.backend.mapper.AssessmentMapper.toListItemDto
import com.medicalsystem.backend.model.*
import com.medicalsystem.backend.repository.AssessmentAssignmentRepository
import com.medicalsystem.backend.repository.AssessmentCohortSpecification
import com.medicalsystem.backend.repository.StudentRepository
import com.medicalsystem.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class AssessmentService(
    private val assignmentRepository: AssessmentAssignmentRepository,
    private val studentRepository: StudentRepository,
    private val userRepository: UserRepository,
    private val scaleRepository: AssessmentScaleRepository,
    private val scaleSettingRepository: com.medicalsystem.backend.repository.AssessmentScaleSettingJpaRepository,
    private val dispatchRepository: com.medicalsystem.backend.repository.AssessmentDispatchRepository,
    private val clock: Clock,
    private val scaleDraftService: ScaleDraftService? = null,
    private val studentJpaRepository: com.medicalsystem.backend.repository.StudentJpaRepository? = null,
    private val demographicsProjector: DemographicsProjector? = null,
    private val clinicalRiskEvaluator: ClinicalRiskEvaluator? = null,
    private val domainEventPublisher: com.medicalsystem.backend.event.DomainEventPublisher? = null
) {
    private val logger = LoggerFactory.getLogger(AssessmentService::class.java)

    @Transactional(readOnly = true)
    fun getAssessmentsForUser(user: User): List<AssessmentListItemDto> {
        val assignments = when (user.role) {
            UserRole.STUDENT -> assignmentRepository.findByStudentId(user.id)
            UserRole.TEACHER -> assignmentRepository.findByAssignedByUserId(user.id)
            UserRole.HEAD_COUNSELLOR, UserRole.SYSTEM_ADMIN -> assignmentRepository.findAll()
            else -> emptyList()
        }

        val assignerIds = assignments.map { it.assignedByUserId }.toSet()
        val assignerMap = userRepository.findAllById(assignerIds).associateBy { it.id }
        val scaleMap = scaleRepository.findAll().associateBy { it.batteryCode }

        return assignments.map { assignment ->
            val scale = scaleMap[assignment.batteryCode.value]
            val assignerName = assignerMap[assignment.assignedByUserId]?.name
            assignment.toListItemDto(scale, assignerName)
        }
    }

    @Transactional(readOnly = true)
    fun getAssessmentDetails(id: Long, user: User): AssessmentDetailsDto {
        val assignment = assignmentRepository.findById(id).orElseThrow {
            NotFoundException("Assessment assignment with id $id not found")
        }

        if (user.role == UserRole.STUDENT && assignment.studentId != user.id) {
            throw ForbiddenException("You do not have permission to view this assignment")
        }

        val scale = scaleRepository.findByBatteryCode(assignment.batteryCode.value).orElseThrow {
            NotFoundException("Assessment scale definition not found for ${assignment.batteryCode.value}")
        }
        val assignerName = userRepository.findById(assignment.assignedByUserId).orElse(null)?.name
        
        return scale.toDetailsDto(assignment, assignerName)
    }

    @Transactional(readOnly = true)
    fun getCatalog(): List<AssessmentCatalogItemDto> {
        val settingsMap = scaleSettingRepository.findAll().associateBy { it.batteryCode }
        return scaleRepository.findAll().map { scale ->
            val isEnabled = settingsMap[scale.batteryCode]?.isAvailable ?: true
            scale.toCatalogItemDto().copy(isEnabled = isEnabled)
        }
    }

    fun toggleScaleAvailability(batteryCode: String, isAvailable: Boolean, adminUser: User): AssessmentCatalogItemDto {
        if (adminUser.role != UserRole.SYSTEM_ADMIN) {
            throw ForbiddenException("Only administrators can manage assessment scale availability")
        }
        val scale = scaleRepository.findByBatteryCode(batteryCode).orElseThrow {
            NotFoundException("Assessment scale definition not found for $batteryCode")
        }
        val entity = scaleSettingRepository.findById(batteryCode).orElseGet {
            com.medicalsystem.backend.entity.AssessmentScaleSettingEntity(batteryCode = batteryCode, isAvailable = true)
        }
        entity.isAvailable = isAvailable
        entity.updatedAt = java.time.Instant.now()
        scaleSettingRepository.save(entity)
        return scale.toCatalogItemDto().copy(isEnabled = isAvailable)
    }

    fun assignToStudent(request: AssignAssessmentRequest, assigner: User): BatchAssignResultDto {
        if (assigner.role !in listOf(UserRole.TEACHER, UserRole.HEAD_COUNSELLOR, UserRole.SYSTEM_ADMIN)) {
            throw ForbiddenException("Only educators and administrators can assign assessments")
        }

        val settingsMap = scaleSettingRepository.findAll().associateBy { it.batteryCode }
        request.batteryCodes.forEach { batteryCode ->
            if (settingsMap[batteryCode]?.isAvailable == false) {
                throw com.medicalsystem.backend.exception.ValidationException("Assessment scale $batteryCode is currently unavailable")
            }
        }

        val student = studentRepository.findByIdAndVisibleTo(request.studentId, assigner).orElseThrow {
            NotFoundException("Student with id ${request.studentId} not found or not in your visibility scope")
        }

        var assignedCount = 0
        request.batteryCodes.forEach { batteryCode ->
            val existingPending = assignmentRepository.findPendingByStudentIdAndBatteryCode(student.id, batteryCode)
            if (existingPending.isEmpty) {
                val dispatch = AssessmentDispatch.createForIndividuals(
                    batteryCode = BatteryId(batteryCode),
                    dispatcherId = assigner.id,
                    dueDate = request.dueDate
                )
                val savedDispatch = dispatchRepository.save(dispatch)

                val newAssignment = AssessmentAssignment(
                    studentId = student.id,
                    assignedByUserId = assigner.id,
                    batteryCode = BatteryId(batteryCode),
                    dispatchId = savedDispatch.id,
                    status = AssessmentStatus.PENDING,
                    assignedAt = LocalDateTime.now(clock),
                    dueDate = request.dueDate
                )
                val savedAssignment = assignmentRepository.save(newAssignment)
                savedAssignment.initAssignedEvent()
                savedAssignment.getDomainEvents().forEach { domainEventPublisher?.publish(it) }
                savedAssignment.clearDomainEvents()

                savedDispatch.initDispatchedEvent(listOf(student.id))
                savedDispatch.getDomainEvents().forEach { domainEventPublisher?.publish(it) }
                savedDispatch.clearDomainEvents()
                assignedCount++
            }
        }

        return BatchAssignResultDto(
            assignedCount = assignedCount
        )
    }

    fun assignToCohort(request: AssignCohortAssessmentRequest, assigner: User): BatchAssignResultDto {
        if (assigner.role !in listOf(UserRole.TEACHER, UserRole.HEAD_COUNSELLOR, UserRole.SYSTEM_ADMIN)) {
            throw ForbiddenException("Only educators and administrators can assign assessments")
        }

        val settingsMap = scaleSettingRepository.findAll().associateBy { it.batteryCode }
        request.batteryCodes.forEach { batteryCode ->
            if (settingsMap[batteryCode]?.isAvailable == false) {
                throw com.medicalsystem.backend.exception.ValidationException("Assessment scale $batteryCode is currently unavailable")
            }
        }

        val spec = AssessmentCohortSpecification.buildSpecification(
            request.majorId, request.collegeId, request.academicYear, assigner
        )
        val visibleStudents = studentRepository.findAll(spec)

        if (visibleStudents.isEmpty()) {
            return BatchAssignResultDto(
                assignedCount = 0
            )
        }

        var totalAssigned = 0
        val studentIds = visibleStudents.map { it.id }
        
        request.batteryCodes.forEach { batteryCode ->
            val pendingAssignments = assignmentRepository.findPendingByStudentIdInAndBatteryCode(studentIds, batteryCode)
            val studentsWithPending = pendingAssignments.map { it.studentId }.toSet()
            val targetStudents = visibleStudents.filter { !studentsWithPending.contains(it.id) }
            
            if (targetStudents.isNotEmpty()) {
                val dispatch = AssessmentDispatch.createBatch(
                    batteryCode = BatteryId(batteryCode),
                    dispatcherId = assigner.id,
                    targetCohort = "Cohort (Major: ${request.majorId}, Year: ${request.academicYear})",
                    dueDate = request.dueDate
                )
                val savedDispatch = dispatchRepository.save(dispatch)

                val toSave = targetStudents.map { student ->
                    AssessmentAssignment(
                        studentId = student.id,
                        assignedByUserId = assigner.id,
                        batteryCode = BatteryId(batteryCode),
                        dispatchId = savedDispatch.id,
                        status = AssessmentStatus.PENDING,
                        assignedAt = LocalDateTime.now(clock),
                        dueDate = request.dueDate
                    )
                }
                
                val savedAssignments = assignmentRepository.saveAll(toSave)
                savedAssignments.forEach { saved ->
                    saved.initAssignedEvent()
                    saved.getDomainEvents().forEach { domainEventPublisher?.publish(it) }
                    saved.clearDomainEvents()
                }
                savedDispatch.initDispatchedEvent(toSave.map { it.studentId })
                savedDispatch.getDomainEvents().forEach { domainEventPublisher?.publish(it) }
                savedDispatch.clearDomainEvents()
                totalAssigned += toSave.size
            }
        }

        return BatchAssignResultDto(
            assignedCount = totalAssigned
        )
    }

    fun submitAssessment(
        assignmentId: Long,
        request: SubmitAssessmentRequest,
        currentUser: User
    ): AssessmentSubmissionResponseDto {
        val assignment = assignmentRepository.findById(assignmentId).orElseThrow {
            NotFoundException("Assessment assignment with id $assignmentId not found")
        }

        if (assignment.studentId != currentUser.id) {
            throw ForbiddenException("You can only submit your own assessments")
        }

        val scale = scaleRepository.findByBatteryCode(assignment.batteryCode.value).orElseThrow {
            NotFoundException("Assessment scale definition not found for ${assignment.batteryCode.value}")
        }
        val answersMap: Map<String, Any> = when (val a = request.answers) {
            is Map<*, *> -> a.entries.filter { it.key != null && it.value != null }.associate { it.key.toString() to it.value!! }
            is List<*> -> {
                a.mapNotNull { item ->
                    when (item) {
                        is AnswerSubmissionDto -> item.questionId to item.selectedValue
                        is Map<*, *> -> {
                            val qId = (item["questionId"] ?: item["id"])?.toString()
                            val value = item["selectedValue"] ?: item["value"]
                            if (qId != null && value != null) qId to value else null
                        }
                        else -> null
                    }
                }.toMap()
            }
            else -> emptyMap()
        }

        if (answersMap.isEmpty()) {
            throw com.medicalsystem.backend.exception.ValidationException("answers cannot be empty")
        }
        
        AssessmentScoringEngine.validateAnswers(scale, answersMap)
        
        val completedTests = scale.sections.mapNotNull { section ->
            AssessmentScoringEngine.scoreSectionOrNull(assignment.studentId, section, answersMap, assignment.id)
        }

        assignment.complete(
            responses = answersMap,
            completedTests = completedTests
        )
        assignment.completedAt = LocalDateTime.now(clock) // override to use clock

        val savedAssignment = assignmentRepository.save(assignment)

        // 1. Purge draft
        scaleDraftService?.purgeDraft(assignment.studentId, assignment.batteryCode.value)

        // 2. Project demographics (best-effort: a uniqueness race or stale answers must not fail the submission)
        try {
            studentJpaRepository?.findById(assignment.studentId)?.ifPresent { studentEntity ->
                demographicsProjector?.projectDemographics(studentEntity, answersMap)
                studentJpaRepository.save(studentEntity)
            }
        } catch (e: Exception) {
            logger.warn("Demographics projection failed for student {}: {}", assignment.studentId, e.message)
        }

        // 3. Clinical Risk Evaluation (runs before event publishing so AFTER_COMMIT listeners see updated riskStatus)
        val riskResult = clinicalRiskEvaluator?.evaluateRisk(assignment.studentId, answersMap, completedTests)
        if (riskResult != null) {
            assignment.severity = when (riskResult.riskStatus) {
                RiskStatus.HIGH -> ResultSeverity.HIGH
                RiskStatus.MEDIUM -> ResultSeverity.MODERATE
                RiskStatus.LOW -> ResultSeverity.LOW
            }
            assignmentRepository.save(assignment)
        }

        assignment.getDomainEvents().forEach { domainEventPublisher?.publish(it) }
        assignment.clearDomainEvents()

        return AssessmentSubmissionResponseDto(
            success = true,
            assignmentId = savedAssignment.id ?: assignmentId,
            totalQuestionsAnswered = answersMap.size,
            completedAt = savedAssignment.completedAt ?: LocalDateTime.now(clock)
        )
    }

    fun getDraftForAssignment(assignmentId: Long, currentUser: User): ScaleDraftDto? {
        val assignment = assignmentRepository.findById(assignmentId).orElseThrow {
            NotFoundException("Assessment assignment with id $assignmentId not found")
        }
        if (assignment.studentId != currentUser.id) {
            throw ForbiddenException("You can only access drafts for your own assessments")
        }
        return scaleDraftService?.getDraft(assignment.studentId, assignment.batteryCode.value)
    }

    fun saveDraftForAssignment(assignmentId: Long, request: SaveDraftRequest, currentUser: User) {
        val assignment = assignmentRepository.findById(assignmentId).orElseThrow {
            NotFoundException("Assessment assignment with id $assignmentId not found")
        }
        if (assignment.studentId != currentUser.id) {
            throw ForbiddenException("You can only save drafts for your own assessments")
        }
        scaleDraftService?.saveDraft(assignment.studentId, assignment.batteryCode.value, request)
    }

    fun recordProgress(
        assignmentId: Long,
        request: RecordProgressRequest,
        currentUser: User
    ) {
        val assignment = assignmentRepository.findById(assignmentId).orElseThrow {
            NotFoundException("Assessment assignment with id $assignmentId not found")
        }

        assignment.recordProgress(request.answers, currentUser.id)
        assignmentRepository.save(assignment)
        scaleDraftService?.saveDraft(
            assignment.studentId,
            assignment.batteryCode.value,
            SaveDraftRequest(answers = request.answers, updatedAt = System.currentTimeMillis())
        )
    }
}
