package com.medicalsystem.backend.service

import com.medicalsystem.backend.entity.RiskFlagEntity
import com.medicalsystem.backend.model.*
import com.medicalsystem.backend.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ClinicalRiskAssessmentResult(
    val riskStatus: RiskStatus,
    val flags: Set<RiskFlagName>,
    val phq9Score: Int,
    val gad7Score: Int,
    val isAcuteRisk: Boolean,
    // 甲方三层筛查判别结果（增量）
    val dangerLevel: ThreeTierScreeningEvaluator.DangerLevel,
    val ghq12BinaryTotal: Int,
    val otherRiskFactorCount: Int,
    val layer2Positive: Boolean,
    val screeningReasons: List<String>,
    val screeningReasonsJoined: String,
    val scidInterview: Boolean,
    val adhd: ThreeTierScreeningEvaluator.AdhdScreeningResult
)

@Service
class ClinicalRiskEvaluator(
    private val studentHealthProfileJpaRepository: StudentHealthProfileJpaRepository,
    private val studentJpaRepository: StudentJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val notificationService: NotificationService,
    private val eventPublisher: com.medicalsystem.backend.event.DomainEventPublisher
) {
    private val logger = LoggerFactory.getLogger(ClinicalRiskEvaluator::class.java)
    private val threeTierScreeningEvaluator = ThreeTierScreeningEvaluator()

    @Transactional
    fun evaluateRisk(
        studentId: Long,
        answers: Map<String, Any>,
        completedTests: List<PsychometricTest> = emptyList()
    ): ClinicalRiskAssessmentResult {
        val flags = mutableSetOf<RiskFlagName>()

        val ghq22 = answers["ghq_22"]?.toString()?.toIntOrNull() ?: -1
        val ghq23 = answers["ghq_23"]?.toString()?.toIntOrNull() ?: -1
        val phq99 = answers["phq9_9"]?.toString()?.toIntOrNull() ?: -1

        var isAcute = false

        // ghq_23: Suicidal ideation ("过去任何时候您是否想过自杀或故意伤害自己")，甲方编码"是"=0
        // phq9_9 >= 1: Thoughts of hurting oneself
        if (ghq23 == 0 || phq99 >= 1) {
            flags.add(RiskFlagName.SUICIDAL_IDEATION)
            isAcute = true
        }

        // ghq_22: Suicide attempt history ("过去任何时候您是否有过喝药、割腕等自伤或自杀行为")，甲方编码"是"=0
        if (ghq22 == 0) {
            flags.add(RiskFlagName.SUICIDE_ATTEMPT)
            isAcute = true
        }

        // NSSI 甲方文档编码: 0 = "0次", 1 = "1~2次", 2 = "3~5次", 3 = "6次及以上"；编码>=1 即为有自伤行为
        val hasSelfHarm = (1..5).any { i ->
            (answers["nssi_$i"]?.toString()?.toIntOrNull() ?: 0) >= 1
        }
        if (hasSelfHarm) {
            flags.add(RiskFlagName.SELF_HARM)
        }

        val phq9Score = threeTierScreeningEvaluator.phq9Total(answers)
        val gad7Score = threeTierScreeningEvaluator.gad7Total(answers)

        // 甲方三层筛查判别：第一层危险分级 + 第二层临床筛选 + 第三层筛选理由 + 独立 ADHD 名单。
        val student = studentJpaRepository.findById(studentId).orElse(null)
        val screening = threeTierScreeningEvaluator.screen(answers, student?.studentNumber ?: "")

        // 危险分级（第一层）决定风险状态：高危/中危/低危 -> HIGH/MEDIUM/LOW。
        // 第二层仅对高危者筛选，阳性者仍为 HIGH，故 riskStatus 直接取第一层等级。
        val evaluatedRisk: RiskStatus = when (screening.dangerLevel) {
            ThreeTierScreeningEvaluator.DangerLevel.HIGH -> RiskStatus.HIGH
            ThreeTierScreeningEvaluator.DangerLevel.MEDIUM -> RiskStatus.MEDIUM
            ThreeTierScreeningEvaluator.DangerLevel.LOW -> RiskStatus.LOW
        }

        val profileOpt = studentHealthProfileJpaRepository.findByStudentId(studentId)
        if (profileOpt.isPresent) {
            val profile = profileOpt.get()
            val currentLevel = riskPriority(profile.riskStatus)
            val evaluatedLevel = riskPriority(evaluatedRisk)
            if (evaluatedLevel > currentLevel) {
                profile.riskStatus = evaluatedRisk
            }

            val existingFlagNames = profile.riskFlags.map { it.name }.toSet()
            for (flagName in flags) {
                if (!existingFlagNames.contains(flagName)) {
                    profile.riskFlags.add(
                        RiskFlagEntity(
                            name = flagName,
                            status = FlagStatus.POSITIVE,
                            healthProfile = profile
                        )
                    )
                }
            }
            studentHealthProfileJpaRepository.save(profile)
        }

        if (evaluatedRisk == RiskStatus.HIGH) {
            val studentName = student?.name ?: "学生"
            val scaleCode = completedTests.map { it.testType.name }.distinct().joinToString(",")
            val riskFlagNames = flags.map { it.name }
            val headCouncillors = userJpaRepository.findAll().filter { it.role == UserRole.HEAD_COUNSELLOR }
            for (hc in headCouncillors) {
                try {
                    notificationService.createNotification(
                        userId = hc.id,
                        messageCode = NotificationMessageCode.ASSESSMENT_HIGH_RISK_ALERT_HC,
                        payload = mapOf(
                            "studentId" to studentId.toString(),
                            "studentNumber" to (student?.studentNumber ?: ""),
                            "studentName" to studentName,
                            "riskStatus" to evaluatedRisk.name,
                            "riskFlags" to riskFlagNames,
                            "crisisFlags" to flags.joinToString { it.name },
                            "level" to "HIGH",
                            "scaleCode" to scaleCode,
                            "screeningReasons" to screening.screeningReasonsJoined
                        ),
                        actionType = NotificationActionType.VIEW_RECORDS,
                        actionTargetId = studentId
                    )
                } catch (e: Exception) {
                    logger.error("Failed to notify Head Councillor ${hc.id}: ${e.message}")
                }
            }

            // Notify the risk pipeline: a HIGH-risk event must auto-create a referral for the
            // HEAD_COUNSELLOR. The listener is fail-open — it must never break the submission.
            try {
                eventPublisher.publish(
                    com.medicalsystem.backend.event.StudentRiskStatusChangedEvent(
                        studentId = studentId,
                        oldStatus = profileOpt.orElse(null)?.riskStatus ?: RiskStatus.LOW,
                        newStatus = evaluatedRisk,
                        riskFlags = riskFlagNames,
                        scaleCode = scaleCode.ifEmpty { null }
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to publish StudentRiskStatusChangedEvent for student $studentId: ${e.message}")
            }
        }

        return ClinicalRiskAssessmentResult(
            riskStatus = evaluatedRisk,
            flags = flags,
            phq9Score = phq9Score,
            gad7Score = gad7Score,
            isAcuteRisk = isAcute,
            dangerLevel = screening.dangerLevel,
            ghq12BinaryTotal = screening.ghq12BinaryTotal,
            otherRiskFactorCount = screening.otherRiskFactorCount,
            layer2Positive = screening.layer2Positive,
            screeningReasons = screening.screeningReasons,
            screeningReasonsJoined = screening.screeningReasonsJoined,
            scidInterview = screening.scidInterview,
            adhd = screening.adhd
        )
    }

    private fun riskPriority(status: RiskStatus): Int = when (status) {
        RiskStatus.HIGH -> 3
        RiskStatus.MEDIUM -> 2
        RiskStatus.LOW -> 1
    }
}