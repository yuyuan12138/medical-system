package com.medicalsystem.backend.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.medicalsystem.backend.entity.NotificationEntity
import com.medicalsystem.backend.model.Notification
import com.medicalsystem.backend.model.NotificationActionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class NotificationRepositoryAdapter(
    private val notificationJpaRepository: NotificationJpaRepository
) : NotificationRepository {

    private val objectMapper = ObjectMapper()
    private val logger = LoggerFactory.getLogger(NotificationRepositoryAdapter::class.java)

    override fun save(notification: Notification): Notification {
        val entity = NotificationEntity(
            id = notification.id ?: 0L,
            userId = notification.userId,
            messageCode = notification.messageCode,
            payload = objectMapper.writeValueAsString(notification.payload),
            isRead = notification.isRead,
            createdAt = notification.createdAt,
            actionType = notification.actionType,
            actionTargetId = notification.actionTargetId,
            isActionAvailable = notification.isActionAvailable
        )
        val savedEntity = notificationJpaRepository.save(entity)
        return toDomain(savedEntity)
    }

    override fun saveAll(notifications: List<Notification>): List<Notification> {
        return notifications.map { save(it) }
    }

    override fun invalidateActionsForTarget(actionType: NotificationActionType, targetId: Long) {
        notificationJpaRepository.invalidateActionsForTarget(actionType, targetId)
    }

    override fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Notification> {
        return notificationJpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId).map { toDomain(it) }
    }

    override fun findById(id: Long): Notification? {
        return notificationJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun countUnreadByUserId(userId: Long): Long {
        return notificationJpaRepository.countByUserIdAndIsReadFalse(userId)
    }

    private fun toDomain(entity: NotificationEntity): Notification {
        val typeRef = object : TypeReference<Map<String, Any>>() {}
        val payloadMap = try {
            objectMapper.readValue(entity.payload, typeRef)
        } catch (e: Exception) {
            logger.warn(
                "Failed to deserialize notification payload (id={}, messageCode={}); returning empty payload",
                entity.id, entity.messageCode, e
            )
            emptyMap()
        }
        
        return Notification(
            id = entity.id,
            userId = entity.userId,
            messageCode = entity.messageCode,
            payload = payloadMap,
            isRead = entity.isRead,
            createdAt = entity.createdAt,
            actionType = entity.actionType,
            actionTargetId = entity.actionTargetId,
            isActionAvailable = entity.isActionAvailable
        )
    }
}
