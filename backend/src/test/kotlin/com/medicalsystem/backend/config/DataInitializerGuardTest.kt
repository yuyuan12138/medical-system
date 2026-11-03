package com.medicalsystem.backend.config

import com.medicalsystem.backend.config.DataInitializer.Companion.DEFAULT_MAX_STUDENTS_BEFORE_REFUSING
import com.medicalsystem.backend.config.DataInitializer.SeedWipePolicy
import com.medicalsystem.backend.config.DataInitializer.SeedWipePolicy.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the boot-seeding decision logic (FIX: a restart used to TRUNCATE every table by
 * default). The policy is the only gate to the destructive wipe path and must fail CLOSED.
 */
class DataInitializerGuardTest {

    /** Default configuration: no env vars set -> SEED_DEMO_DATA defaults true, wipe defaults false. */
    private fun defaultDecision(studentRows: Long) =
        SeedWipePolicy(seedEnabled = true, wipeRequested = false, maxStudentsBeforeRefusing = DEFAULT_MAX_STUDENTS_BEFORE_REFUSING)
            .decide(studentRows)

    @Test
    fun `default config on empty db seeds demo data without any truncate`() {
        val decision = defaultDecision(0)
        assertTrue(decision is Decision.SeedOnly, "expected SeedOnly, got $decision")
    }

    @Test
    fun `default config on non-empty db skips seeding and never truncates`() {
        val decision = defaultDecision(10)
        assertTrue(decision is Decision.NoOp, "expected NoOp, got $decision")
        assertTrue((decision as Decision.NoOp).reason.contains("skipped"))
    }

    @Test
    fun `wipe flag set but real-looking data is refused (fail closed)`() {
        val policy = SeedWipePolicy(seedEnabled = true, wipeRequested = true, maxStudentsBeforeRefusing = DEFAULT_MAX_STUDENTS_BEFORE_REFUSING)

        // Just one row over the demo threshold
        val justOver = policy.decide(DEFAULT_MAX_STUDENTS_BEFORE_REFUSING + 1)
        assertTrue(justOver is Decision.Refused, "expected Refused, got $justOver")

        // The load-test incident: 10,000 seeded accounts must never be truncated
        val incidentScale = policy.decide(10_000)
        assertTrue(incidentScale is Decision.Refused, "expected Refused, got $incidentScale")

        val reason = (incidentScale as Decision.Refused).reason
        assertTrue(reason.contains("refused"), "reason must say the wipe was refused: $reason")
        assertTrue(reason.contains("10000"), "reason must name the offending row count: $reason")
    }

    @Test
    fun `wipe flag set with clearly demo data reaches the truncate path`() {
        val policy = SeedWipePolicy(seedEnabled = true, wipeRequested = true, maxStudentsBeforeRefusing = DEFAULT_MAX_STUDENTS_BEFORE_REFUSING)

        val empty = policy.decide(0)
        assertTrue(empty is Decision.WipeAndSeed, "expected WipeAndSeed on empty db, got $empty")

        // Demo seeding creates exactly 10 students
        val demo = policy.decide(10)
        assertTrue(demo is Decision.WipeAndSeed, "expected WipeAndSeed for 10 demo students, got $demo")

        // Boundary: exactly at the limit is still allowed
        val atLimit = policy.decide(DEFAULT_MAX_STUDENTS_BEFORE_REFUSING)
        assertTrue(atLimit is Decision.WipeAndSeed, "expected WipeAndSeed at the limit, got $atLimit")
        assertEquals(DEFAULT_MAX_STUDENTS_BEFORE_REFUSING, (atLimit as Decision.WipeAndSeed).studentRows)
    }

    @Test
    fun `seed flag off disables both seeding and any truncate`() {
        // Even with the destructive flag set, SEED_DEMO_DATA=false must win: no seed, no truncate.
        val decision = SeedWipePolicy(seedEnabled = false, wipeRequested = true, maxStudentsBeforeRefusing = DEFAULT_MAX_STUDENTS_BEFORE_REFUSING)
            .decide(0)
        assertTrue(decision is Decision.NoOp, "expected NoOp when seeding is disabled, got $decision")
    }

    @Test
    fun `custom max students threshold is respected`() {
        val policy = SeedWipePolicy(seedEnabled = true, wipeRequested = true, maxStudentsBeforeRefusing = 3)
        assertTrue(policy.decide(3) is Decision.WipeAndSeed)
        assertTrue(policy.decide(4) is Decision.Refused)
    }
}