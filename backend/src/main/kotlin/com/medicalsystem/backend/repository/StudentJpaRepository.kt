package com.medicalsystem.backend.repository

import com.medicalsystem.backend.entity.StudentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface StudentJpaRepository : JpaRepository<StudentEntity, Long>, JpaSpecificationExecutor<StudentEntity> {
    fun existsByStudentNumber(studentNumber: String): Boolean
    @Query("SELECT COUNT(s) FROM StudentEntity s WHERE s.id <> :studentId AND s.demographics.idCardNumber = :idCard")
    fun countOthersByIdCardNumber(studentId: Long, idCard: String): Long
    fun findByStudentNumber(studentNumber: String): StudentEntity?
    fun findByMajorCollegeId(collegeId: Long): List<StudentEntity>
    fun countByMajorId(majorId: Long): Long
    fun countByDemographicsEthnicityId(ethnicityId: Long): Long
    fun countByDemographicsSchoolId(schoolId: Long): Long
    fun countByDegreeLevelId(degreeLevelId: Long): Long

    @Query("SELECT s.studentNumber FROM StudentEntity s")
    fun findAllStudentNumbers(): Set<String>
}
