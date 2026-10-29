package com.medicalsystem.backend.mapper

import com.medicalsystem.backend.dto.ReferredByDto
import com.medicalsystem.backend.dto.ReferralDto
import com.medicalsystem.backend.entity.AttachmentEntity
import com.medicalsystem.backend.entity.ReferralEntity
import com.medicalsystem.backend.entity.ReferralStepEntity
import com.medicalsystem.backend.model.ReferralAttachment
import com.medicalsystem.backend.model.Referral
import com.medicalsystem.backend.model.ReferralStep
import com.medicalsystem.backend.model.Appointment
import org.springframework.stereotype.Component

@Component
class ReferralMapper {

    fun toModel(entity: ReferralEntity): Referral {
        return Referral(
            id = entity.id,
            studentId = entity.studentId,
            type = entity.type,
            date = entity.createdAt,
            title = entity.title,
            description = entity.description,
            riskLevel = entity.riskLevel,
            status = entity.status,
            referredById = entity.referredById,
            clinicalStatus = entity.clinicalStatus.toMutableList(),
            severeRiskFactors = entity.severeRiskFactors.toMutableList(),
            destination = entity.destination?.let { dest ->
                if (dest.hospital != null) {
                    if (dest.triageAdmin != null && dest.department != null && dest.doctor != null) {
                        com.medicalsystem.backend.model.ReferralDestination.Triaged(
                            hospitalId = com.medicalsystem.backend.model.HospitalId(dest.hospital!!.id),
                            triageAdminId = com.medicalsystem.backend.model.TriageAdminId(dest.triageAdmin!!.userId),
                            departmentId = com.medicalsystem.backend.model.HospitalDepartmentId(dest.department!!.id),
                            doctorId = com.medicalsystem.backend.model.DoctorId(dest.doctor!!.userId),
                            transferDate = dest.transferDate
                        )
                    } else {
                        com.medicalsystem.backend.model.ReferralDestination.Submitted(
                            hospitalId = com.medicalsystem.backend.model.HospitalId(dest.hospital!!.id),
                            transferDate = dest.transferDate
                        )
                    }
                } else null
            },
            appointment = entity.appointment?.let { app ->
                Appointment(
                    id = app.id,
                    doctorId = app.doctorId,
                    appointmentTime = app.appointmentTime,
                    status = app.status
                )
            },
            attachments = entity.attachments.map {
                ReferralAttachment(
                    id = it.id,
                    file = com.medicalsystem.backend.model.FileReference(
                        name = it.name,
                        sizeBytes = it.size.toLongOrNull() ?: 0L,
                        url = java.net.URI("http://mock-url.com")
                    ),
                    fileId = it.fileId
                )
            }.toMutableList(),
            steps = entity.steps.map {
                ReferralStep(
                    id = it.id,
                    type = it.type,
                    time = it.time,
                    status = it.status,
                    actorId = it.actorId,
                    reason = it.reason
                )
            }.toMutableList(),
            feedback = entity.feedback?.let {
                com.medicalsystem.backend.model.ReferralFeedback(
                    id = it.id ?: 0L,
                    referralId = it.referral?.id ?: 0L,
                    content = it.content,
                    attachments = it.attachments.map { att ->
                        com.medicalsystem.backend.model.FeedbackAttachment(
                            file = com.medicalsystem.backend.model.FileReference(
                                name = att.name,
                                sizeBytes = att.sizeBytes,
                                url = java.net.URI(att.fileUrl ?: "http://mock-url.com")
                            ),
                            fileId = att.fileId
                        )
                    },
                    createdAt = it.createdAt
                )
            }
        )
    }

    fun toEntity(model: Referral): ReferralEntity {
        val entity = ReferralEntity(
            id = model.id,
            studentId = model.studentId,
            type = model.type,
            title = model.title,
            description = model.description,
            riskLevel = model.riskLevel,
            status = model.status,
            referredById = model.referredById,
            createdAt = model.date,
            clinicalStatus = model.clinicalStatus.toMutableList(),
            severeRiskFactors = model.severeRiskFactors.toMutableList()
        )

        entity.appointment = model.appointment?.let { app ->
            com.medicalsystem.backend.entity.AppointmentEntity(
                id = app.id,
                referral = entity,
                doctorId = app.doctorId,
                appointmentTime = app.appointmentTime,
                status = app.status
            )
        }

        entity.attachments = model.attachments.map {
            AttachmentEntity(
                id = it.id,
                name = it.file.name,
                size = it.file.sizeBytes.toString(),
                fileId = it.fileId,
                referral = entity
            )
        }.toMutableSet()

        entity.steps = model.steps.map {
            ReferralStepEntity(
                id = it.id ?: 0,
                referral = entity,
                type = it.type,
                time = it.time,
                status = it.status,
                actorId = it.actorId,
                reason = it.reason
            )
        }.toMutableSet()

        entity.feedback = model.feedback?.let {
            com.medicalsystem.backend.entity.ReferralFeedbackEntity(
                id = if (it.id == 0L) null else it.id,
                referral = entity,
                content = it.content,
                attachments = it.attachments.map { att ->
                    com.medicalsystem.backend.entity.FeedbackAttachmentEntity(
                        name = att.file.name,
                        sizeBytes = att.file.sizeBytes,
                        fileId = att.fileId
                    )
                }.toMutableList(),
                createdAt = it.createdAt
            )
        }

        return entity
    }

    fun toDto(model: Referral, student: com.medicalsystem.backend.model.Student?, referredBy: com.medicalsystem.backend.model.User?, currentUser: com.medicalsystem.backend.model.User? = null): ReferralDto {
        return ReferralDto(
            id = model.id.toString(),
            studentId = model.studentId,
            studentName = student?.name ?: "Unknown",
            studentNumber = student?.studentNumber ?: "Unknown",
            type = model.type,
            date = model.date,
            title = model.title,
            description = model.description,
            riskLevel = model.riskLevel,
            status = model.status,
            referredBy = ReferredByDto(referredBy?.name ?: "Unknown"),
            availableActions = currentUser?.let { model.getAllowedActions(it).map { action -> action.name.lowercase() } } ?: emptyList()
        )
    }

    fun toDetailsDto(model: Referral, student: com.medicalsystem.backend.model.Student?, referredBy: com.medicalsystem.backend.model.User?, currentUser: com.medicalsystem.backend.model.User? = null, scidDiagnosis: String? = null): com.medicalsystem.backend.dto.ReferralDetailsDto {
        val baseInfo = toDto(model, student, referredBy, currentUser)
        
        val studentDemographics = com.medicalsystem.backend.dto.StudentDemographicsDto(
            studentId = student?.studentNumber ?: "Unknown",
            school = "未知学院",
            grade = "未知年级",
            phone = "未知电话",
            age = 20, // Default mock value since backend doesn't store this yet
            gender = "未知"
        )

        val triageInfo = com.medicalsystem.backend.dto.TriageInfoDto(
            isFirstVisit = model.clinicalStatus.contains(com.medicalsystem.backend.model.ClinicalStatusType.FIRST_VISIT),
            isMedicated = model.clinicalStatus.contains(com.medicalsystem.backend.model.ClinicalStatusType.MEDICATED),
            priorTherapy = if (model.clinicalStatus.contains(com.medicalsystem.backend.model.ClinicalStatusType.PRIOR_THERAPY)) "有" else "无",
            scidDiagnosis = scidDiagnosis,
            fullDescription = model.description
        )

        val riskAssessment = com.medicalsystem.backend.dto.RiskAssessmentDto(
            ideation = model.severeRiskFactors.contains(com.medicalsystem.backend.model.RiskFlagName.SUICIDAL_IDEATION),
            attempt = model.severeRiskFactors.contains(com.medicalsystem.backend.model.RiskFlagName.SUICIDE_ATTEMPT),
            selfHarm = model.severeRiskFactors.contains(com.medicalsystem.backend.model.RiskFlagName.SELF_HARM),
            notes = null
        )

        val feedbackDto = model.feedback?.let {
            com.medicalsystem.backend.dto.FeedbackDto(
                summary = it.content,
                followUp = "",
                attachments = it.attachments.map { att ->
                    com.medicalsystem.backend.dto.AttachmentDto(
                        name = att.file.name,
                        size = att.file.sizeBytes.toString(),
                        fileId = att.fileId
                    )
                }
            )
        }

        val referralAttachments = model.attachments.map { att ->
            com.medicalsystem.backend.dto.AttachmentDto(
                name = att.file.name,
                size = att.file.sizeBytes.toString(),
                fileId = att.fileId
            )
        }

        return com.medicalsystem.backend.dto.ReferralDetailsDto(
            baseInfo = baseInfo,
            studentDemographics = studentDemographics,
            triageInfo = triageInfo,
            riskAssessment = riskAssessment,
            feedback = feedbackDto,
            attachments = referralAttachments
        )
    }

    fun toTrackingDto(model: Referral, hospitalName: String? = null, departmentName: String? = null, doctorName: String? = null, adminName: String? = null): com.medicalsystem.backend.dto.ReferralTrackingDto {
        val dest = model.destination?.let { dest ->
            com.medicalsystem.backend.dto.DestinationDto(
                hospital = hospitalName ?: "",
                department = departmentName ?: "",
                doctor = doctorName ?: "",
                admin = adminName ?: "",
                transferDate = dest.transferDate?.toString(),
                appointmentTime = model.appointment?.appointmentTime?.toString()
            )
        }

        val standardSequence = listOf(
            com.medicalsystem.backend.model.ReferralStepType.INITIATION,
            com.medicalsystem.backend.model.ReferralStepType.REVIEW,
            com.medicalsystem.backend.model.ReferralStepType.TRIAGE,
            com.medicalsystem.backend.model.ReferralStepType.SCHEDULING,
            com.medicalsystem.backend.model.ReferralStepType.EVALUATION,
            com.medicalsystem.backend.model.ReferralStepType.FEEDBACK
        )

        val typeIndices = standardSequence.mapIndexed { index, type -> type to index }.toMap()

        val sortedModelSteps = model.steps.sortedWith(
            compareBy<com.medicalsystem.backend.model.ReferralStep> { typeIndices[it.type] ?: 999 }
                .thenBy { it.time }
        )

        val mappedSteps = sortedModelSteps.map { step ->
            com.medicalsystem.backend.dto.ReferralStepDto(
                id = step.id.toString(),
                type = step.type.name,
                time = step.time.toString(),
                status = step.status.name,
                reason = step.reason
            )
        }.toMutableList()

        if (model.status != com.medicalsystem.backend.model.ReferralStatus.REJECTED && 
            model.status != com.medicalsystem.backend.model.ReferralStatus.RECALLED && 
            model.status != com.medicalsystem.backend.model.ReferralStatus.CLOSED) {

            val lastActualStepType = sortedModelSteps.lastOrNull()?.type
            val nextIndex = if (lastActualStepType != null) {
                standardSequence.indexOf(lastActualStepType) + 1
            } else 0

            if (nextIndex in 1 until standardSequence.size) {
                for (i in nextIndex until standardSequence.size) {
                    mappedSteps.add(
                        com.medicalsystem.backend.dto.ReferralStepDto(
                            id = "pending_$i",
                            type = standardSequence[i].name,
                            time = "",
                            status = "PENDING"
                        )
                    )
                }
            }
        }

        val steps = mappedSteps.ifEmpty { null }

        return com.medicalsystem.backend.dto.ReferralTrackingDto(
            destination = dest,
            steps = steps
        )
    }
}
