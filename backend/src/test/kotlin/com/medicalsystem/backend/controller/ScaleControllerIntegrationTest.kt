package com.medicalsystem.backend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.medicalsystem.backend.config.AssessmentCatalogLoader
import com.medicalsystem.backend.dto.SaveDraftRequest
import com.medicalsystem.backend.dto.SubmitScaleRequest
import com.medicalsystem.backend.entity.*
import com.medicalsystem.backend.exception.GlobalExceptionHandler
import com.medicalsystem.backend.model.*
import com.medicalsystem.backend.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.LocalDate

@SpringBootTest
@Transactional
class ScaleControllerIntegrationTest {

    @Autowired
    private lateinit var scaleController: ScaleController

    @Autowired
    private lateinit var studentProfileController: StudentProfileController

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var studentJpaRepository: StudentJpaRepository

    @Autowired
    private lateinit var majorJpaRepository: MajorJpaRepository

    @Autowired
    private lateinit var collegeJpaRepository: CollegeJpaRepository

    @Autowired
    private lateinit var assessmentAssignmentJpaRepository: AssessmentAssignmentJpaRepository

    @Autowired
    private lateinit var catalogLoader: AssessmentCatalogLoader

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    private lateinit var studentUser: UserEntity
    private lateinit var studentEntity: StudentEntity

    @BeforeEach
    fun setUp() {
        val college = collegeJpaRepository.findAll().firstOrNull() ?: collegeJpaRepository.save(CollegeEntity(name = "计算机学院"))
        val major = majorJpaRepository.findAll().firstOrNull() ?: majorJpaRepository.save(MajorEntity(name = "计算机科学与技术", college = college))

        studentUser = userJpaRepository.save(
            UserEntity(
                name = "移动端学生",
                role = UserRole.STUDENT,
                email = EmailAddress("mobile_stu_${System.currentTimeMillis()}@univ.edu.cn"),
                status = AccountStatus.ACTIVE,
                phone = "13899998888"
            )
        )

        studentEntity = studentJpaRepository.save(
            StudentEntity(
                id = studentUser.id,
                studentNumber = "STU_MOB_999",
                name = "移动端学生",
                major = major,
                enrollmentDate = LocalDate.of(2024, 9, 1)
            )
        )

        val userModel = User(
            id = studentUser.id,
            name = studentUser.name,
            email = studentUser.email,
            role = studentUser.role,
            status = studentUser.status,
            phone = studentUser.phone
        )

        val argumentResolver = object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter): Boolean {
                return parameter.hasParameterAnnotation(com.medicalsystem.backend.security.CurrentUser::class.java)
            }

            override fun resolveArgument(
                parameter: MethodParameter,
                mavContainer: ModelAndViewContainer?,
                webRequest: NativeWebRequest,
                binderFactory: WebDataBinderFactory?
            ): Any? = userModel
        }

        mockMvc = MockMvcBuilders.standaloneSetup(scaleController, studentProfileController)
            .setCustomArgumentResolvers(argumentResolver)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `testListScales_InitialState_ReturnsNotStarted`() {
        mockMvc.perform(get("/api/scales"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].code").value("comprehensive_student_intake_survey"))
            .andExpect(jsonPath("$[0].status").value("NOT_STARTED"))
            .andExpect(jsonPath("$[0].questionCount").value(377))
    }

    @Test
    fun `testSaveAndGetDraft_UpdatesStatusToInProgress`() {
        val answers = mapOf<String, Any>(
            "demo_name" to "李四",
            "G1" to 1
        )
        val saveDraftReq = SaveDraftRequest(answers = answers, updatedAt = 1700000000000L)

        // Save draft
        mockMvc.perform(
            put("/api/scales/comprehensive_student_intake_survey/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saveDraftReq))
        )
            .andExpect(status().isNoContent)

        // Verify get draft
        mockMvc.perform(get("/api/scales/comprehensive_student_intake_survey/draft"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scaleCode").value("comprehensive_student_intake_survey"))
            .andExpect(jsonPath("$.answers.demo_name").value("李四"))

        // Verify list scales reflects IN_PROGRESS
        mockMvc.perform(get("/api/scales"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$[0].answeredCount").value(2))
    }

    @Test
    fun `testGetScaleDetail_ReturnsQuestionsAndSections`() {
        mockMvc.perform(get("/api/scales/comprehensive_student_intake_survey"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("comprehensive_student_intake_survey"))
            .andExpect(jsonPath("$.questions.length()").value(377))
            .andExpect(jsonPath("$.sections.length()").value(33))
    }

    @Test
    fun `testGetStudentProfile_ReturnsProfileAndDemographics`() {
        mockMvc.perform(get("/api/students/me/profile"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.studentNumber").value("STU_MOB_999"))
            .andExpect(jsonPath("$.fullName").value("移动端学生"))
            .andExpect(jsonPath("$.phone").value("13899998888"))
            .andExpect(jsonPath("$.demographics.major").value(studentEntity.major.name))
            .andExpect(jsonPath("$.demographics.isSubmitted").value(false))
    }

    @Test
    fun `testSubmitScale_CompletesAssessmentAndUpdatesProfile`() {
        val allQuestions = catalogLoader.getScaleDetail("comprehensive_student_intake_survey")?.questions ?: emptyList()
        val completeAnswers = mutableMapOf<String, Any>()
        for (q in allQuestions) {
            completeAnswers[q.code] = when (q.type) {
                "text" -> "测试内容"
                "date" -> "2004-05-12"
                "number" -> 175.0
                "slider" -> 5.0
                else -> 0
            }
        }
        completeAnswers["demo_id_card"] = "110101200405125555"
        completeAnswers["G1"] = 1
        completeAnswers["G6"] = 1

        val submitReq = SubmitScaleRequest(answers = completeAnswers)

        mockMvc.perform(
            post("/api/scales/comprehensive_student_intake_survey/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitReq))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.scaleCode").value("comprehensive_student_intake_survey"))

        // Profile should now reflect isDemographicsSubmitted = true
        mockMvc.perform(get("/api/students/me/profile"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isDemographicsSubmitted").value(true))
            .andExpect(jsonPath("$.demographics.idCardNumber").value("110101200405125555"))
            .andExpect(jsonPath("$.demographics.gender").value("MALE"))
            .andExpect(jsonPath("$.demographics.homeAddress").value("城市"))

        // List scales should now reflect COMPLETED
        mockMvc.perform(get("/api/scales"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[0].completionPercentage").value(100))
    }

    @Test
    fun `testSubmitScale_OnCompletedAssignment_Returns409NotSilent200`() {
        // Given: the student has already completed this scale (via any entry point)
        assessmentAssignmentJpaRepository.save(
            AssessmentAssignmentEntity(
                student = studentEntity,
                assignedByUser = studentUser,
                batteryCode = "comprehensive_student_intake_survey",
                status = AssessmentStatus.COMPLETED,
                assignedAt = java.time.LocalDateTime.now(),
                completedAt = java.time.LocalDateTime.now(),
                answersJson = """{"G1":1}"""
            )
        )

        val submitReq = SubmitScaleRequest(answers = mapOf<String, Any>("G1" to 2))

        // When/Then: a retake attempt via the scale path must be rejected with 409 (same as
        // POST /api/assessments/{id}/submit), not silently accepted with 200.
        mockMvc.perform(
            post("/api/scales/comprehensive_student_intake_survey/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitReq))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("问卷测评已完成，不可重复提交"))
    }

    @Test
    fun `testGetDraftAndListScales_FallsBackToAssignmentAnswersJson`() {
        assessmentAssignmentJpaRepository.save(
            AssessmentAssignmentEntity(
                student = studentEntity,
                assignedByUser = studentUser,
                batteryCode = "comprehensive_student_intake_survey",
                status = AssessmentStatus.PENDING,
                assignedAt = java.time.LocalDateTime.now(),
                answersJson = """{"demo_name":"桌面端草稿","phq9_1":1,"phq9_2":2}"""
            )
        )

        mockMvc.perform(get("/api/scales/comprehensive_student_intake_survey/draft"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answers.demo_name").value("桌面端草稿"))

        mockMvc.perform(get("/api/scales"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$[0].answeredCount").value(3))
    }
}
