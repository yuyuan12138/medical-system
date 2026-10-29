package com.medicalsystem.backend.repository

import com.medicalsystem.backend.entity.StudentHealthProfileEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface StudentHealthProfileJpaRepository : JpaRepository<StudentHealthProfileEntity, Long> {
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["psychometricTests"])
    fun findByStudentId(studentId: Long): Optional<StudentHealthProfileEntity>

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["riskFlags"])
    fun findByRiskStatusIn(statuses: Collection<com.medicalsystem.backend.model.RiskStatus>): List<StudentHealthProfileEntity>
}
