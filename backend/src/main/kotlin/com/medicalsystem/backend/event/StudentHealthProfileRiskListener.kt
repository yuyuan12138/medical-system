package com.medicalsystem.backend.event

import com.medicalsystem.backend.dto.CreateReferralDto
import com.medicalsystem.backend.model.AccountStatus
import com.medicalsystem.backend.model.ReferralStatus
import com.medicalsystem.backend.model.RiskStatus
import com.medicalsystem.backend.model.UserRole
import com.medicalsystem.backend.repository.StudentHealthProfileRepository
import com.medicalsystem.backend.repository.UserRepository
import com.medicalsystem.backend.service.ReferralService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class StudentHealthProfileRiskListener(
    private val healthProfileRepository: StudentHealthProfileRepository,
    private val referralRepository: com.medicalsystem.backend.repository.ReferralRepository,
    private val userRepository: UserRepository,
    private val referralService: ReferralService
) {
    private val logger = LoggerFactory.getLogger(StudentHealthProfileRiskListener::class.java)

    companion object {
        /** Referral statuses that end the referral pipeline; an open referral blocks duplicate auto-creation. */
        private val TERMINAL_REFERRAL_STATUSES = setOf(
            ReferralStatus.REJECTED,
            ReferralStatus.CLOSED,
            ReferralStatus.ERROR
        )

        private const val AUTO_REFERRAL_TITLE = "高风险学生自动转介 (Auto referral: HIGH risk)"
    }

    @Async
    @EventListener
    fun onReferralInitiated(event: ReferralInitiatedEvent) {
        val referral = referralRepository.findById(event.referralId).orElse(null) ?: return
        logger.info("Handling ReferralInitiatedEvent for student ${event.studentId} with risk status ${referral.riskLevel}")
        updateRiskLevelIfHigher(event.studentId, referral.riskLevel.name)
    }

    /**
     * Auto-create an INITIAL referral in AWAITING_APPROVAL for the HEAD_COUNSELLOR when a student's
     * risk status becomes HIGH (acute flags such as SUICIDAL_IDEATION/SELF_HARM or an overall HIGH
     * three-tier screening result).
     *
     * Fail-open by design: any failure here must NEVER fail the assessment submission — the caller
     * (ClinicalRiskEvaluator, inside the submit transaction) only requires that we do not throw.
     */
    @EventListener
    fun onStudentRiskStatusChanged(event: StudentRiskStatusChangedEvent) {
        if (event.newStatus != RiskStatus.HIGH) return
        try {
            createAutoReferral(event)
        } catch (e: Exception) {
            logger.warn(
                "Auto-referral failed for student ${event.studentId} (submission is unaffected): ${e.message}",
                e
            )
        }
    }

    @Async
    @EventListener
    fun onStudentRegistered(event: StudentRegisteredEvent) {
        logger.info("Handling StudentRegisteredEvent for student ${event.studentId} with risk level ${event.riskLevel}")
        // Registration already creates initial profile, but if it didn't, we'd do it here.
    }

    private fun createAutoReferral(event: StudentRiskStatusChangedEvent) {
        // Idempotency: skip if an open (non-terminal) referral already exists for this student,
        // e.g. from a previous HIGH-risk event or manual initiation.
        val hasOpenReferral = referralRepository.findAll().any {
            it.studentId == event.studentId && it.status !in TERMINAL_REFERRAL_STATUSES
        }
        if (hasOpenReferral) {
            logger.info(
                "Open referral already exists for student ${event.studentId}; skipping auto-referral"
            )
            return
        }

        // Assign to one active HEAD_COUNSELLOR. If none exists, log a warning and skip (fail-open).
        val counsellor = userRepository.findAll().firstOrNull {
            it.role == UserRole.HEAD_COUNSELLOR && it.status == AccountStatus.ACTIVE
        }
        if (counsellor == null) {
            logger.warn(
                "No active HEAD_COUNSELLOR found; skipping auto-referral for high-risk student ${event.studentId}"
            )
            return
        }

        val flagsText = event.riskFlags.joinToString(", ").ifEmpty { "HIGH" }
        val dto = CreateReferralDto(
            studentId = event.studentId,
            actionType = "submit",
            title = AUTO_REFERRAL_TITLE,
            reason = "Assessment screening flagged the student as HIGH risk" +
                (event.scaleCode?.let { " (scale: $it)" } ?: "") +
                ". Risk flags: $flagsText",
            riskLevel = RiskStatus.HIGH,
            clinicalStatus = emptyList(),
            severeRiskFactors = emptyList(),
            attachments = emptyList()
        )

        // ReferralService submits the draft, which transitions it to AWAITING_APPROVAL.
        val created = referralService.initiateReferral(dto, counsellor)
        logger.info(
            "Auto-created referral ${created.id} (AWAITING_APPROVAL, assigned to HEAD_COUNSELLOR ${counsellor.id}) " +
                "for high-risk student ${event.studentId}"
        )
    }

    private fun updateRiskLevelIfHigher(studentId: Long, newRiskLevel: String) {
        val profile = healthProfileRepository.findByStudentId(studentId).orElse(null) ?: return
        try {
            val newStatus = RiskStatus.valueOf(newRiskLevel.uppercase())
            val newPriority = riskPriority(newStatus)
            val currentPriority = riskPriority(profile.riskStatus)
            if (newPriority > currentPriority) {
                val oldStatus = profile.riskStatus
                profile.riskStatus = newStatus
                healthProfileRepository.save(profile)
                logger.info(
                    "Referral risk override: raised student $studentId risk status " +
                        "$oldStatus -> $newStatus"
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse risk level $newRiskLevel", e)
        }
    }

    private fun riskPriority(status: RiskStatus): Int = when (status) {
        RiskStatus.HIGH -> 3
        RiskStatus.MEDIUM -> 2
        RiskStatus.LOW -> 1
    }
}
