package com.medicalsystem.backend.integration

import com.medicalsystem.backend.config.AssessmentCatalogLoader
import com.medicalsystem.backend.controller.ScaleController
import com.medicalsystem.backend.dto.SubmitScaleRequest
import com.medicalsystem.backend.entity.AssessmentAssignmentEntity
import com.medicalsystem.backend.entity.CollegeEntity
import com.medicalsystem.backend.entity.MajorEntity
import com.medicalsystem.backend.entity.StudentEntity
import com.medicalsystem.backend.entity.UserEntity
import com.medicalsystem.backend.exception.ConflictException
import com.medicalsystem.backend.model.AccountStatus
import com.medicalsystem.backend.model.AssessmentStatus
import com.medicalsystem.backend.model.EmailAddress
import com.medicalsystem.backend.model.UserRole
import com.medicalsystem.backend.model.User
import com.medicalsystem.backend.repository.AssessmentAssignmentJpaRepository
import com.medicalsystem.backend.repository.CollegeJpaRepository
import com.medicalsystem.backend.repository.MajorJpaRepository
import com.medicalsystem.backend.repository.ScaleDraftJpaRepository
import com.medicalsystem.backend.repository.StudentJpaRepository
import com.medicalsystem.backend.repository.UserJpaRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Regression tests for submit idempotency (fix wave: submit-idempotency).
 *
 * Deliberately NOT @Transactional at the test level: the concurrency test needs two threads
 * with independent DB transactions, so setup data must be committed and visible to both.
 */
@SpringBootTest
class SubmitIdempotencyIntegrationTest {

    @Autowired
    private lateinit var scaleController: ScaleController

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
    private lateinit var scaleDraftJpaRepository: ScaleDraftJpaRepository

    @Autowired
    private lateinit var catalogLoader: AssessmentCatalogLoader

    private val suffix = System.nanoTime()

    private lateinit var studentUser: UserEntity
    private lateinit var studentEntity: StudentEntity
    private val scaleCode = "comprehensive_student_intake_survey"

    private val idCard: String
        get() = "11010120040512" + String.format("%04d", suffix % 10000)

    @BeforeEach
    fun setUp() {
        val college = collegeJpaRepository.findAll().firstOrNull()
            ?: collegeJpaRepository.save(CollegeEntity(name = "并发测试学院$suffix"))
        val major = majorJpaRepository.findAll().firstOrNull()
            ?: majorJpaRepository.save(MajorEntity(name = "并发测试专业$suffix", college = college))

        studentUser = userJpaRepository.save(
            UserEntity(
                name = "并发学生$suffix",
                role = UserRole.STUDENT,
                email = EmailAddress("concurrent_submit_$suffix@univ.edu.cn"),
                status = AccountStatus.ACTIVE,
                phone = "139" + String.format("%08d", suffix % 100000000)
            )
        )
        studentEntity = studentJpaRepository.save(
            StudentEntity(
                id = studentUser.id,
                studentNumber = "STU_CONC_$suffix",
                name = "并发学生$suffix",
                major = major,
                enrollmentDate = LocalDate.of(2024, 9, 1)
            )
        )
    }

    @AfterEach
    fun cleanup() {
        // FK-safe order: children first. Leftovers (if an unexpected FK blocks a delete) are
        // unique per run (nanoTime suffix), so they cannot collide with other tests.
        runCatching { scaleDraftJpaRepository.deleteByStudentIdAndScaleCode(studentEntity.id, scaleCode) }
        runCatching {
            assessmentAssignmentJpaRepository.deleteAll(
                assessmentAssignmentJpaRepository.findByStudentId(studentEntity.id)
            )
        }
        runCatching { studentJpaRepository.delete(studentEntity) }
        runCatching { userJpaRepository.delete(studentUser) }
    }

    private fun buildUserModel(): User = User(
        id = studentUser.id,
        name = studentUser.name,
        email = studentUser.email,
        role = studentUser.role,
        status = studentUser.status,
        phone = studentUser.phone
    )

    private fun buildCompleteAnswers(): Map<String, Any> {
        val allQuestions = catalogLoader.getScaleDetail(scaleCode)?.questions ?: emptyList()
        val answers = mutableMapOf<String, Any>()
        for (q in allQuestions) {
            answers[q.code] = when (q.type) {
                "text" -> "并发测试内容"
                "date" -> "2004-05-12"
                "number" -> 175.0
                "slider" -> 5.0
                else -> 0
            }
        }
        answers["demo_id_card"] = idCard
        answers["G1"] = 1
        answers["G6"] = 1
        return answers
    }

    @Test
    fun `scale submit on completed assignment is rejected as conflict`() {
        assessmentAssignmentJpaRepository.save(
            AssessmentAssignmentEntity(
                student = studentEntity,
                assignedByUser = studentUser,
                batteryCode = scaleCode,
                status = AssessmentStatus.COMPLETED,
                assignedAt = LocalDateTime.now(),
                completedAt = LocalDateTime.now(),
                answersJson = """{"G1":1}"""
            )
        )

        val ex = assertThrows<ConflictException> {
            scaleController.submitScale(
                scaleCode,
                SubmitScaleRequest(answers = mapOf<String, Any>("G1" to 2)),
                buildUserModel()
            )
        }
        // Same conflict semantics as POST /api/assessments/{id}/submit
        assertEquals("问卷测评已完成，不可重复提交", ex.message)
    }

    @Test
    fun `concurrent double submit yields exactly one 200 and one 409`() {
        val assignment = assessmentAssignmentJpaRepository.save(
            AssessmentAssignmentEntity(
                student = studentEntity,
                assignedByUser = studentUser,
                batteryCode = scaleCode,
                status = AssessmentStatus.PENDING,
                assignedAt = LocalDateTime.now()
            )
        )
        assertTrue(assignment.id != null)

        val answers = buildCompleteAnswers()
        val userModel = buildUserModel()
        val startGate = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<Pair<Int, String?>>())
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                pool.submit(
                    Callable {
                        startGate.await()
                        try {
                            val response = scaleController.submitScale(
                                scaleCode,
                                SubmitScaleRequest(answers = answers),
                                userModel
                            )
                            results.add(Pair(response.statusCode.value(), null))
                        } catch (e: ConflictException) {
                            results.add(Pair(409, e.message))
                        }
                    }
                )
            }
            // release both threads as simultaneously as possible
            startGate.countDown()
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(2, results.size)
        assertEquals(1, results.count { it.first == 200 }, "exactly one submit should win")
        assertEquals(1, results.count { it.first == 409 }, "exactly one submit should get a conflict")
        assertEquals(
            "问卷测评已完成，不可重复提交",
            results.first { it.first == 409 }.second
        )

        // exactly one COMPLETED row for (student, battery)
        val completedRows = assessmentAssignmentJpaRepository.findByStudentId(studentEntity.id)
            .filter { it.batteryCode == scaleCode && it.status == AssessmentStatus.COMPLETED }
        assertEquals(1, completedRows.size)
    }
}
