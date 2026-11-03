package com.medicalsystem.backend.model

import com.medicalsystem.backend.exception.AssessmentValidationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AssessmentScoringEngineTest {

    @Test
    fun `scoreSection should return correct PsychometricTest`() {
        val answers = mapOf("q1" to 1, "q2" to 2)
        
        val scale = AssessmentScale(
            batteryCode = "MENTAL_HEALTH_ASSESSMENT",
            title = "Test",
            subtitle = "",
            description = "",
            duration = "",
            orderNum = 1,
            sections = listOf(
                AssessmentSection(
                    code = "phq_9",
                    title = "PHQ-9",
                    subtitle = "",
                    description = "",
                    orderNum = 1,
                    questions = listOf(
                        AssessmentQuestion(code = "q1", text = "Q1", orderNum = 1, optionGroup = AssessmentOptionGroup("group1", listOf(
                            AssessmentOption(0, "0"),
                            AssessmentOption(1, "1")
                        ))),
                        AssessmentQuestion(code = "q2", text = "Q2", orderNum = 2, optionGroup = AssessmentOptionGroup("group2", listOf(
                            AssessmentOption(0, "0"),
                            AssessmentOption(2, "2")
                        )))
                    )
                )
            )
        )
        
        val result = AssessmentScoringEngine.scoreSection(1L, scale.sections.first(), answers)

        assertEquals(PsychometricTestType.PHQ_9, result.testType)
        assertEquals(3, result.score.points)
        assertEquals(3, result.score.max)
    }

    @Test
    fun `scoreSectionOrNull computes real score and max for PHQ_9 and GAD_7 and returns null for non-psychometric sections`() {
        val phqSection = AssessmentSection(
            code = "phq_9",
            title = "PHQ-9",
            orderNum = 1,
            questions = (1..9).map { i ->
                AssessmentQuestion(
                    code = "phq9_$i",
                    text = "Q$i",
                    orderNum = i,
                    optionGroup = AssessmentOptionGroup("FOUR_POINT", (0..3).map { AssessmentOption(it, "$it") })
                )
            }
        )
        val gadSection = AssessmentSection(
            code = "gad_7",
            title = "GAD-7",
            orderNum = 2,
            questions = (1..7).map { i ->
                AssessmentQuestion(
                    code = "gad7_$i",
                    text = "Q$i",
                    orderNum = i,
                    optionGroup = AssessmentOptionGroup("FOUR_POINT", (0..3).map { AssessmentOption(it, "$it") })
                )
            }
        )
        val demoSection = AssessmentSection(
            code = "demographics_survey",
            title = "Demographics",
            orderNum = 3,
            questions = listOf(AssessmentQuestion(code = "demo_name", text = "Name", orderNum = 1, type = "text"))
        )

        val answers = mutableMapOf<String, Any>("demo_name" to "张三")
        (1..9).forEach { answers["phq9_$it"] = 2 }
        (1..7).forEach { answers["gad7_$it"] = 3 }

        val phqResult = AssessmentScoringEngine.scoreSectionOrNull(1L, phqSection, answers, 100L)
        assertNotNull(phqResult)
        assertEquals(PsychometricTestType.PHQ_9, phqResult!!.testType)
        assertEquals(18, phqResult.score.points)
        assertEquals(27, phqResult.score.max)
        assertEquals(100L, phqResult.sourceAssignmentId)

        val gadResult = AssessmentScoringEngine.scoreSectionOrNull(1L, gadSection, answers, 100L)
        assertNotNull(gadResult)
        assertEquals(PsychometricTestType.GAD_7, gadResult!!.testType)
        assertEquals(21, gadResult.score.points)
        assertEquals(21, gadResult.score.max)

        val demoResult = AssessmentScoringEngine.scoreSectionOrNull(1L, demoSection, answers, 100L)
        assertNull(demoResult)
    }

    @Test
    fun `scoreSectionOrNull binary-scores GHQ_12 and sums ASRS items 1-18 only`() {
        val ghqSection = AssessmentSection(
            code = "ghq_12_section",
            title = "GHQ-12",
            orderNum = 1,
            questions = (1..12).map { i ->
                AssessmentQuestion(
                    code = "ghq_$i", text = "Q$i", orderNum = i,
                    optionGroup = AssessmentOptionGroup("FOUR_POINT", (0..3).map { AssessmentOption(it, "$it") })
                )
            }
        )
        val ghqAnswers = (1..12).associate { "ghq_$it" to (if (it <= 5) 2 else 0) }
        val ghqResult = AssessmentScoringEngine.scoreSectionOrNull(1L, ghqSection, ghqAnswers, 100L)
        assertNotNull(ghqResult)
        assertEquals(PsychometricTestType.GHQ_12, ghqResult!!.testType)
        assertEquals(5, ghqResult.score.points)
        assertEquals(12, ghqResult.score.max)

        val asrsSection = AssessmentSection(
            code = "adhd",
            title = "ASRS",
            orderNum = 2,
            questions = (1..19).map { i ->
                AssessmentQuestion(
                    code = "adhd_$i", text = "Q$i", orderNum = i,
                    optionGroup = AssessmentOptionGroup("ADHD_OPTIONS", (0..4).map { AssessmentOption(it, "$it") })
                )
            }
        )
        val asrsAnswers = (1..9).associate { "adhd_$it" to 4 } +
            (10..18).associate { "adhd_$it" to 1 } + mapOf("adhd_19" to 4)
        val asrsResult = AssessmentScoringEngine.scoreSectionOrNull(1L, asrsSection, asrsAnswers, 100L)
        assertNotNull(asrsResult)
        assertEquals(PsychometricTestType.ASRS, asrsResult!!.testType)
        assertEquals(45, asrsResult.score.points)
        assertEquals(72, asrsResult.score.max)
    }

    @Test
    fun `validateAnswers should pass when all questions are answered exactly`() {
        val scale = AssessmentScale(
            batteryCode = "TEST", title = "Test", duration = "", orderNum = 1,
            sections = listOf(AssessmentSection(code = "S1", title = "S1", orderNum = 1, questions = listOf(
                AssessmentQuestion(code = "Q1", text = "Q1", orderNum = 1),
                AssessmentQuestion(code = "Q2", text = "Q2", orderNum = 2)
            )))
        )
        val answers = mapOf("Q1" to 1, "Q2" to 2)
        assertDoesNotThrow { AssessmentScoringEngine.validateAnswers(scale, answers) }
    }

    @Test
    fun `validateAnswers should throw exception when a question is missing`() {
        val scale = AssessmentScale(
            batteryCode = "TEST", title = "Test", duration = "", orderNum = 1,
            sections = listOf(AssessmentSection(code = "S1", title = "S1", orderNum = 1, questions = listOf(
                AssessmentQuestion(code = "Q1", text = "Q1", orderNum = 1),
                AssessmentQuestion(code = "Q2", text = "Q2", orderNum = 2)
            )))
        )
        val answers = mapOf("Q1" to 1)
        val exception = assertThrows<AssessmentValidationException> {
            AssessmentScoringEngine.validateAnswers(scale, answers)
        }
        assertEquals(listOf("Q2"), exception.missingKeys)
        assertTrue(exception.invalidKeys.isEmpty())
    }

    @Test
    fun `validateAnswers should throw exception when invalid questions are provided`() {
        val scale = AssessmentScale(
            batteryCode = "TEST", title = "Test", duration = "", orderNum = 1,
            sections = listOf(AssessmentSection(code = "S1", title = "S1", orderNum = 1, questions = listOf(
                AssessmentQuestion(code = "Q1", text = "Q1", orderNum = 1)
            )))
        )
        val answers = mapOf("Q1" to 1, "Q2" to 2)
        val exception = assertThrows<AssessmentValidationException> {
            AssessmentScoringEngine.validateAnswers(scale, answers)
        }
        assertTrue(exception.missingKeys.isEmpty())
        assertEquals(listOf("Q2"), exception.invalidKeys)
    }
}


