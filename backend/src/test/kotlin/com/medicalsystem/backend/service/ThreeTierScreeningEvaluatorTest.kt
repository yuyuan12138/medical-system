package com.medicalsystem.backend.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 甲方三层筛查判别 + 独立 ADHD 名单的纯单元测试。
 * 编码约定与系统实际存储一致（见 ThreeTierScreeningEvaluator 顶部注释）：
 *   GHQ-12 选第3、4项（编码2、3）记1分；是非题"是"=1；ghq_13/14"差"=5；
 *   ghq_15/16/17"经常"=3；nssi"≥1次"=2；G31"有"=1；ASRS 0-4。
 */
class ThreeTierScreeningEvaluatorTest {

    private val evaluator = ThreeTierScreeningEvaluator()
    private val HIGH = ThreeTierScreeningEvaluator.DangerLevel.HIGH
    private val MEDIUM = ThreeTierScreeningEvaluator.DangerLevel.MEDIUM
    private val LOW = ThreeTierScreeningEvaluator.DangerLevel.LOW

    // 工具：构造作答
    private fun ghq12Binary(n: Int, code: Int = 2): Map<String, Any> =
        (1..12).associate { "ghq_$it" to (if (it <= n) code else 0) }

    private fun adhd(ad: Int, hd: Int, childhood: Int): Map<String, Any> {
        val m = mutableMapOf<String, Any>()
        var rest = ad
        for (i in 1..9) { val v = minOf(4, rest); m["adhd_$i"] = v; rest -= v }
        rest = hd
        for (i in 10..18) { val v = minOf(4, rest); m["adhd_$i"] = v; rest -= v }
        m["adhd_19"] = childhood
        return m
    }

    private fun phq(score: Int): Map<String, Any> {
        val m = mutableMapOf<String, Any>()
        var rest = score
        for (i in 1..9) { val v = minOf(3, rest); m["phq9_$i"] = v; rest -= v }
        return m
    }

    private fun gad(score: Int): Map<String, Any> {
        val m = mutableMapOf<String, Any>()
        var rest = score
        for (i in 1..7) { val v = minOf(3, rest); m["gad7_$i"] = v; rest -= v }
        return m
    }

    private fun highRiskBase(): Map<String, Any> = ghq12Binary(5)

    // ===== 第一层：GHQ-12 二分计分 =====
    @Test fun `GHQ12 all options 1-2 give binary 0`() {
        val answers = (1..12).associate { "ghq_$it" to 1 }
        assertEquals(0, evaluator.ghq12BinaryTotal(answers))
    }
    @Test fun `GHQ12 code 2-or-3 each count 1`() {
        assertEquals(5, evaluator.ghq12BinaryTotal(ghq12Binary(5)))
    }
    @Test fun `GHQ12 all code 3 gives 12`() {
        assertEquals(12, evaluator.ghq12BinaryTotal(ghq12Binary(12, code = 3)))
    }
    @Test fun `GHQ12 missing answers count 0`() {
        assertEquals(0, evaluator.ghq12BinaryTotal(emptyMap()))
    }

    // ===== 第一层：其他危险因子（14项） =====
    @Test fun `risk factors empty is 0`() {
        assertEquals(0, evaluator.otherRiskFactorCount(emptyMap()))
    }
    @Test fun `all 14 risk factor conditions give 14`() {
        val answers = mapOf<String, Any>(
            "ghq_13" to 5, "ghq_14" to 5, "ghq_15" to 3, "ghq_16" to 3, "ghq_17" to 3,
            "ghq_18" to 1, "ghq_19" to 1,
            "ghq_20" to listOf("tcm", "western_doctor", "psychiatrist"),
            "ghq_21" to 1, "ghq_22" to 1, "ghq_23" to 1, "ghq_24" to 1
        )
        assertEquals(14, evaluator.otherRiskFactorCount(answers))
    }
    @Test fun `ghq_20 counts only tcm western_doctor psychiatrist`() {
        val answers = mapOf<String, Any>("ghq_20" to listOf("tcm", "friends", "witch_doctor"))
        assertEquals(1, evaluator.otherRiskFactorCount(answers))
    }
    @Test fun `non-risk option values do not count`() {
        val answers = mapOf<String, Any>("ghq_13" to 4, "ghq_15" to 2, "ghq_18" to 0)
        assertEquals(0, evaluator.otherRiskFactorCount(answers))
    }

    // ===== 第一层：危险分级 =====
    @Test fun `ghq12 gte 5 is HIGH`() {
        val r = evaluator.screen(ghq12Binary(5), "2025010005")
        assertEquals(HIGH, r.dangerLevel); assertEquals(5, r.ghq12BinaryTotal)
    }
    @Test fun `any risk factor is HIGH even with low ghq`() {
        val r = evaluator.screen(ghq12Binary(0) + mapOf<String, Any>("ghq_24" to 1), "2025010001")
        assertEquals(HIGH, r.dangerLevel); assertEquals(0, r.ghq12BinaryTotal)
        assertEquals(1, r.otherRiskFactorCount)
    }
    @Test fun `ghq 2-4 no risk factor is MEDIUM`() {
        assertEquals(MEDIUM, evaluator.screen(ghq12Binary(3), "2025010001").dangerLevel)
    }
    @Test fun `ghq 0-1 no risk factor is LOW`() {
        assertEquals(LOW, evaluator.screen(ghq12Binary(0), "2025010001").dangerLevel)
        assertEquals(LOW, evaluator.screen(ghq12Binary(1), "2025010001").dangerLevel)
    }
    @Test fun `risk factor overrides medium ghq to HIGH`() {
        assertEquals(HIGH, evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_18" to 1), "2025010001").dangerLevel)
    }

    // ===== 第一层：SCID 抽样访谈标记 =====
    @Test fun `SCID HIGH always sampled`() {
        assertTrue(evaluator.screen(ghq12Binary(5), "2025010009").scidInterview)
    }
    @Test fun `SCID MEDIUM sampled only tail 1-4`() {
        val a = ghq12Binary(3)
        assertTrue(evaluator.screen(a, "2025010001").scidInterview)
        assertTrue(evaluator.screen(a, "2025010004").scidInterview)
        assertFalse(evaluator.screen(a, "2025010005").scidInterview)
        assertFalse(evaluator.screen(a, "2025010000").scidInterview)
    }
    @Test fun `SCID LOW sampled only tail 1`() {
        val a = ghq12Binary(0)
        assertTrue(evaluator.screen(a, "2025010001").scidInterview)
        assertFalse(evaluator.screen(a, "2025010002").scidInterview)
    }
    @Test fun `SCID non-numeric tail not sampled for MEDIUM`() {
        assertFalse(evaluator.screen(ghq12Binary(3), "AB-XYZ").scidInterview)
    }

    // ===== 第二层：临床指标筛选（仅高危者） =====
    @Test fun `T2 PHQ-9 gte 15 positive`() {
        val r = evaluator.screen(highRiskBase() + phq(15), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("PHQ-9≥15"))
    }
    @Test fun `T2 PHQ-9 14 not positive`() {
        assertFalse(evaluator.screen(highRiskBase() + phq(14), "2025010001").layer2Positive)
    }
    @Test fun `T2 GAD-7 gte 15 positive`() {
        val r = evaluator.screen(highRiskBase() + gad(15), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("GAD-7≥15"))
    }
    @Test fun `T2 ghq_23 yes positive`() {
        val r = evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_23" to 1), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("自伤/自杀想法"))
    }
    @Test fun `T2 ghq_22 yes positive`() {
        val r = evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_22" to 1), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("自伤/自杀行为"))
    }
    @Test fun `T2 NSSI any item gte 1 time positive`() {
        val r = evaluator.screen(highRiskBase() + mapOf<String, Any>("nssi_3" to 2), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("自伤行为（过去一年）"))
    }
    @Test fun `T2 G31 yes positive`() {
        val r = evaluator.screen(highRiskBase() + mapOf<String, Any>("G31" to 1), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("门诊就诊史"))
    }
    @Test fun `T2 ghq_21 yes positive`() {
        val r = evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_21" to 1), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("GHQ中曾就诊"))
    }
    @Test fun `T2 LOW risk never enters tier 2 even with high PHQ`() {
        val r = evaluator.screen(phq(27), "2025010001")
        assertEquals(LOW, r.dangerLevel); assertFalse(r.layer2Positive)
        assertTrue(r.screeningReasons.isEmpty())
    }
    @Test fun `T2 HIGH without tier-2 condition not positive`() {
        val r = evaluator.screen(highRiskBase(), "2025010001")
        assertEquals(HIGH, r.dangerLevel); assertFalse(r.layer2Positive)
        assertTrue(r.screeningReasons.isEmpty())
    }

    // ===== 第三层：筛选理由（统一写法 + 连接） =====
    @Test fun `T3 reasons unified wording joined by plus in appendix order`() {
        val answers = ghq12Binary(5) + phq(18) + gad(16) +
            mapOf<String, Any>("G31" to 1, "ghq_21" to 1, "nssi_1" to 3)
        val r = evaluator.screen(answers, "2025010001")
        assertEquals(
            listOf("SCID访谈对象", "PHQ-9≥15", "GAD-7≥15", "自伤行为（过去一年）", "门诊就诊史", "GHQ中曾就诊"),
            r.screeningReasons
        )
        assertEquals("SCID访谈对象+PHQ-9≥15+GAD-7≥15+自伤行为（过去一年）+门诊就诊史+GHQ中曾就诊", r.screeningReasonsJoined)
    }
    @Test fun `T3 self-harm ideation and behavior reasons appear`() {
        val r = evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_23" to 1, "ghq_22" to 1), "2025010001")
        assertTrue(r.screeningReasons.contains("自伤/自杀想法"))
        assertTrue(r.screeningReasons.contains("自伤/自杀行为"))
    }
    @Test fun `T3 reasons are unique no duplicate phrasing`() {
        val r = evaluator.screen(ghq12Binary(5) + mapOf<String, Any>("ghq_23" to 1), "2025010001")
        assertEquals(r.screeningReasons.distinct(), r.screeningReasons)
    }

    // ===== 独立 ADHD 名单（附录8 四阈值须同时满足） =====
    @Test fun `ADHD AD HD total childhood scores computed`() {
        val r = evaluator.adhdScreen(adhd(ad = 20, hd = 15, childhood = 4))
        assertEquals(20, r.adScore); assertEquals(15, r.hdScore)
        assertEquals(35, r.asrsTotal); assertEquals(4, r.childhoodScore)
    }
    @Test fun `ADHD all four thresholds met lands on list`() {
        val r = evaluator.adhdScreen(adhd(20, 15, 4) + phq(5))
        assertTrue(r.isAdhdList)
        assertTrue(r.metTotalThreshold && r.metDimensionThreshold && r.metChildhoodThreshold && r.metPhqExclusion)
    }
    @Test fun `ADHD total below 30 fails`() {
        val r = evaluator.adhdScreen(adhd(19, 10, 4) + phq(5))
        assertFalse(r.isAdhdList); assertFalse(r.metTotalThreshold)
    }
    @Test fun `ADHD neither dimension gte 19 fails`() {
        val r = evaluator.adhdScreen(adhd(18, 18, 4) + phq(5))
        assertFalse(r.isAdhdList); assertFalse(r.metDimensionThreshold)
    }
    @Test fun `ADHD HD dimension alone satisfies dimension threshold`() {
        val r = evaluator.adhdScreen(adhd(10, 20, 4) + phq(5))
        assertTrue(r.isAdhdList)
    }
    @Test fun `ADHD childhood below 3 fails`() {
        val r = evaluator.adhdScreen(adhd(20, 15, 2) + phq(5))
        assertFalse(r.isAdhdList); assertFalse(r.metChildhoodThreshold)
    }
    @Test fun `ADHD PHQ-9 exclusion 8 fails 7 passes`() {
        assertFalse(evaluator.adhdScreen(adhd(20, 15, 4) + phq(8)).isAdhdList)
        assertTrue(evaluator.adhdScreen(adhd(20, 15, 4) + phq(7)).isAdhdList)
    }
    @Test fun `ADHD via screen is independent of three tiers`() {
        val r = evaluator.screen(adhd(20, 15, 4) + phq(5), "2025010001")
        assertEquals(LOW, r.dangerLevel)
        assertTrue(r.adhd.isAdhdList)
    }
}