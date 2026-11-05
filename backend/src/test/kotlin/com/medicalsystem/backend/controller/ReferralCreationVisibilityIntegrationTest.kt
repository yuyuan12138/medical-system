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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.concurrent.atomic.AtomicLong

/**
 * The referral WRITE path must enforce the same StudentVisibilityPolicy as reads:
 * a TEACHER may only initiate referrals for students assigned to them, while
 * HEAD_COUNSELLOR / SYSTEM_ADMIN may refer any student.
 *
 * Deliberately NOT @Transactional: each test drives a real HTTP-ish request through the
 * standalone MockMvc; seed data is committed via transactional repository saves.
 */
@SpringBootTest
class ReferralCreationVisibilityIntegrationTest {

    @Autowired
    private lateinit var referralController: ReferralController

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

    private var owningTeacherUserId: Long = 0
    private var otherTeacherUserId: Long = 0
    private var headCounsellorUserId: Long = 0
    private var studentId: Long = 0

    @BeforeEach
    fun seed() {
        val sfx = seq.incrementAndGet()

        val college = collegeJpaRepository.save(CollegeEntity(name = "refvis-学院-$sfx"))
        val major = majorJpaRepository.save(MajorEntity(name = "refvis-专业-$sfx", college = college))

        val owningTeacher = userJpaRepository.save(
            UserEntity(name = "refvis-主管教师-$sfx", role = UserRole.TEACHER, email = EmailAddress("refvis-owner-$sfx@t.com"), status = AccountStatus.ACTIVE)
        )
        val owningTeacherEntity = teacherJpaRepository.save(
            TeacherEntity(userId = owningTeacher.id!!, employeeNumber = "TV-OWN-$sfx", college = college)
        )

        val otherTeacher = userJpaRepository.save(
            UserEntity(name = "refvis-其他教师-$sfx", role = UserRole.TEACHER, email = EmailAddress("refvis-other-$sfx@t.com"), status = AccountStatus.ACTIVE)
        )
        teacherJpaRepository.save(TeacherEntity(userId = otherTeacher.id!!, employeeNumber = "TV-OTH-$sfx", college = college))

        val headCounsellor = userJpaRepository.save(
            UserEntity(name = "refvis-心理主管-$sfx", role = UserRole.HEAD_COUNSELLOR, email = EmailAddress("refvis-hc-$sfx@t.com"), status = AccountStatus.ACTIVE)
        )

        val studentUser = userJpaRepository.save(
            UserEntity(name = "refvis-学生-$sfx", role = UserRole.STUDENT, email = EmailAddress("refvis-stu-$sfx@t.com"), status = AccountStatus.ACTIVE)
        )
        val student = studentJpaRepository.save(
            StudentEntity(
                id = studentUser.id!!,
                studentNumber = "TV-$sfx",
                name = "refvis-学生-$sfx",
                major = major,
                enrollmentDate = java.time.LocalDate.now(),
                assignedTeacher = owningTeacherEntity
            )
        )

        owningTeacherUserId = owningTeacher.id!!
        otherTeacherUserId = otherTeacher.id!!
        headCounsellorUserId = headCounsellor.id!!
        studentId = student.id

        mockMvc = MockMvcBuilders.standaloneSetup(referralController)
            .setControllerAdvice(GlobalExceptionHandler())
            .setCustomArgumentResolvers(TestUserResolver(userJpaRepository))
            .setMessageConverters(
                MappingJackson2HttpMessageConverter(
                    com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                )
            )
            .build()
    }

    private fun referralBody() = """
        {
          "studentId": $studentId,
          "actionType": "draft",
          "title": "visibility test referral",
          "reason": "integration test",
          "riskLevel": "LOW"
        }
    """.trimIndent()

    @Test
    fun `teacher cannot create referral for student outside their assigned scope`() {
        val result = mockMvc.perform(
            post("/api/referrals")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "mock-token-$otherTeacherUserId")
                .content(referralBody())
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("referral_target_not_visible"))
            .andReturn()

        assertTrue(result.response.contentAsString.contains("referral_target_not_visible"))
    }

    @Test
    fun `teacher can create referral for an assigned student`() {
        mockMvc.perform(
            post("/api/referrals")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "mock-token-$owningTeacherUserId")
                .content(referralBody())
        ).andExpect(status().isCreated())
    }

    @Test
    fun `head counsellor can create referral for any student`() {
        mockMvc.perform(
            post("/api/referrals")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "mock-token-$headCounsellorUserId")
                .content(referralBody())
        ).andExpect(status().isCreated())
    }

    /** Resolves @CurrentUser from an Authorization header of the form `mock-token-{userId}`. */
    class TestUserResolver(private val userJpaRepository: UserJpaRepository) : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter): Boolean {
            return parameter.parameterType == User::class.java &&
                parameter.hasParameterAnnotation(com.medicalsystem.backend.security.CurrentUser::class.java)
        }

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: WebDataBinderFactory?
        ): Any? {
            val token = webRequest.getHeader("Authorization") ?: return null
            val id = token.removePrefix("mock-token-").toLongOrNull() ?: return null
            val entity = userJpaRepository.findById(id).orElse(null) ?: return null
            return User(
                id = entity.id,
                name = entity.name,
                email = entity.email,
                role = entity.role,
                status = entity.status,
                phone = entity.phone
            )
        }
    }
}
