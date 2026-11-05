package com.medicalsystem.backend.controller

import com.medicalsystem.backend.entity.CollegeEntity
import com.medicalsystem.backend.entity.MajorEntity
import com.medicalsystem.backend.entity.StudentEntity
import com.medicalsystem.backend.entity.TeacherEntity
import com.medicalsystem.backend.entity.UserEntity
import com.medicalsystem.backend.exception.GlobalExceptionHandler
import com.medicalsystem.backend.model.AccountStatus
import com.medicalsystem.backend.model.EmailAddress
import com.medicalsystem.backend.model.User
import com.medicalsystem.backend.model.UserRole
import com.medicalsystem.backend.repository.CollegeJpaRepository
import com.medicalsystem.backend.repository.MajorJpaRepository
import com.medicalsystem.backend.repository.StudentJpaRepository
import com.medicalsystem.backend.repository.TeacherJpaRepository
import com.medicalsystem.backend.repository.UserJpaRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.MethodParameter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.concurrent.atomic.AtomicLong

/**
 * Freshly registered students may have no health profile / demographics / assignments yet.
 * These endpoints must return 200 with empty/default data (or 404 on visibility miss) —
 * never HTTP 500.
 *
 * Deliberately NOT @Transactional: the me/profile lazy-loading bug only reproduces when no
 * persistence session is already open in the test thread. Seed data is committed via
 * transactional repository saves.
 */
@SpringBootTest
class FreshStudentProfileIntegrationTest {

    @Autowired
    private lateinit var studentController: StudentController

    @Autowired
    private lateinit var studentProfileController: StudentProfileController

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var collegeJpaRepository: CollegeJpaRepository

    @Autowired
    private lateinit var majorJpaRepository: MajorJpaRepository

    @Autowired
    private lateinit var teacherJpaRepository: TeacherJpaRepository

    @Autowired
    private lateinit var studentJpaRepository: StudentJpaRepository

    private lateinit var mockMvc: MockMvc

    private val seq = AtomicLong(System.currentTimeMillis())

    private var freshStudentUserId: Long = 0
    private var uninvolvedTeacherUserId: Long = 0

    @BeforeEach
    fun seed() {
        val sfx = seq.incrementAndGet()

        val college = collegeJpaRepository.save(CollegeEntity(name = "fresh-学院-$sfx"))
        val major = majorJpaRepository.save(MajorEntity(name = "fresh-专业-$sfx", college = college))

        val freshStudent = userJpaRepository.save(
            UserEntity(name = "fresh-新生-$sfx", role = UserRole.STUDENT, email = EmailAddress("fresh-stu-$sfx@t.com"), status = AccountStatus.ACTIVE)
        )
        studentJpaRepository.save(
            StudentEntity(
                id = freshStudent.id!!,
                studentNumber = "FR-$sfx",
                name = "fresh-新生-$sfx",
                major = major,
                enrollmentDate = java.time.LocalDate.now()
            )
        )

        val teacher = userJpaRepository.save(
            UserEntity(name = "fresh-无关教师-$sfx", role = UserRole.TEACHER, email = EmailAddress("fresh-teacher-$sfx@t.com"), status = AccountStatus.ACTIVE)
        )
        teacherJpaRepository.save(TeacherEntity(userId = teacher.id!!, employeeNumber = "FR-T-$sfx", college = college))

        freshStudentUserId = freshStudent.id!!
        uninvolvedTeacherUserId = teacher.id!!

        mockMvc = MockMvcBuilders.standaloneSetup(studentController, studentProfileController)
            .setControllerAdvice(GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                ReferralCreationVisibilityIntegrationTest.TestUserResolver(userJpaRepository)
            )
            .build()
    }

    @Test
    fun `psychometrics returns 200 with empty defaults for accessible student without profile`() {
        mockMvc.perform(
            get("/api/students/$freshStudentUserId/psychometrics")
                .header("Authorization", "mock-token-$freshStudentUserId")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tests").isEmpty())
            .andExpect(jsonPath("$.scores").isEmpty())
            .andExpect(jsonPath("$.radarData").isEmpty())
    }

    @Test
    fun `psychometrics returns 404 not 500 for student outside requester visibility`() {
        mockMvc.perform(
            get("/api/students/$freshStudentUserId/psychometrics")
                .header("Authorization", "mock-token-$uninvolvedTeacherUserId")
        )
            .andExpect(status().isNotFound())
    }

    @Test
    fun `me profile returns 200 for freshly registered student`() {
        mockMvc.perform(
            get("/api/students/me/profile")
                .header("Authorization", "mock-token-$freshStudentUserId")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isDemographicsSubmitted").value(false))
            .andExpect(jsonPath("$.demographics.isSubmitted").value(false))
    }
}
