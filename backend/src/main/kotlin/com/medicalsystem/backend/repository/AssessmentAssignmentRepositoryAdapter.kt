package com.medicalsystem.backend.repository

import com.medicalsystem.backend.mapper.AssessmentAssignmentMapper
import com.medicalsystem.backend.model.AssessmentAssignment
import com.medicalsystem.backend.model.AssessmentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
class AssessmentAssignmentRepositoryAdapter(
    private val jpaRepository: AssessmentAssignmentJpaRepository,
    private val mapper: AssessmentAssignmentMapper
) : AssessmentAssignmentRepository {

    override fun findByStudentIdOrderByAssignedAtDesc(studentId: Long, pageable: Pageable): Page<AssessmentAssignment> {
        return jpaRepository.findByStudentIdOrderByAssignedAtDesc(studentId, pageable).map { mapper.toModel(it) }
    }

    override fun findById(id: Long): Optional<AssessmentAssignment> {
        return jpaRepository.findById(id).map { mapper.toModel(it) }
    }

    override fun findByStudentId(studentId: Long): List<AssessmentAssignment> {
        return jpaRepository.findByStudentId(studentId).map { mapper.toModel(it) }
    }

    override fun findByStudentIdAndStatus(studentId: Long, status: AssessmentStatus): List<AssessmentAssignment> {
        return jpaRepository.findByStudentIdAndStatus(studentId, status).map { mapper.toModel(it) }
    }

    override fun findByAssignedByUserId(userId: Long): List<AssessmentAssignment> {
        return jpaRepository.findByAssignedByUserId(userId).map { mapper.toModel(it) }
    }

    override fun findPendingByStudentIdAndBatteryCode(
        studentId: Long,
        batteryCode: String
    ): Optional<AssessmentAssignment> {
        return jpaRepository.findByStudentIdAndBatteryCodeAndStatus(studentId, batteryCode, AssessmentStatus.PENDING)
            .map { mapper.toModel(it) }
    }

    override fun save(assignment: AssessmentAssignment): AssessmentAssignment {
        // saveAndFlush so optimistic-lock (version) conflicts surface here instead of at commit
        // time, and so the returned model carries the bumped version for chained saves.
        val entity = mapper.toEntity(assignment)
        val savedEntity = jpaRepository.saveAndFlush(entity)
        return mapper.toModel(savedEntity)
    }

    override fun saveAll(assignments: List<AssessmentAssignment>): List<AssessmentAssignment> {
        val entities = assignments.map { mapper.toEntity(it) }
        val savedEntities = jpaRepository.saveAll(entities)
        return savedEntities.map { mapper.toModel(it) }
    }

    override fun findAll(): List<AssessmentAssignment> {
        return jpaRepository.findAll().map { mapper.toModel(it) }
    }
    override fun findPendingByStudentIdInAndBatteryCode(
        studentIds: List<Long>,
        batteryCode: String
    ): List<AssessmentAssignment> {
        return jpaRepository.findByStudentIdInAndBatteryCodeAndStatus(studentIds, batteryCode, AssessmentStatus.PENDING)
            .map { mapper.toModel(it) }
    }
}
