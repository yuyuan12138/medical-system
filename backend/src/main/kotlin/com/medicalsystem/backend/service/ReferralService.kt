package com.medicalsystem.backend.service

import com.medicalsystem.backend.dto.CreateReferralDto
import com.medicalsystem.backend.dto.ReferralDto
import com.medicalsystem.backend.dto.ReferralTrackingDto
import com.medicalsystem.backend.model.Referral
import com.medicalsystem.backend.model.ReferralStatus
import com.medicalsystem.backend.mapper.ReferralMapper
import com.medicalsystem.backend.repository.ReferralRepository
import com.medicalsystem.backend.repository.StudentRepository
import com.medicalsystem.backend.exception.ResourceNotFoundException
import com.medicalsystem.backend.model.ReferralType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import com.medicalsystem.backend.repository.UserRepository
import com.medicalsystem.backend.repository.HospitalRepository
import com.medicalsystem.backend.repository.HospitalDepartmentRepository
import com.medicalsystem.backend.exception.ValidationException
import com.medicalsystem.backend.model.User

@Service
@Transactional(readOnly = true)
class ReferralService(
    private val referralRepository: ReferralRepository,
    private val studentRepository: StudentRepository,
    private val userRepository: UserRepository,
    private val hospitalRepository: HospitalRepository,
    private val hospitalDepartmentRepository: HospitalDepartmentRepository,
    private val doctorRepository: com.medicalsystem.backend.repository.DoctorRepository,
    private val trialAdminRepository: com.medicalsystem.backend.repository.TrialAdminRepository,
    private val referralMapper: ReferralMapper,
    private val studentHealthProfileRepository: com.medicalsystem.backend.repository.StudentHealthProfileRepository,
    private val eventPublisher: com.medicalsystem.backend.event.DomainEventPublisher
) {
    private val logger = LoggerFactory.getLogger(ReferralService::class.java)

    companion object {
        const val ACTION_DRAFT = "draft"
    }

    private fun mapToDto(model: Referral, currentUser: User? = null): ReferralDto {
        val student = studentRepository.findById(model.studentId).orElse(null)
        val referredBy = userRepository.findById(model.referredById).orElse(null)
        return referralMapper.toDto(model, student, referredBy, currentUser)
    }

    fun fetchActiveReferrals(user: User? = null): List<ReferralDto> {
        val referrals = if (user != null) {
            referralRepository.findVisibleReferralsFor(user)
        } else {
            referralRepository.findAll()
        }

        val studentIds = referrals.map { it.studentId }.toSet()
        val userIds = referrals.map { it.referredById }.toSet()

        val students = studentRepository.findAllById(studentIds).associateBy { it.id }
        val users = userRepository.findAllById(userIds).associateBy { it.id }

        return referrals.map { referral -> 
            referralMapper.toDto(referral, students[referral.studentId], users[referral.referredById], user)
        }
    }

    fun fetchReferralDetails(id: Long, user: User? = null): com.medicalsystem.backend.dto.ReferralDetailsDto {
        val model = if (user != null) {
            referralRepository.findByIdAndVisibleTo(id, user)
        } else {
            referralRepository.findById(id)
        }.orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }
        val student = studentRepository.findById(model.studentId).orElse(null)
        val referredBy = userRepository.findById(model.referredById).orElse(null)
        val healthProfile = studentHealthProfileRepository.findByStudentId(model.studentId).orElse(null)
        return referralMapper.toDetailsDto(model, student, referredBy, user, healthProfile?.scidDiagnosis)
    }

    fun fetchReferralTracking(id: Long, user: User? = null): ReferralTrackingDto {
        // TODO: Refactor to a dedicated Query Service / Projection to avoid manual stitching and constructor bloat
        val model = if (user != null) {
            referralRepository.findByIdAndVisibleTo(id, user)
        } else {
            referralRepository.findById(id)
        }.orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }
        
        var hospitalName: String? = null
        var departmentName: String? = null
        var doctorName: String? = null
        var adminName: String? = null

        model.destination?.let { dest ->
            val hospitalId = when (dest) {
                is com.medicalsystem.backend.model.ReferralDestination.Submitted -> dest.hospitalId
                is com.medicalsystem.backend.model.ReferralDestination.Triaged -> dest.hospitalId
            }
            hospitalName = hospitalRepository.findById(hospitalId.value).orElse(null)?.name
            
            if (dest is com.medicalsystem.backend.model.ReferralDestination.Triaged) {
                departmentName = hospitalDepartmentRepository.findById(dest.departmentId.value).orElse(null)?.name
                doctorName = userRepository.findById(dest.doctorId.value).orElse(null)?.name
                adminName = userRepository.findById(dest.triageAdminId.value).orElse(null)?.name
            }
        }

        return referralMapper.toTrackingDto(model, hospitalName, departmentName, doctorName, adminName)
    }

    @Transactional
    fun initiateReferral(dto: com.medicalsystem.backend.dto.CreateReferralDto, user: User): ReferralDto {
        val student = studentRepository.findById(dto.studentId)
            .orElseThrow { com.medicalsystem.backend.exception.StudentNotFoundException(dto.studentId) }
            
        val attachmentModels = dto.attachments.map { att ->
            com.medicalsystem.backend.model.ReferralAttachment(
                file = com.medicalsystem.backend.model.FileReference(
                    name = att.name,
                    sizeBytes = att.size.toLongOrNull() ?: 1024L,
                    url = java.net.URI("http://mock-storage.internal")
                ),
                fileId = att.fileId
            )
        }

        val model = com.medicalsystem.backend.model.ReferralFactory.createDraft(
            studentId = dto.studentId,
            title = dto.title,
            reason = dto.reason,
            riskLevel = dto.riskLevel,
            referredById = user.id,
            clinicalStatus = dto.clinicalStatus,
            severeRiskFactors = dto.severeRiskFactors,
            attachments = attachmentModels
        )

        if (dto.actionType != ACTION_DRAFT) {
            model.submit(user.role, user.id)
        }

        val saved = saveAndPublishEvents(model)
        
        eventPublisher.publish(
            com.medicalsystem.backend.event.ReferralInitiatedEvent(
                referralId = saved.id!!,
                studentId = saved.studentId,
                initiatorId = user.id
            )
        )
        
        return mapToDto(saved)
    }

    @Transactional
    fun approveReferral(id: Long, dto: com.medicalsystem.backend.dto.ApproveReferralDto, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }

        if (!referral.getAllowedActions(user).contains(com.medicalsystem.backend.model.ReferralAction.APPROVE_REFERRAL)) {
            throw ValidationException("User not authorized to approve referral")
        }
        
        val hasTrialAdmin = trialAdminRepository.existsByHospitalId(dto.hospitalId)
        
        if (!hasTrialAdmin) {
            throw ValidationException("Selected hospital has no assigned Trial Admin")
        }

        referral.approve(com.medicalsystem.backend.model.HospitalId(dto.hospitalId), actorId = user.id)
        return mapToDto(saveAndPublishEvents(referral))
    }

    @Transactional
    fun rejectReferral(id: Long, dto: com.medicalsystem.backend.dto.RejectReferralDto, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }

        if (!referral.getAllowedActions(user).contains(com.medicalsystem.backend.model.ReferralAction.REJECT_REFERRAL)) {
            throw ValidationException("User not authorized to reject referral")
        }

        referral.transition(ReferralStatus.REJECTED, actorId = user.id, reason = dto.reason)
        return mapToDto(saveAndPublishEvents(referral))
    }

    @Transactional
    fun recallReferral(id: Long, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }

        if (com.medicalsystem.backend.model.ReferralAction.RECALL_REFERRAL !in referral.getAllowedActions(user)) {
            throw ValidationException("User not authorized to recall referral")
        }

        referral.recall(actorId = user.id)
        return mapToDto(saveAndPublishEvents(referral))
    }

    
    @Transactional
    fun assignDoctor(id: Long, dto: com.medicalsystem.backend.dto.AssignDoctorDto, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }

        if (!referral.getAllowedActions(user).contains(com.medicalsystem.backend.model.ReferralAction.ASSIGN_DOCTOR) &&
            !referral.getAllowedActions(user).contains(com.medicalsystem.backend.model.ReferralAction.REASSIGN_DOCTOR)) {
            throw ValidationException("User not authorized to assign doctor")
        }

        val doctor = userRepository.findById(dto.doctorId)
            .orElseThrow { ResourceNotFoundException("Doctor with ID ${dto.doctorId} not found") }
        
        if (doctor.role != com.medicalsystem.backend.model.UserRole.DOCTOR) {
            throw ValidationException("Assigned user is not a doctor")
        }

        val existingHospitalId = referral.destination?.hospitalId
            ?: throw ValidationException("Hospital must be assigned before assigning a doctor")

        val doctorProfile = doctorRepository.findById(doctor.id).orElse(null)
            ?: throw ValidationException("Assigned user is not a doctor or profile is missing")
            
        val departmentIdRaw = doctorProfile.department?.id
            ?: throw ValidationException("Assigned doctor has no department")

        referral.destination = com.medicalsystem.backend.model.ReferralDestination.Triaged(
            hospitalId = existingHospitalId,
            triageAdminId = com.medicalsystem.backend.model.TriageAdminId(user.id),
            departmentId = com.medicalsystem.backend.model.HospitalDepartmentId(departmentIdRaw),
            doctorId = com.medicalsystem.backend.model.DoctorId(doctor.id),
            transferDate = referral.destination?.transferDate
        )

        referral.transition(ReferralStatus.WAITING_FOR_SCHEDULING, actorId = user.id)
        return mapToDto(saveAndPublishEvents(referral))
    }

    @Transactional
    fun requestReassignment(id: Long, dto: com.medicalsystem.backend.dto.RejectReferralDto, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }

        if (!referral.getAllowedActions(user).contains(com.medicalsystem.backend.model.ReferralAction.REQUEST_REASSIGNMENT)) {
            throw ValidationException("User not authorized to request reassignment")
        }

        referral.transition(ReferralStatus.NEEDS_REASSIGNMENT, actorId = user.id, reason = dto.reason)
        return mapToDto(saveAndPublishEvents(referral))
    }

    @Transactional
    fun scheduleAppointment(id: Long, dto: com.medicalsystem.backend.dto.ScheduleAppointmentDto, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }

        if (!referral.getAllowedActions(user).contains(com.medicalsystem.backend.model.ReferralAction.SCHEDULE_APPOINTMENT)) {
            throw ValidationException("User not authorized to schedule appointment")
        }

        if (user.role != com.medicalsystem.backend.model.UserRole.DOCTOR) {
            throw ValidationException("Only doctors can schedule appointments")
        }

        val time = dto.appointmentTime ?: throw ValidationException("Appointment time is required")

        referral.scheduleAppointment(doctorId = user.id, time = time, actorId = user.id)
        return mapToDto(saveAndPublishEvents(referral))
    }

    @Transactional
    fun acknowledgeFeedback(id: Long, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }

        if (!referral.getAllowedActions(user).contains(com.medicalsystem.backend.model.ReferralAction.ACKNOWLEDGE_FEEDBACK)) {
            throw ValidationException("User not authorized to acknowledge feedback")
        }

        referral.acknowledgeFeedback(actorId = user.id)
        
        return mapToDto(saveAndPublishEvents(referral))
    }

    @Transactional
    fun cancelReferralByAdmin(id: Long, dto: com.medicalsystem.backend.dto.RejectReferralDto?, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { ResourceNotFoundException("Referral with ID $id not found") }

        if (user.role != com.medicalsystem.backend.model.UserRole.SYSTEM_ADMIN) {
            throw com.medicalsystem.backend.exception.ForbiddenException("Only administrators can cancel referrals")
        }

        referral.adminCancel(user, dto?.reason ?: "Cancelled by administrator")
        return mapToDto(saveAndPublishEvents(referral))
    }

    private fun saveAndPublishEvents(referral: Referral): Referral {
        val events = referral.getDomainEvents()
        val saved = referralRepository.save(referral)
        events.forEach { eventPublisher.publish(it) }
        referral.clearDomainEvents()
        return saved
    }

    @Transactional
    fun forceArchiveReferral(id: Long, dto: com.medicalsystem.backend.dto.AdminArchiveReferralDto, user: User): ReferralDto {
        val referral = referralRepository.findByIdAndVisibleTo(id, user)
            .orElseThrow { com.medicalsystem.backend.exception.ResourceNotFoundException("Referral with ID $id not found") }

        if (!referral.getAllowedActions(user).contains(com.medicalsystem.backend.model.ReferralAction.FORCE_ARCHIVE)) {
            throw com.medicalsystem.backend.exception.ValidationException("User not authorized to force archive this referral")
        }

        referral.forceArchive(actorId = user.id, reason = dto.reason)
        return mapToDto(saveAndPublishEvents(referral))
    }
}
