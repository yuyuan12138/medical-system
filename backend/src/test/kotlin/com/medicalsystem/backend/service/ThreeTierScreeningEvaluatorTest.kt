package com.medicalsystem.backend.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 甲方三层筛查判别 + 独立 ADHD 名单的纯单元测试。
 * 编码约定与**甲方文档编码**一致（问卷 JSON 已对齐甲方附录，见 ThreeTierScreeningEvaluator 顶部注释）：
 *   GHQ-12 选第3、4项（编码2、3）记1分；是非题"是"=0/"否"=1；ghq_13/14"差"=4；
 *   ghq_15/16/17"经常"=3；NSSI"0次"=0/"≥1次"=1；G31"有"=0/"没有"=1；ASRS 0-4。
 * 本测试用甲方文档编码构造作答，断言判别结果与按甲方文档手工复算一致。
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

    // ===== 第一层：其他危险因子（14项，甲方文档编码） =====
    @Test fun `risk factors empty is 0`() {
        assertEquals(0, evaluator.otherRiskFactorCount(emptyMap()))
    }
    @Test fun `all 14 risk factor conditions give 14`() {
        val answers = mapOf<String, Any>(
            "ghq_13" to 4, "ghq_14" to 4, "ghq_15" to 3, "ghq_16" to 3, "ghq_17" to 3,
            "ghq_18" to 0, "ghq_19" to 0,
            "ghq_20" to listOf("tcm", "western_doctor", "psychiatrist"),
            "ghq_21" to 0, "ghq_22" to 0, "ghq_23" to 0, "ghq_24" to 0
        )
        assertEquals(14, evaluator.otherRiskFactorCount(answers))
    }
    @Test fun `ghq_20 counts only tcm western_doctor psychiatrist`() {
        val answers = mapOf<String, Any>("ghq_20" to listOf("tcm", "friends", "witch_doctor"))
        assertEquals(1, evaluator.otherRiskFactorCount(answers))
    }
    @Test fun `non-risk option values do not count`() {
        // 甲方文档编码：ghq_13=3"一般"（非"差"=4）、ghq_15=2"有时"（非"经常"=3）、ghq_18=1"否"（非"是"=0）
        val answers = mapOf<String, Any>("ghq_13" to 3, "ghq_15" to 2, "ghq_18" to 1)
        assertEquals(0, evaluator.otherRiskFactorCount(answers))
    }
    @Test fun `ghq_13-14 only code 4 (差) counts as risk factor`() {
        // 甲方附录2：13-14题编码0-4，仅"差"=4计危险因子；3"一般"/2"好"不计
        assertEquals(1, evaluator.otherRiskFactorCount(mapOf<String, Any>("ghq_13" to 4)))
        assertEquals(1, evaluator.otherRiskFactorCount(mapOf<String, Any>("ghq_14" to 4)))
        assertEquals(0, evaluator.otherRiskFactorCount(mapOf<String, Any>("ghq_13" to 3, "ghq_14" to 2)))
    }

    // ===== 第一层：危险分级 =====
    @Test fun `ghq12 gte 5 is HIGH`() {
        val r = evaluator.screen(ghq12Binary(5), "2025010005")
        assertEquals(HIGH, r.dangerLevel); assertEquals(5, r.ghq12BinaryTotal)
    }
    @Test fun `any risk factor is HIGH even with low ghq`() {
        val r = evaluator.screen(ghq12Binary(0) + mapOf<String, Any>("ghq_24" to 0), "2025010001")
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
        // ghq_18=0 即"是"（甲方编码"是"=0）-> 危险因子 -> 高危
        assertEquals(HIGH, evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_18" to 0), "2025010001").dangerLevel)
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

    // ===== 第二层：临床指标筛选（仅高危者，甲方文档编码） =====
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
    @Test fun `T2 GAD-7 14 not positive`() {
        assertFalse(evaluator.screen(highRiskBase() + gad(14), "2025010001").layer2Positive)
    }
    @Test fun `T2 ghq_23 yes (code 0) positive`() {
        // 甲方编码"是"=0：ghq_23=0 即"想过自杀或故意伤害自己" -> 阳性
        val r = evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_23" to 0), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("自伤/自杀想法"))
    }
    @Test fun `T2 ghq_23 no (code 1) not positive`() {
        assertFalse(evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_23" to 1), "2025010001").layer2Positive)
    }
    @Test fun `T2 ghq_22 yes (code 0) positive`() {
        val r = evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_22" to 0), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("自伤/自杀行为"))
    }
    @Test fun `T2 ghq_22 no (code 1) not positive`() {
        assertFalse(evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_22" to 1), "2025010001").layer2Positive)
    }
    @Test fun `T2 NSSI code 1 (1~2次) positive, code 0 (0次) not`() {
        // 甲方附录6：编码 0次=0、1~2次=1、3~5次=2、6次及以上=3；任一条编码>=1 即≥1次阳性
        val r1 = evaluator.screen(highRiskBase() + mapOf<String, Any>("nssi_3" to 1), "2025010001")
        assertTrue(r1.layer2Positive); assertTrue(r1.screeningReasons.contains("自伤行为（过去一年）"))
        val r0 = evaluator.screen(highRiskBase() + (1..5).associate { "nssi_$it" to 0 }, "2025010001")
        assertFalse(r0.layer2Positive)
    }
    @Test fun `T2 G31 yes (code 0) positive`() {
        val r = evaluator.screen(highRiskBase() + mapOf<String, Any>("G31" to 0), "2025010001")
        assertTrue(r.layer2Positive); assertTrue(r.screeningReasons.contains("门诊就诊史"))
    }
    @Test fun `T2 G31 no (code 1) not positive`() {
        assertFalse(evaluator.screen(highRiskBase() + mapOf<String, Any>("G31" to 1), "2025010001").layer2Positive)
    }
    @Test fun `T2 ghq_21 yes (code 0) positive`() {
        val r = evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_21" to 0), "2025010001")
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
            mapOf<String, Any>("G31" to 0, "ghq_21" to 0, "nssi_1" to 2)
        val r = evaluator.screen(answers, "2025010001")
        assertEquals(
            listOf("SCID访谈对象", "PHQ-9≥15", "GAD-7≥15", "自伤行为（过去一年）", "门诊就诊史", "GHQ中曾就诊"),
            r.screeningReasons
        )
        assertEquals("SCID访谈对象+PHQ-9≥15+GAD-7≥15+自伤行为（过去一年）+门诊就诊史+GHQ中曾就诊", r.screeningReasonsJoined)
    }
    @Test fun `T3 self-harm ideation and behavior reasons appear`() {
        val r = evaluator.screen(ghq12Binary(4) + mapOf<String, Any>("ghq_23" to 0, "ghq_22" to 0), "2025010001")
        assertTrue(r.screeningReasons.contains("自伤/自杀想法"))
        assertTrue(r.screeningReasons.contains("自伤/自杀行为"))
    }
    @Test fun `T3 reasons are unique no duplicate phrasing`() {
        val r = evaluator.screen(ghq12Binary(5) + mapOf<String, Any>("ghq_23" to 0), "2025010001")
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

    // ===== 甲方文档手工复算端到端（附录1-9，编码即文档编码） =====
    /**
     * 手工复算样例（全部按甲方文档编码作答）：
     *  - GHQ-12：第1,3,5,7,9题选第3项（编码2），其余选第1项（编码0）
     *    -> 二分计分 5 分 -> GHQ总分5 -> 第一层高危（GHQ总分≥5）。
     *  - 危险因子：ghq_14=4（心理健康"差"）计1；ghq_18=0（喝酒遇麻烦"是"）计1；
     *    ghq_20 勾选 中医+朋友 计1（仅中医/普通西医/精神科医生计分）；其余13项均选阴性值。
     *    -> 其他危险因子 3 项。
     *  - PHQ-9 总分 16（≥15 阳性）；GAD-7 总分 14（<15 阴性）。
     *  - nssi_2=1（1~2次）-> 自伤行为（过去一年）阳性；nssi 其余=0（0次）。
     *  - ghq_22=1（否）、ghq_23=1（否）-> 无自伤/自杀行为与想法；
     *    G31=1（没有）-> 无门诊就诊史；ghq_21=1（否）-> 无曾就诊。
     *  - ASRS：AD=10、HD=10、童年=0、PHQ-9=16>=8 -> ADHD 四阈值均不满足。
     *  期望：HIGH、因子3、第二层阳性，理由恰为 SCID访谈对象+PHQ-9≥15+自伤行为（过去一年）；ADHD 不入榜。
     */
    @Test fun `doc manual recomputation end-to-end matches appendix rules`() {
        val answers = (1..12).associate { "ghq_$it" to 0 } +
            mapOf<String, Any>(
                "ghq_1" to 2, "ghq_3" to 2, "ghq_5" to 2, "ghq_7" to 2, "ghq_9" to 2,
                "ghq_13" to 0, "ghq_14" to 4, "ghq_15" to 0, "ghq_16" to 0, "ghq_17" to 0,
                "ghq_18" to 0, "ghq_19" to 1,
                "ghq_20" to listOf("tcm", "friends"),
                "ghq_21" to 1, "ghq_22" to 1, "ghq_23" to 1, "ghq_24" to 1,
                "nssi_1" to 0, "nssi_2" to 1, "nssi_3" to 0, "nssi_4" to 0, "nssi_5" to 0,
                "G31" to 1
            ) + phq(16) + gad(14) + adhd(10, 10, 0)

        val r = evaluator.screen(answers, "2025010011")

        assertEquals(5, r.ghq12BinaryTotal)      // 手工复算：5条编码>=2
        assertEquals(3, r.otherRiskFactorCount)  // 手工复算：ghq_14差 + ghq_18是 + ghq_20中医
        assertEquals(HIGH, r.dangerLevel)        // GHQ总分5>=5 -> 高危（因子3亦独立触发高危）
        assertTrue(r.scidInterview)              // 高危全访谈
        assertEquals(16, r.phq9Score)
        assertEquals(14, r.gad7Score)
        assertTrue(r.layer2Positive)             // PHQ-9 16>=15 且 NSSI 1~2次
        assertEquals(
            listOf("SCID访谈对象", "PHQ-9≥15", "自伤行为（过去一年）"),
            r.screeningReasons
        )
        assertEquals("SCID访谈对象+PHQ-9≥15+自伤行为（过去一年）", r.screeningReasonsJoined)
        assertFalse(r.adhd.isAdhdList)           // 总分20<30、维度10<19、童年0<3、PHQ 16>=8
    }

    /**
     * 手工复算样例二（全部阴性/低危）：
     *  - GHQ-12 全部选第1、2项（编码0/1）-> 0分；危险因子全阴性（是非题全"否"=1、13/14题"优秀"=0、
     *    15-17题"从无"=0、ghq_20 从未求助、NSSI 全0次、G31"没有"=1）。
     *  期望：低危、编号尾号1抽样访谈、第二层不入（非高危）、无理由、PHQ/GAD 0 分、ADHD 不入榜。
     */
    @Test fun `doc manual recomputation all-negative is LOW with no tier-2`() {
        val answers = (1..12).associate { "ghq_$it" to 1 } +
            mapOf<String, Any>(
                "ghq_13" to 0, "ghq_14" to 0, "ghq_15" to 0, "ghq_16" to 0, "ghq_17" to 0,
                "ghq_18" to 1, "ghq_19" to 1, "ghq_20" to listOf("none"),
                "ghq_21" to 1, "ghq_22" to 1, "ghq_23" to 1, "ghq_24" to 1,
                "nssi_1" to 0, "nssi_2" to 0, "nssi_3" to 0, "nssi_4" to 0, "nssi_5" to 0,
                "G31" to 1
            )

        val r = evaluator.screen(answers, "2025010011")

        assertEquals(0, r.ghq12BinaryTotal)
        assertEquals(0, r.otherRiskFactorCount)
        assertEquals(LOW, r.dangerLevel)
        assertTrue(r.scidInterview)              // 低危尾号1 -> 访谈
        assertFalse(r.layer2Positive)
        assertTrue(r.screeningReasons.isEmpty())
        assertEquals(0, r.phq9Score)
        assertEquals(0, r.gad7Score)
        assertFalse(r.adhd.isAdhdList)
    }
}