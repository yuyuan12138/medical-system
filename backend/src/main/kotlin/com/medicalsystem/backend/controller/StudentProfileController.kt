package com.medicalsystem.backend.controller

import com.medicalsystem.backend.dto.StudentDemographicsProfileDto
import com.medicalsystem.backend.dto.StudentProfileResponseDto
import com.medicalsystem.backend.exception.NotFoundException
import com.medicalsystem.backend.exception.UnauthorizedException
import com.medicalsystem.backend.model.AssessmentStatus
import com.medicalsystem.backend.model.User
import com.medicalsystem.backend.repository.AssessmentAssignmentJpaRepository
import com.medicalsystem.backend.repository.StudentJpaRepository
import com.medicalsystem.backend.repository.UserJpaRepository
import com.medicalsystem.backend.repository.StudentHealthProfileJpaRepository
import com.medicalsystem.backend.model.RiskStatus
import com.medicalsystem.backend.model.UserRole
import com.medicalsystem.backend.exception.ForbiddenException
import com.medicalsystem.backend.security.CurrentUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/students")
class StudentProfileController(
    private val studentJpaRepository: StudentJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val assignmentJpaRepository: AssessmentAssignmentJpaRepository,
    private val studentHealthProfileJpaRepository: StudentHealthProfileJpaRepository
) {

    data class UpdateHealthProfileDto(
        val scidDiagnosis: String?,
        val riskStatus: RiskStatus? = null
    )

    @org.springframework.web.bind.annotation.PutMapping("/{id}/health-profile")
    fun updateHealthProfile(
        @org.springframework.web.bind.annotation.PathVariable id: Long,
        @org.springframework.web.bind.annotation.RequestBody dto: UpdateHealthProfileDto,
        @CurrentUser user: User?
    ): ResponseEntity<Map<String, String>> {
        val currentUser = user ?: throw UnauthorizedException("Unauthorized")
        if (currentUser.role !in listOf(UserRole.DOCTOR, UserRole.HEAD_COUNSELLOR, UserRole.SYSTEM_ADMIN)) {
            throw ForbiddenException("Only doctors and head counsellors can update clinical diagnosis")
        }
        if (!studentJpaRepository.existsById(id)) {
            throw NotFoundException("Student not found for id $id")
        }
        val profile = studentHealthProfileJpaRepository.findByStudentId(id).orElseGet {
            com.medicalsystem.backend.entity.StudentHealthProfileEntity(studentId = id, riskStatus = RiskStatus.LOW)
        }
        dto.scidDiagnosis?.let { profile.scidDiagnosis = it }
        dto.riskStatus?.let { profile.riskStatus = it }
        studentHealthProfileJpaRepository.save(profile)
        return ResponseEntity.ok(mapOf("status" to "updated"))
    }

    @GetMapping("/me/profile")
    fun getMyProfile(@CurrentUser user: User?): ResponseEntity<StudentProfileResponseDto> {
        val currentUser = user ?: throw UnauthorizedException("Unauthorized")
        val student = studentJpaRepository.findById(currentUser.id).orElseThrow {
            NotFoundException("Student profile not found for user ${currentUser.id}")
        }
        val userEntity = userJpaRepository.findById(currentUser.id).orElse(null)

        val assignments = assignmentJpaRepository.findByStudentId(currentUser.id)
        val completedAssignment = assignments.firstOrNull {
            it.batteryCode == "comprehensive_student_intake_survey" && it.status == AssessmentStatus.COMPLETED
        }
        val isSubmitted = completedAssignment != null || student.demographics != null

        val demographicsDto = StudentDemographicsProfileDto(
            isSubmitted = isSubmitted,
            gender = student.demographics?.gender?.name,
            ethnicity = student.demographics?.ethnicity?.name,
            major = student.major.name,
            birthday = student.demographics?.dateOfBirth?.toString(),
            idCardNumber = student.demographics?.idCardNumber,
            email = student.demographics?.email,
            homeAddress = student.demographics?.homeAddress,
            emergencyContact = student.demographics?.emergencyContactName,
            emergencyPhone = student.demographics?.emergencyContactPhone,
            completedAt = completedAssignment?.completedAt?.toString()
        )

        val profile = StudentProfileResponseDto(
            studentNumber = student.studentNumber,
            fullName = student.name,
            phone = userEntity?.phone ?: "",
            registeredAt = student.enrollmentDate.toString(),
            isDemographicsSubmitted = isSubmitted,
            demographics = demographicsDto
        )

        return ResponseEntity.ok(profile)
    }
}
