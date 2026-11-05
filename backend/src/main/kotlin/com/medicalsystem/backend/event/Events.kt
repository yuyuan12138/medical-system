package com.medicalsystem.backend.event

import java.time.LocalDateTime
import com.medicalsystem.backend.model.ReferralStatus

import com.medicalsystem.backend.model.RiskStatus

data class ReferralInitiatedEvent(
    val referralId: Long,
    val studentId: Long,
    val initiatorId: Long,
    override val occurredOn: LocalDateTime = LocalDateTime.now()
) : DomainEvent

data class StudentRiskStatusChangedEvent(
    val studentId: Long,
    val oldStatus: RiskStatus,
    val newStatus: RiskStatus,
    val riskFlags: List<String> = emptyList(),
    val scaleCode: String? = null,
    override val occurredOn: LocalDateTime = LocalDateTime.now()
) : DomainEvent

data class ReferralStatusChangedEvent(
    val referralId: Long,
    val oldStatus: ReferralStatus,
    val newStatus: ReferralStatus,
    val studentId: Long,
    override val occurredOn: LocalDateTime = LocalDateTime.now()
) : DomainEvent

data class StudentRegisteredEvent(
    val studentId: Long,
    val riskLevel: String?,
    override val occurredOn: LocalDateTime = LocalDateTime.now()
) : DomainEvent

data class AppointmentScheduledEvent(
    val referralId: Long,
    val studentId: Long,
    val doctorId: Long,
    val appointmentTime: LocalDateTime,
    override val occurredOn: LocalDateTime = LocalDateTime.now()
) : DomainEvent

data class ReferralRecalledEvent(
    val referralId: Long,
    val studentId: Long,
    override val occurredOn: LocalDateTime = LocalDateTime.now()
) : DomainEvent

data class StaffRegisteredEvent(
    val userId: Long,
    val role: com.medicalsystem.backend.model.UserRole,
    val name: String,
    val email: com.medicalsystem.backend.model.EmailAddress,
    val employeeNumber: String,
    val status: com.medicalsystem.backend.model.AccountStatus = com.medicalsystem.backend.model.AccountStatus.PENDING_APPROVAL,
    override val occurredOn: LocalDateTime = LocalDateTime.now()
) : DomainEvent

data class OtpChallengeIssuedEvent(
    val email: com.medicalsystem.backend.model.EmailAddress,
    val code: com.medicalsystem.backend.model.OtpCode,
    val expiresInMinutes: Long = 5,
    override val occurredOn: LocalDateTime = LocalDateTime.now()
) : DomainEvent


