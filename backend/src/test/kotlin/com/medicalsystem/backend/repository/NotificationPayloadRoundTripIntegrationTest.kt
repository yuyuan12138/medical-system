package com.medicalsystem.backend.repository

import com.medicalsystem.backend.model.NotificationActionType
import com.medicalsystem.backend.model.NotificationMessageCode
import com.medicalsystem.backend.service.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.atomic.AtomicLong

/**
 * Regression: HIGH-risk alert notifications were written with an enriched payload but every
 * read returned {}. The H2 `JSON` column (columnDefinition = "JSON" on a String field) stored
 * the VARCHAR bind value as a JSON *string scalar* (double-encoded), so payload deserialization
 * silently failed. The column is now a plain string; whatever is written must be read back
 * verbatim through the real service write/read path.
 */
@SpringBootTest
class NotificationPayloadRoundTripIntegrationTest {

    @Autowired
    private lateinit var notificationService: NotificationService

    private val seq = AtomicLong(System.currentTimeMillis())

    @Test
    fun `notification payload round-trips through persistence`() {
        val userId = seq.incrementAndGet()

        notificationService.createNotification(
            userId = userId,
            messageCode = NotificationMessageCode.ASSESSMENT_HIGH_RISK_ALERT_HC,
            payload = mapOf(
                "studentNumber" to "RT-$userId",
                "studentName" to "回归学生-$userId",
                "riskStatus" to "HIGH",
                "riskFlags" to listOf("DEPRESSION_HIGH", "SELF_HARM"),
                "scaleCode" to "phq_9"
            ),
            actionType = NotificationActionType.VIEW_RECORDS,
            actionTargetId = 1L
        )

        val read = notificationService.getNotificationsForUser(userId)
        assertTrue(read.isNotEmpty(), "notification should be persisted and readable")

        val payload = read.first().payload
        assertEquals("RT-$userId", payload["studentNumber"])
        assertEquals("回归学生-$userId", payload["studentName"])
        assertEquals("HIGH", payload["riskStatus"])
        assertEquals(listOf("DEPRESSION_HIGH", "SELF_HARM"), payload["riskFlags"])
        assertEquals("phq_9", payload["scaleCode"])
    }
}
