package com.medicalsystem.backend.service

import com.medicalsystem.backend.entity.*
import com.medicalsystem.backend.model.*
import com.medicalsystem.backend.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@Transactional
class ClinicalRiskEvaluatorTest {

    @Autowired
    private lateinit var clinicalRiskEvaluator: ClinicalRiskEvaluator

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var studentJpaRepository: StudentJpaRepository

    @Autowired
    private lateinit var studentHealthProfileJpaRepository: StudentHealthProfileJpaRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var majorJpaRepository: MajorJpaRepository

    @Autowired
    private lateinit var collegeJpaRepository: CollegeJpaRepository

    private lateinit var studentUser: UserEntity
    private lateinit var headCouncillorUser: UserEntity

    @BeforeEach
    fun setUp() {
        val college = collegeJpaRepository.findAll().firstOrNull() ?: collegeJpaRepository.save(CollegeEntity(name = "信息学院"))
        val major = majorJpaRepository.findAll().firstOrNull() ?: majorJpaRepository.save(MajorEntity(name = "软件工程", college = college))

        studentUser = userJpaRepository.save(
            UserEntity(
                name = "测试学生",
                role = UserRole.STUDENT,
                email = EmailAddress("risk_stu_${System.currentTimeMillis()}@univ.edu.cn"),
                status = AccountStatus.ACTIVE
            )
        )

        studentJpaRepository.save(
            StudentEntity(
                id = studentUser.id,
                studentNumber = "STU_RISK_001",
                name = "测试学生",
                major = major,
                enrollmentDate = LocalDate.now()
            )
        )

        studentHealthProfileJpaRepository.save(
            StudentHealthProfileEntity(
                studentId = studentUser.id,
                riskStatus = RiskStatus.LOW
            )
        )

        headCouncillorUser = userJpaRepository.save(
            UserEntity(
                name = "辅导员老师",
                role = UserRole.HEAD_COUNSELLOR,
                email = EmailAddress("hc_${System.currentTimeMillis()}@univ.edu.cn"),
                status = AccountStatus.ACTIVE
            )
        )
    }

    @Test
    fun `testAcuteSuicidalIdeation_AlertsHeadCouncillor`() {
        val answers = mapOf<String, Any>(
            "ghq_23" to 1,
            "phq9_1" to 0
        )

        val result = clinicalRiskEvaluator.evaluateRisk(studentUser.id, answers)

        assertEquals(RiskStatus.HIGH, result.riskStatus)
        assertTrue(result.flags.contains(RiskFlagName.SUICIDAL_IDEATION))
        assertTrue(result.isAcuteRisk)

        // Verify profile updated in DB
        val profile = studentHealthProfileJpaRepository.findByStudentId(studentUser.id).get()
        assertEquals(RiskStatus.HIGH, profile.riskStatus)
        assertTrue(profile.riskFlags.any { it.name == RiskFlagName.SUICIDAL_IDEATION })

        // Verify notification delivered to Head Councillor
        val hcNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(headCouncillorUser.id)
        assertTrue(hcNotifications.any { it.messageCode == NotificationMessageCode.ASSESSMENT_HIGH_RISK_ALERT_HC })
    }

    @Test
    fun `testSuicideAttemptHistory`() {
        val answers = mapOf<String, Any>(
            "ghq_22" to 1,
            "ghq_23" to 0
        )

        val result = clinicalRiskEvaluator.evaluateRisk(studentUser.id, answers)

        assertEquals(RiskStatus.HIGH, result.riskStatus)
        assertTrue(result.flags.contains(RiskFlagName.SUICIDE_ATTEMPT))
        assertTrue(result.isAcuteRisk)

        val profile = studentHealthProfileJpaRepository.findByStudentId(studentUser.id).get()
        assertEquals(RiskStatus.HIGH, profile.riskStatus)
        assertTrue(profile.riskFlags.any { it.name == RiskFlagName.SUICIDE_ATTEMPT })
    }

    @Test
    fun `testSelfHarmFlag_OnlyOnOption2OrAbove`() {
        // nssi_1 = 1 means "0次" (no self harm)
        val negativeAnswers = mapOf<String, Any>("nssi_1" to 1)
        val negResult = clinicalRiskEvaluator.evaluateRisk(studentUser.id, negativeAnswers)
        assertFalse(negResult.flags.contains(RiskFlagName.SELF_HARM))

        // nssi_1 = 2 means "1~2次" (affirmative self harm)
        val positiveAnswers = mapOf<String, Any>("nssi_1" to 2)
        val posResult = clinicalRiskEvaluator.evaluateRisk(studentUser.id, positiveAnswers)
        assertTrue(posResult.flags.contains(RiskFlagName.SELF_HARM))
    }

    @Test
    fun `testHighDepressionScore_highRiskGatePlusPhq15_isTier2Positive`() {
        // 三层逻辑：第一层高危（GHQ二分=5）+ PHQ-9=18>=15 -> 第二层阳性，理由含 PHQ-9≥15。
        val answers = (1..5).associate { "ghq_$it" to 2 } + (1..9).associate { "phq9_$it" to 2 }

        val result = clinicalRiskEvaluator.evaluateRisk(studentUser.id, answers)

        assertEquals(RiskStatus.HIGH, result.riskStatus)
        assertEquals(ThreeTierScreeningEvaluator.DangerLevel.HIGH, result.dangerLevel)
        assertEquals(18, result.phq9Score)
        assertTrue(result.layer2Positive)
        assertTrue(result.screeningReasons.contains("PHQ-9≥15"))
        assertTrue(result.scidInterview)

        val profile = studentHealthProfileJpaRepository.findByStudentId(studentUser.id).get()
        assertEquals(RiskStatus.HIGH, profile.riskStatus)
    }

    @Test
    fun `testPhqAloneWithoutGhqRisk_isLowRiskNotTier2`() {
        // PHQ-9=18 但无 GHQ 危险 -> 第一层低危，不进入第二层（甲方分层法：第二层仅高危者）。
        val answers = (1..9).associate { "phq9_$it" to 2 }

        val result = clinicalRiskEvaluator.evaluateRisk(studentUser.id, answers)

        assertEquals(RiskStatus.LOW, result.riskStatus)
        assertEquals(ThreeTierScreeningEvaluator.DangerLevel.LOW, result.dangerLevel)
        assertEquals(18, result.phq9Score)
        assertFalse(result.layer2Positive)
        assertTrue(result.screeningReasons.isEmpty())
    }

    @Test
    fun `testMediumDepressionScore_mediumGhq_isMediumRisk`() {
        // GHQ二分=3（无危险因子）-> 第一层中危 -> MEDIUM，不进入第二层。
        val answers = (1..3).associate { "ghq_$it" to 2 }

        val result = clinicalRiskEvaluator.evaluateRisk(studentUser.id, answers)

        assertEquals(RiskStatus.MEDIUM, result.riskStatus)
        assertEquals(ThreeTierScreeningEvaluator.DangerLevel.MEDIUM, result.dangerLevel)
        assertEquals(3, result.ghq12BinaryTotal)
        assertFalse(result.layer2Positive)

        val profile = studentHealthProfileJpaRepository.findByStudentId(studentUser.id).get()
        assertEquals(RiskStatus.MEDIUM, profile.riskStatus)
    }

    @Test
    fun `testNormalScore_LowRisk`() {
        val answers = mapOf<String, Any>(
            "ghq_22" to 0,
            "ghq_23" to 0,
            "phq9_1" to 0,
            "phq9_2" to 0
        )

        val result = clinicalRiskEvaluator.evaluateRisk(studentUser.id, answers)

        assertEquals(RiskStatus.LOW, result.riskStatus)
        assertEquals(0, result.phq9Score)
        assertFalse(result.isAcuteRisk)

        val profile = studentHealthProfileJpaRepository.findByStudentId(studentUser.id).get()
        assertEquals(RiskStatus.LOW, profile.riskStatus)
    }
}
