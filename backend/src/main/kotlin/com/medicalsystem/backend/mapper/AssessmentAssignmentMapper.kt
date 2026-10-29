package com.medicalsystem.backend.mapper

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.medicalsystem.backend.entity.AssessmentAssignmentEntity
import com.medicalsystem.backend.entity.StudentEntity
import com.medicalsystem.backend.entity.UserEntity
import com.medicalsystem.backend.model.AssessmentAssignment
import com.medicalsystem.backend.model.BatteryId
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class AssessmentAssignmentMapper(
    private val entityManager: EntityManager
) {
    private val objectMapper = ObjectMapper()
    private val mapTypeRef = object : TypeReference<Map<String, Any>>() {}

    fun toModel(entity: AssessmentAssignmentEntity): AssessmentAssignment {
        val parsedAnswers: Map<String, Any>? = entity.answersJson?.let {
            try {
                objectMapper.readValue(it, mapTypeRef)
            } catch (e: Exception) {
                null
            }
        }

        return AssessmentAssignment(
            id = entity.id,
            studentId = entity.student.id,
            assignedByUserId = entity.assignedByUser.id,
            batteryCode = BatteryId(entity.batteryCode),
            dispatchId = entity.dispatch?.id,
            status = entity.status,
            assignedAt = entity.assignedAt,
            completedAt = entity.completedAt,
            dueDate = entity.dueDate,
            answers = parsedAnswers,
            revokedAt = entity.revokedAt,
            severity = entity.severity
        )
    }

    fun toEntity(model: AssessmentAssignment): AssessmentAssignmentEntity {
        val studentRef = entityManager.getReference(StudentEntity::class.java, model.studentId)
        val assignerRef = entityManager.getReference(UserEntity::class.java, model.assignedByUserId)
        val dispatchRef = model.dispatchId?.let { entityManager.getReference(com.medicalsystem.backend.entity.AssessmentDispatchEntity::class.java, it) }

        val answersStr = model.answers?.let {
            objectMapper.writeValueAsString(it)
        }

        val entity = AssessmentAssignmentEntity(
            id = model.id,
            student = studentRef,
            assignedByUser = assignerRef,
            batteryCode = model.batteryCode.value,
            status = model.status,
            assignedAt = model.assignedAt,
            completedAt = model.completedAt,
            dueDate = model.dueDate,
            answersJson = answersStr,
            revokedAt = model.revokedAt,
            dispatch = dispatchRef,
            severity = model.severity
        )
        
        return entity
    }
}
