package com.medicalsystem.backend.event

import com.medicalsystem.backend.model.AccountStatus
import com.medicalsystem.backend.model.Referral
import com.medicalsystem.backend.model.ReferralStatus
import com.medicalsystem.backend.model.RiskStatus
import com.medicalsystem.backend.model.User
import com.medicalsystem.backend.model.UserRole
import com.medicalsystem.backend.repository.ReferralRepository
import com.medicalsystem.backend.repository.StudentHealthProfileRepository
import com.medicalsystem.backend.repository.UserRepository
import com.medicalsystem.backend.dto.CreateReferralDto
import com.medicalsystem.backend.dto.ReferralDto
import com.medicalsystem.backend.dto.ReferredByDto
import com.medicalsystem.backend.service.ReferralService
import com.medicalsystem.backend.model.EmailAddress
import com.medicalsystem.backend.model.ReferralType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentHealthProfileRiskListenerTest {

    @Mock
    private lateinit var healthProfileRepository: StudentHealthProfileRepository

    @Mock
    private lateinit var referralRepository: ReferralRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var referralService: ReferralService

    @InjectMocks
    private lateinit var listener: StudentHealthProfileRiskListener

    private val studentId = 81L
    private val counsellor = User(
        id = 16L,
        name = "Head Counsellor",
        email = EmailAddress("hc@univ.edu.cn"),
        role = UserRole.HEAD_COUNSELLOR,
        status = AccountStatus.ACTIVE
    )

    private fun highEvent() = StudentRiskStatusChangedEvent(
        studentId = studentId,
        oldStatus = RiskStatus.LOW,
        newStatus = RiskStatus.HIGH,
        riskFlags = listOf("SUICIDAL_IDEATION"),
        scaleCode = "PHQ_9,GAD_7"
    )

    @BeforeEach
    fun setUp() {
        whenever(userRepository.findAll()).thenReturn(listOf(counsellor))
        whenever(referralRepository.findAll()).thenReturn(emptyList())
        val created = ReferralDto(
            id = "501",
            studentId = studentId,
            studentName = "测试学生",
            studentNumber = "20240001",
            type = ReferralType.INITIAL,
            date = LocalDateTime.now(),
            title = "高风险学生自动转介",
            description = "Assessment screening flagged the student as HIGH risk",
            riskLevel = RiskStatus.HIGH,
            status = ReferralStatus.AWAITING_APPROVAL,
            referredBy = ReferredByDto("Head Counsellor")
        )
        whenever(referralService.initiateReferral(any(), any())).thenReturn(created)
    }

    @Test
    fun `high risk event creates exactly one awaiting-approval referral`() {
        listener.onStudentRiskStatusChanged(highEvent())

        val captor = argumentCaptor<CreateReferralDto>()
        verify(referralService, times(1)).initiateReferral(captor.capture(), any())
        val dto = captor.firstValue
        assertEquals(studentId, dto.studentId)
        assertEquals(RiskStatus.HIGH, dto.riskLevel)
        assertEquals("submit", dto.actionType)
        assertTrue(dto.clinicalStatus.isEmpty())
        assertTrue(dto.severeRiskFactors.isEmpty())
        assertTrue(dto.attachments.isEmpty())
        assertTrue(dto.title.isNotBlank())
    }

    @Test
    fun `second high risk event does not create a duplicate referral`() {
        listener.onStudentRiskStatusChanged(highEvent())

        // Now an open (non-terminal) referral exists from the previous HIGH event.
        whenever(
            referralRepository.findAll()
        ).thenReturn(listOf(openReferral()))

        listener.onStudentRiskStatusChanged(highEvent())

        verify(referralService, times(1)).initiateReferral(any(), any())
    }

    @Test
    fun `no active head counsellor skips referral creation without failing`() {
        whenever(userRepository.findAll()).thenReturn(
            listOf(
                counsellor.copy(status = AccountStatus.PENDING_APPROVAL),
                counsellor.copy(id = 17L, role = UserRole.TEACHER)
            )
        )

        assertDoesNotThrow { listener.onStudentRiskStatusChanged(highEvent()) }

        verify(referralService, never()).initiateReferral(any(), any())
    }

    @Test
    fun `referral service failure never propagates to the submission path`() {
        whenever(referralService.initiateReferral(any(), any()))
            .thenThrow(RuntimeException("referral backend down"))

        assertDoesNotThrow { listener.onStudentRiskStatusChanged(highEvent()) }
    }

    @Test
    fun `non-high risk events are ignored`() {
        listener.onStudentRiskStatusChanged(
            StudentRiskStatusChangedEvent(
                studentId = studentId,
                oldStatus = RiskStatus.LOW,
                newStatus = RiskStatus.MEDIUM
            )
        )

        verify(referralService, never()).initiateReferral(any(), any())
    }

    private fun openReferral(): Referral =
        ReferralFactoryForTest.open(studentId)

    /** Minimal open referral built through the domain factory, submitted to AWAITING_APPROVAL. */
    private object ReferralFactoryForTest {
        fun open(studentId: Long): Referral {
            val referral = com.medicalsystem.backend.model.ReferralFactory.createDraft(
                studentId = studentId,
                title = "Prior auto referral",
                reason = "Previous HIGH risk event",
                riskLevel = RiskStatus.HIGH,
                referredById = 16L
            )
            referral.submit(UserRole.HEAD_COUNSELLOR, actorId = 16L)
            return referral
        }
    }
}
