package com.medicalsystem.backend.service

import com.medicalsystem.backend.dto.StudentDto
import com.medicalsystem.backend.model.College
import com.medicalsystem.backend.model.Major
import com.medicalsystem.backend.model.RiskStatus
import com.medicalsystem.backend.model.Student
import com.medicalsystem.backend.repository.MajorRepository
import com.medicalsystem.backend.repository.StudentHealthProfileRepository
import com.medicalsystem.backend.repository.StudentRepository
import com.medicalsystem.backend.mapper.StudentMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional
import com.medicalsystem.backend.event.DomainEventPublisher

@ExtendWith(MockitoExtension::class)
class StudentServiceTest {

    @Mock
    private lateinit var studentRepository: StudentRepository

    @Mock
    private lateinit var majorRepository: MajorRepository
    
    @Mock
    private lateinit var studentMapper: StudentMapper
    
    @Mock
    private lateinit var healthProfileRepository: StudentHealthProfileRepository

    @Mock
    private lateinit var studentHealthProfileJpaRepository: com.medicalsystem.backend.repository.StudentHealthProfileJpaRepository
    
    @Mock
    private lateinit var eventPublisher: DomainEventPublisher
    
    @Mock
    private lateinit var psychometricSummaryMapper: com.medicalsystem.backend.mapper.PsychometricSummaryMapper

    @InjectMocks
    private lateinit var studentService: StudentService

    @Test
    fun `fetchAllStudents returns visible students`() {
        val user = com.medicalsystem.backend.model.User(1L, "Teacher", com.medicalsystem.backend.model.EmailAddress("t@t.com"), null, com.medicalsystem.backend.model.UserRole.TEACHER)
        val college = College(1L, "Engineering")
        val major = Major(1L, "CS", college)
        val model = Student(1L, "S123", "John Doe", major, LocalDate.of(2023, 9, 1), RiskStatus.LOW, null)
        val dto = StudentDto("1", "S123", "John Doe", 1L, "CS", LocalDate.of(2023, 9, 1), "BACHELOR", 1L, RiskStatus.LOW)
        
        `when`(studentRepository.findVisibleStudentsFor(user)).thenReturn(listOf(model))
        `when`(studentMapper.toDto(model)).thenReturn(dto)

        val result = studentService.fetchAllStudents(user)

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun `fetchHighRiskStudents returns only visible flagged students sorted high first`() {
        val user = com.medicalsystem.backend.model.User(1L, "HC", com.medicalsystem.backend.model.EmailAddress("hc@t.com"), null, com.medicalsystem.backend.model.UserRole.HEAD_COUNSELLOR)
        val college = College(1L, "Engineering")
        val major = Major(1L, "CS", college)
        val s1 = Student(1L, "S001", "High Student", major, LocalDate.of(2023, 9, 1), RiskStatus.HIGH, null)
        val s2 = Student(2L, "S002", "Medium Student", major, LocalDate.of(2023, 9, 1), RiskStatus.MEDIUM, null)
        val s3 = Student(3L, "S003", "Invisible", major, LocalDate.of(2023, 9, 1), RiskStatus.HIGH, null)
        val flag = com.medicalsystem.backend.entity.RiskFlagEntity(
            id = 10L,
            name = com.medicalsystem.backend.model.RiskFlagName.SUICIDAL_IDEATION,
            status = com.medicalsystem.backend.model.FlagStatus.POSITIVE,
            healthProfile = null
        )
        fun profile(studentId: Long, status: RiskStatus, flags: List<com.medicalsystem.backend.entity.RiskFlagEntity>) =
            com.medicalsystem.backend.entity.StudentHealthProfileEntity(id = studentId, studentId = studentId, riskStatus = status, riskFlags = flags.toMutableList())

        `when`(studentRepository.findVisibleStudentsFor(user)).thenReturn(listOf(s1, s2))
        `when`(studentHealthProfileJpaRepository.findByRiskStatusIn(listOf(RiskStatus.HIGH, RiskStatus.MEDIUM)))
            .thenReturn(listOf(profile(2, RiskStatus.MEDIUM, emptyList()), profile(1, RiskStatus.HIGH, listOf(flag)), profile(3, RiskStatus.HIGH, emptyList())))
        `when`(studentRepository.findAllById(setOf(1L, 2L, 3L))).thenReturn(listOf(s1, s2, s3))

        val result = studentService.fetchHighRiskStudents(user, includeMedium = true)

        assertEquals(listOf("High Student", "Medium Student"), result.map { it.studentName })
        assertEquals(RiskStatus.HIGH, result[0].riskStatus)
        assertEquals(listOf("SUICIDAL_IDEATION"), result[0].riskFlags)
    }

    @Test
    fun `fetchStudentDetails throws exception when not visible`() {
        val user = com.medicalsystem.backend.model.User(1L, "Teacher", com.medicalsystem.backend.model.EmailAddress("t@t.com"), null, com.medicalsystem.backend.model.UserRole.TEACHER)
        `when`(studentRepository.findByIdAndVisibleTo(1L, user)).thenReturn(Optional.empty())
        
        assertThrows(com.medicalsystem.backend.exception.StudentNotFoundException::class.java) {
            studentService.fetchStudentDetails(1L, user)
        }
    }
}
