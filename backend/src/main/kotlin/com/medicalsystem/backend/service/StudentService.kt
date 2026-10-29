package com.medicalsystem.backend.service

import com.medicalsystem.backend.dto.StudentDto
import com.medicalsystem.backend.entity.StudentEntity
import com.medicalsystem.backend.repository.StudentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.medicalsystem.backend.model.RiskStatus
import com.medicalsystem.backend.mapper.StudentMapper
import com.medicalsystem.backend.exception.ResourceNotFoundException
import com.medicalsystem.backend.exception.ConflictException
import com.medicalsystem.backend.exception.ValidationException
import org.slf4j.LoggerFactory
import com.medicalsystem.backend.repository.MajorRepository
import com.medicalsystem.backend.repository.StudentHealthProfileRepository
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(readOnly = true)
class StudentService(
    private val studentRepository: StudentRepository,
    private val majorRepository: MajorRepository,
    private val studentMapper: StudentMapper,
    private val healthProfileRepository: StudentHealthProfileRepository,
    private val eventPublisher: com.medicalsystem.backend.event.DomainEventPublisher,
    private val psychometricSummaryMapper: com.medicalsystem.backend.mapper.PsychometricSummaryMapper,
    private val studentHealthProfileJpaRepository: com.medicalsystem.backend.repository.StudentHealthProfileJpaRepository
) {
    private val logger = LoggerFactory.getLogger(StudentService::class.java)

    fun fetchAllStudents(user: com.medicalsystem.backend.model.User): List<StudentDto> {
        val students = studentRepository.findVisibleStudentsFor(user)
        return students.map { studentMapper.toDto(it) }
    }

    fun fetchStudentDetails(id: Long, user: com.medicalsystem.backend.model.User): StudentDto {
        val model = studentRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { com.medicalsystem.backend.exception.StudentNotFoundException(id) }
        return studentMapper.toDto(model)
    }

    @Transactional
    fun registerStudent(dto: StudentDto): StudentDto {
        val majorId = dto.majorId ?: throw ValidationException("majorId must not be null")

        if (studentRepository.existsByStudentNumber(dto.studentNumber)) {
            throw com.medicalsystem.backend.exception.DuplicateStudentException(dto.studentNumber)
        }

        val major = majorRepository.findById(majorId)
            .orElseThrow { ResourceNotFoundException("Major with ID $majorId not found") }

        val model = com.medicalsystem.backend.model.Student(
            id = 0,
            studentNumber = dto.studentNumber,
            name = dto.name,
            major = major,
            enrollmentDate = dto.enrollmentDate,
            riskStatus = dto.riskLevel ?: RiskStatus.LOW,
            demographics = null
        )
        
        val savedModel = studentRepository.save(model)
        savedModel.register(dto.riskLevel?.name)
        savedModel.getDomainEvents().forEach { eventPublisher.publish(it) }
        savedModel.clearDomainEvents()
        
        val healthProfile = com.medicalsystem.backend.model.StudentHealthProfileFactory.createInitialProfile(
            studentId = savedModel.id
        )
        healthProfileRepository.save(healthProfile)
        
        logger.info("Successfully registered student with ID: ${savedModel.id} and number: ${savedModel.studentNumber}")
        
        return studentMapper.toDto(savedModel)
    }

    @Transactional(readOnly = true)
    fun fetchHighRiskStudents(user: com.medicalsystem.backend.model.User, includeMedium: Boolean): List<com.medicalsystem.backend.dto.HighRiskStudentDto> {
        val statuses = if (includeMedium) {
            listOf(com.medicalsystem.backend.model.RiskStatus.HIGH, com.medicalsystem.backend.model.RiskStatus.MEDIUM)
        } else {
            listOf(com.medicalsystem.backend.model.RiskStatus.HIGH)
        }
        val profiles = studentHealthProfileJpaRepository.findByRiskStatusIn(statuses)
        if (profiles.isEmpty()) return emptyList()
        val visibleIds = studentRepository.findVisibleStudentsFor(user).map { it.id }.toSet()
        val students = studentRepository.findAllById(profiles.map { it.studentId }.toSet()).associateBy { it.id }
        return profiles
            .filter { visibleIds.contains(it.studentId) && students.containsKey(it.studentId) }
            .map { profile ->
                val student = students.getValue(profile.studentId)
                com.medicalsystem.backend.dto.HighRiskStudentDto(
                    studentId = profile.studentId,
                    studentName = student.name,
                    studentNumber = student.studentNumber,
                    riskStatus = profile.riskStatus,
                    riskFlags = profile.riskFlags.filter { f -> f.status == com.medicalsystem.backend.model.FlagStatus.POSITIVE }.map { it.name.name },
                    scidDiagnosis = profile.scidDiagnosis
                )
            }
            .sortedWith(compareBy({ it.riskStatus != com.medicalsystem.backend.model.RiskStatus.HIGH }, { it.studentNumber }))
    }

    fun fetchPsychometricSummary(id: Long, user: com.medicalsystem.backend.model.User): com.medicalsystem.backend.dto.PsychometricsSummaryDto {
        val entity = studentRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow {
                logger.error("Student with ID $id not found when fetching psychometrics.")
                com.medicalsystem.backend.exception.StudentNotFoundException(id)
            }
            
        val profile = healthProfileRepository.findByStudentId(id).getOrNull()
            
        return psychometricSummaryMapper.toDto(entity, profile)
    }
}
