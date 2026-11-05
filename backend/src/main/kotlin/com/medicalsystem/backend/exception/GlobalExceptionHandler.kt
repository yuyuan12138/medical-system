package com.medicalsystem.backend.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(e: ResourceNotFoundException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to (e.message ?: "Resource not found")))
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(e: ConflictException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to (e.message ?: "Conflict")))
    }

    /**
     * Safety net: if a version/optimistic-lock conflict escapes a transactional boundary without
     * being translated (e.g. flush at commit time from a non-submit path), still surface it as a
     * clear 409 instead of a 500.
     */
    @ExceptionHandler(
        org.springframework.dao.OptimisticLockingFailureException::class,
        jakarta.persistence.OptimisticLockException::class
    )
    fun handleOptimisticLock(e: Exception): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to "操作冲突，数据已被其他操作修改，请重试"))
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("error" to (e.message ?: "Unauthorized")))
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(e: ValidationException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (e.message ?: "Bad request")))
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: org.springframework.web.bind.MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "Validation failed", "details" to errors))
    }

    @ExceptionHandler(AssessmentValidationException::class)
    fun handleAssessmentValidation(e: AssessmentValidationException): ResponseEntity<Map<String, Any>> {
        val body = mutableMapOf<String, Any>("error" to "Validation failed")
        if (e.missingKeys.isNotEmpty()) {
            body["missingKeys"] = e.missingKeys
        }
        if (e.invalidKeys.isNotEmpty()) {
            body["invalidKeys"] = e.invalidKeys
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }
}
