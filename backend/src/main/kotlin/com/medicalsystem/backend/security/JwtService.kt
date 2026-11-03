package com.medicalsystem.backend.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret:}")
    private val jwtSecret: String
) {
    companion object {
        /**
         * 384-bit HMAC (HS384) is the algorithm JJWT selects for 48-63 byte keys, which is
         * what this service has always used for the committed secret (57 bytes). Require at
         * least 48 bytes so every deployment signs with >= HS384 strength.
         */
        const val MINIMUM_SECRET_BYTES: Int = 48

        // The previously committed default value. It is now public knowledge and is retained
        // ONLY to detect and reject operators who accidentally keep it. Never used as a key.
        const val LEGACY_COMMITTED_SECRET: String = "medical-system-intake-secret-key-must-be-long-enough-32bytes"
    }

    init {
        val secretBytes = jwtSecret.toByteArray(StandardCharsets.UTF_8)
        when {
            secretBytes.isEmpty() -> throw IllegalStateException(
                "jwt.secret is missing or empty. Set the JWT_SECRET environment variable " +
                    "(property jwt.secret) to a random value of at least $MINIMUM_SECRET_BYTES bytes, " +
                    "e.g.: export JWT_SECRET=\$(openssl rand -base64 $MINIMUM_SECRET_BYTES)"
            )
            secretBytes.size < MINIMUM_SECRET_BYTES -> throw IllegalStateException(
                "jwt.secret is only ${secretBytes.size} bytes long; at least $MINIMUM_SECRET_BYTES bytes are " +
                    "required for the HS384 algorithm this service selects. Regenerate JWT_SECRET with a " +
                    "longer random value (e.g. openssl rand -base64 $MINIMUM_SECRET_BYTES)."
            )
            jwtSecret == LEGACY_COMMITTED_SECRET -> throw IllegalStateException(
                "jwt.secret is set to the previously committed, now-public default value. Generate a NEW " +
                    "random secret (JWT_SECRET env var, >= $MINIMUM_SECRET_BYTES bytes) and deploy it."
            )
        }
    }
    private val key: SecretKey by lazy {
        val secretBytes = jwtSecret.toByteArray(StandardCharsets.UTF_8)
        val validBytes = if (secretBytes.size < 32) {
            secretBytes.copyOf(32)
        } else {
            secretBytes
        }
        Keys.hmacShaKeyFor(validBytes)
    }

    private val validityMs: Long = 7 * 24 * 60 * 60 * 1000L

    fun generateToken(userId: Long, studentNumber: String? = null, role: String = "STUDENT"): String {
        val now = Date()
        val expiry = Date(now.time + validityMs)
        val builder = Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)

        if (studentNumber != null) {
            builder.claim("studentNumber", studentNumber)
        }
        return builder.compact()
    }

    fun validateAndExtractUserId(token: String): Long? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            claims.subject?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun validateAndExtractStudentNumber(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            claims["studentNumber"] as? String ?: claims.subject
        } catch (e: Exception) {
            null
        }
    }
}
