package com.medicalsystem.backend.dto

import com.medicalsystem.backend.model.RiskStatus

data class HighRiskStudentDto(
    val studentId: Long,
    val studentName: String,
    val studentNumber: String,
    val riskStatus: RiskStatus,
    val riskFlags: List<String>,
    val scidDiagnosis: String?
)
