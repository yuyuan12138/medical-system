package com.medicalsystem.backend.service

import com.medicalsystem.backend.config.AssessmentCatalogLoader
import com.medicalsystem.backend.entity.StudentDemographicsEntity
import com.medicalsystem.backend.entity.StudentEntity
import com.medicalsystem.backend.model.AssessmentQuestion
import com.medicalsystem.backend.model.Gender
import com.medicalsystem.backend.repository.EthnicityJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DemographicsProjector(
    private val catalogLoader: AssessmentCatalogLoader,
    private val ethnicityJpaRepository: EthnicityJpaRepository,
    private val studentJpaRepository: com.medicalsystem.backend.repository.StudentJpaRepository? = null
) {
    fun projectDemographics(student: StudentEntity, answers: Map<String, Any>) {
        if (answers.isEmpty()) return

        val demoScale = catalogLoader.getScaleDetail("comprehensive_student_intake_survey")
            ?: catalogLoader.getScaleDetail("demographics_survey")
        val allQuestions = demoScale?.questions ?: emptyList()
        val questionsByField = allQuestions.filter { it.field != null }.associateBy { it.field!! }
        val questionsByCode = allQuestions.associateBy { it.code }

        val idCard = resolveText(questionsByField["idCardNumber"] ?: questionsByCode["demo_id_card"], answers, listOf("demo_id_card", "idCardNumber"))
        val birthdayAnswer = resolveText(questionsByField["birthday"] ?: questionsByCode["G2"], answers, listOf("G2", "birthday", "dateOfBirth", "birthDate"))
        val birthday = extractBirthday(idCard, birthdayAnswer)

        val rawGender = resolveChoice(questionsByField["gender"] ?: questionsByCode["G1"], answers, listOf("G1", "demo_gender", "gender"), isGender = true)
        val gender = when (rawGender) {
            "1", "男", "MALE" -> Gender.MALE
            "2", "女", "FEMALE" -> Gender.FEMALE
            else -> if (idCard != null && idCard.length == 18) {
                val code = idCard.substring(16, 17).toIntOrNull()
                if (code != null) if (code % 2 == 1) Gender.MALE else Gender.FEMALE else null
            } else null
        }

        val rawEthnicity = resolveChoice(questionsByField["ethnicity"] ?: questionsByCode["G4"], answers, listOf("G4", "demo_ethnicity", "ethnicity")) ?: "汉族"
        val ethnicityEntity = ethnicityJpaRepository.findByName(rawEthnicity)
            .or {
                if (rawEthnicity == "少数民族") ethnicityJpaRepository.findByName("其他")
                else ethnicityJpaRepository.findByName("少数民族")
            }
            .orElseGet {
                ethnicityJpaRepository.findAll().firstOrNull()
            }

        val contactNumber = resolveText(questionsByField["phone"] ?: questionsByCode["demo_phone"], answers, listOf("demo_phone", "phone", "contactNumber"))
        val email = resolveText(questionsByField["email"] ?: questionsByCode["demo_email"], answers, listOf("demo_email", "email"))
        val address = resolveText(null, answers, listOf("demo_home_address", "homeAddress"))
            ?: resolveChoice(questionsByField["homeAddress"] ?: questionsByCode["G6"], answers, listOf("G6"))
        val emergencyContact = resolveText(questionsByField["emergencyContact"], answers, listOf("demo_emergency_contact", "emergencyContactName"))
        val emergencyPhone = resolveText(questionsByField["emergencyPhone"], answers, listOf("demo_emergency_phone", "emergencyContactPhone"))

        val currentDemo = student.demographics ?: StudentDemographicsEntity()
        if (gender != null) currentDemo.gender = gender
        if (birthday != null) currentDemo.dateOfBirth = birthday
        if (ethnicityEntity != null) currentDemo.ethnicity = ethnicityEntity
        if (idCard != null) {
            val conflicting = student.id
                ?.let { id -> studentJpaRepository?.countOthersByIdCardNumber(id, idCard) }
                ?.let { it > 0 } ?: false
            if (!conflicting) {
                currentDemo.idCardNumber = idCard
            }
        }
        if (contactNumber != null) currentDemo.contactNumber = contactNumber
        if (email != null) currentDemo.email = email
        if (address != null) currentDemo.homeAddress = address
        if (emergencyContact != null) currentDemo.emergencyContactName = emergencyContact
        if (emergencyPhone != null) currentDemo.emergencyContactPhone = emergencyPhone

        student.demographics = currentDemo
    }

    private fun resolveChoice(
        question: AssessmentQuestion?,
        answers: Map<String, Any>,
        fallbackKeys: List<String>,
        isGender: Boolean = false
    ): String? {
        val rawObj = question?.let { answers[it.code] } ?: fallbackKeys.firstNotNullOfOrNull { answers[it] } ?: return null
        if (rawObj is Map<*, *>) {
            val textVal = rawObj["text"]?.toString()?.trim()
            if (!textVal.isNullOrEmpty() && !isGender) {
                return textVal
            }
            val innerVal = rawObj["value"]?.toString()?.trim() ?: return null
            if (question != null && question.effectiveOptions.isNotEmpty()) {
                val matched = question.effectiveOptions.find { it.value.toString() == innerVal }
                if (matched != null) return matched.label
            }
            return innerVal
        }
        val rawVal = rawObj.toString().trim()
        if (rawVal.isEmpty()) return null

        if (question != null && question.effectiveOptions.isNotEmpty()) {
            val matched = question.effectiveOptions.find { it.value.toString() == rawVal }
            if (matched != null) return matched.label
        }

        if (isGender) {
            return when (rawVal) {
                "1" -> "男"
                "2" -> "女"
                else -> rawVal
            }
        }
        return rawVal
    }

    private fun resolveText(question: AssessmentQuestion?, answers: Map<String, Any>, fallbackKeys: List<String>): String? {
        val raw = (question?.let { answers[it.code] } ?: fallbackKeys.firstNotNullOfOrNull { answers[it] })?.toString()?.trim()
        return if (raw.isNullOrEmpty()) null else raw
    }

    private fun extractBirthday(idCard: String?, birthdayStr: String?): LocalDate? {
        if (!birthdayStr.isNullOrBlank()) {
            try {
                return LocalDate.parse(birthdayStr.trim())
            } catch (_: Exception) {}
        }
        if (idCard != null && idCard.length == 18) {
            try {
                val year = idCard.substring(6, 10).toInt()
                val month = idCard.substring(10, 12).toInt()
                val day = idCard.substring(12, 14).toInt()
                return LocalDate.of(year, month, day)
            } catch (_: Exception) {}
        }
        return null
    }
}
