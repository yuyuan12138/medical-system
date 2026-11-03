package com.medicalsystem.backend.service

/**
 * 三层筛查判别器（甲方《新生心理健康筛查：三级筛查判定问卷与评分标准》）。
 *
 * 纯函数、无状态、不依赖 Spring / 数据库，可直接单测。
 *
 * 编码约定（重要）：
 * 本判别器按**甲方文档编码**（《三级筛查判定问卷与评分标准》附录1–9，
 * 与甲方2025年系统导出编码一致）解释作答。问卷 JSON 的选项取值编码已对齐甲方文档，
 * 系统写入的答案即甲方文档编码，无需转换：
 *   - GHQ-12（ghq_1..12）：选项按 0..3 编码；选第3、4项（编码 2、3）记1分，选第1、2项（编码 0、1）记0分。
 *   - GHQ 附加是非题（ghq_18/19/21/22/23/24）："是"=0、"否"=1（甲方附录2）。
 *   - GHQ 附加 ghq_13/14（躯体/心理健康自评）：优秀=0 .. 差=4，选"差"（编码4）计危险因子。
 *   - GHQ 附加 ghq_15/16/17：从无=0 .. 经常=3，选"经常"（编码3）计危险因子。
 *   - GHQ 附加 ghq_20（多选）：字符串码 tcm / western_doctor / psychiatrist 等（勾选中医/普通西医/精神科医生各计1分）。
 *   - 自伤行为问卷（nssi_1..5）：0次=0、1~2次=1、3~5次=2、6次及以上=3；任一条编码>=1（即≥1次）为阳性。
 *   - 一般情况 G31（门诊就诊史）："有"=0、"没有"=1，选"有"（编码0）为阳性。
 *   - ASRS（adhd_1..19）：从不=0 .. 非常频繁=4（甲方附录8）。
 *   - PHQ-9（phq9_1..9）/ GAD-7（gad7_1..7）：0..3。
 *
 * 三层判定：
 *  第一层（全部作答者）危险分级：
 *    - GHQ-12 二分总分 0-12；其他危险因子 0-14（附录2 的14项，每满足1项计1分）。
 *    - 高危：GHQ总分>=5 或 其他危险因子>=1
 *    - 中危：未达高危 且 GHQ总分2-4
 *    - 低危：GHQ总分0-1 且 无危险因子
 *    - SCID抽样访谈标记（附录3，非SCID确诊）：高危全访谈；中危编号尾号1-4；低危尾号1。
 *  第二层（仅高危者）临床指标筛选，满足任一即阳性：
 *    - PHQ-9总分>=15；GAD-7总分>=15；ghq_23"是"（自伤/自杀想法）；ghq_22"是"（自伤/自杀行为）；
 *      自伤行为问卷任一条>=1次；门诊就诊史 G31"有"；ghq_21"是"（曾住院）。
 *  第三层（最终名单）：第二层阳性者列名单，逐人标注"筛选理由"（统一写法，多条以"+"连接）。
 *
 * 独立 ADHD 名单（不属于三层，附录8）：
 *    ASRS总分(=AD+HD, 第1-18题)>=30 且 (AD分>=19 或 HD分>=19) 且 童年表现(第19题)>=3 且 PHQ-9<8。
 */
class ThreeTierScreeningEvaluator {

    /** 第一层危险等级。 */
    enum class DangerLevel { HIGH, MEDIUM, LOW }

    /** 独立 ADHD 名单判定结果。 */
    data class AdhdScreeningResult(
        val isAdhdList: Boolean,
        val adScore: Int,          // 第1-9题相加，0-36
        val hdScore: Int,          // 第10-18题相加，0-36
        val asrsTotal: Int,        // AD + HD，0-72
        val childhoodScore: Int,   // 第19题原始分，0-4
        val phq9Score: Int,        // 用于排除条件 PHQ-9<8
        val metTotalThreshold: Boolean,   // asrsTotal >= 30
        val metDimensionThreshold: Boolean, // adScore >= 19 || hdScore >= 19
        val metChildhoodThreshold: Boolean, // childhoodScore >= 3
        val metPhqExclusion: Boolean       // phq9Score < 8
    )

    /** 三层判别结果。 */
    data class ScreeningResult(
        val ghq12BinaryTotal: Int,     // 0-12
        val otherRiskFactorCount: Int, // 0-14
        val dangerLevel: DangerLevel,  // 第一层
        val scidInterview: Boolean,    // SCID抽样访谈标记（附录3）
        val layer2Positive: Boolean,   // 第二层阳性（仅高危者有意义）
        val screeningReasons: List<String>, // 第三层筛选理由（统一写法）
        val screeningReasonsJoined: String, // 以"+"连接
        val phq9Score: Int,
        val gad7Score: Int,
        val adhd: AdhdScreeningResult
    )

    companion object {
        // 第二层阳性阈值
        const val PHQ9_LAYER2_THRESHOLD = 15
        const val GAD7_LAYER2_THRESHOLD = 15
        // 第一层阈值
        const val GHQ12_HIGH_THRESHOLD = 5
        const val RISK_FACTOR_HIGH_THRESHOLD = 1
        // ADHD 四阈值（须同时满足）
        const val ADHD_TOTAL_THRESHOLD = 30
        const val ADHD_DIMENSION_THRESHOLD = 19
        const val ADHD_CHILDHOOD_THRESHOLD = 3
        const val ADHD_PHQ9_EXCLUSION = 8

        /** 第三层筛选理由的统一写法（甲方附录4，顺序即表中顺序）。 */
        const val REASON_SCID = "SCID访谈对象"
        const val REASON_PHQ9 = "PHQ-9≥15"
        const val REASON_GAD7 = "GAD-7≥15"
        const val REASON_SELF_HARM_IDEATION = "自伤/自杀想法"
        const val REASON_SELF_HARM_BEHAVIOR = "自伤/自杀行为"
        const val REASON_NSSI = "自伤行为（过去一年）"
        const val REASON_OUTPATIENT = "门诊就诊史"
        const val REASON_GHQ_ADMITTED = "GHQ中曾就诊"

        /** ghq_20 多选题中计为危险因子的选项码（中医/普通西医/精神科大夫）。 */
        private val GHQ20_RISK_FACTOR_CODES = setOf("tcm", "western_doctor", "psychiatrist")
    }

    /**
     * 执行三层判别 + 独立 ADHD 名单判定。
     * @param answers 作答映射，键为条目编码（ghq_1..24 / phq9_1..9 / gad7_1..7 / nssi_1..5 / G31 / adhd_1..19）。
     * @param studentNumber 受试者编号，用于 SCID 抽样访谈的尾号判定（附录3）。
     */
    fun screen(answers: Map<String, Any>, studentNumber: String): ScreeningResult {
        val ghq12 = ghq12BinaryTotal(answers)
        val riskFactors = otherRiskFactorCount(answers)
        val phq9 = phq9Total(answers)
        val gad7 = gad7Total(answers)

        val dangerLevel = when {
            ghq12 >= GHQ12_HIGH_THRESHOLD || riskFactors >= RISK_FACTOR_HIGH_THRESHOLD -> DangerLevel.HIGH
            ghq12 in 2..4 -> DangerLevel.MEDIUM
            else -> DangerLevel.LOW
        }

        val scidInterview = when (dangerLevel) {
            DangerLevel.HIGH -> true
            DangerLevel.MEDIUM -> tailDigit(studentNumber) in 1..4
            DangerLevel.LOW -> tailDigit(studentNumber) == 1
        }

        // 第二层仅针对第一层高危者。
        val hasSelfHarmIdeation = isYes(answers["ghq_23"])
        val hasSelfHarmBehavior = isYes(answers["ghq_22"])
        val nssiAnyPositive = nssiAnyPositive(answers)
        val hasOutpatient = isYes(answers["G31"])
        val ghqAdmitted = isYes(answers["ghq_21"])
        val phq9Positive = phq9 >= PHQ9_LAYER2_THRESHOLD
        val gad7Positive = gad7 >= GAD7_LAYER2_THRESHOLD

        val layer2Positive = dangerLevel == DangerLevel.HIGH && (
            phq9Positive || gad7Positive || hasSelfHarmIdeation ||
                hasSelfHarmBehavior || nssiAnyPositive || hasOutpatient || ghqAdmitted
            )

        // 第三层筛选理由（统一写法）：列入名单者均为第一层高危，故含 SCID访谈对象。
        val reasons = mutableListOf<String>()
        if (layer2Positive) {
            reasons += REASON_SCID
            if (phq9Positive) reasons += REASON_PHQ9
            if (gad7Positive) reasons += REASON_GAD7
            if (hasSelfHarmIdeation) reasons += REASON_SELF_HARM_IDEATION
            if (hasSelfHarmBehavior) reasons += REASON_SELF_HARM_BEHAVIOR
            if (nssiAnyPositive) reasons += REASON_NSSI
            if (hasOutpatient) reasons += REASON_OUTPATIENT
            if (ghqAdmitted) reasons += REASON_GHQ_ADMITTED
        }

        val adhd = adhdScreen(answers, phq9)

        return ScreeningResult(
            ghq12BinaryTotal = ghq12,
            otherRiskFactorCount = riskFactors,
            dangerLevel = dangerLevel,
            scidInterview = scidInterview,
            layer2Positive = layer2Positive,
            screeningReasons = reasons,
            screeningReasonsJoined = reasons.joinToString("+"),
            phq9Score = phq9,
            gad7Score = gad7,
            adhd = adhd
        )
    }

    /** GHQ-12 二分总分（0-12）：ghq_1..12 中编码>=2（选第3、4项）的条数。 */
    fun ghq12BinaryTotal(answers: Map<String, Any>): Int =
        (1..12).count { i -> intValue(answers["ghq_$i"]) >= 2 }

    /** 其他危险因子数量（0-14），附录2 的14项条件。 */
    fun otherRiskFactorCount(answers: Map<String, Any>): Int {
        var count = 0
        if (intValue(answers["ghq_13"]) == 4) count++   // 躯体健康自评"差"（编码4）
        if (intValue(answers["ghq_14"]) == 4) count++   // 心理健康自评"差"（编码4）
        if (intValue(answers["ghq_15"]) == 3) count++   // 反反复复做/想一件事"经常"
        if (intValue(answers["ghq_16"]) == 3) count++   // 因害怕限制活动"经常"
        if (intValue(answers["ghq_17"]) == 3) count++   // 特别紧张/焦虑"经常"
        if (isYes(answers["ghq_18"])) count++           // 因喝酒遇到麻烦"是"
        if (isYes(answers["ghq_19"])) count++           // 连续两周心情低落"是"
        // ghq_20 多选：勾选 中医/普通西医/精神科大夫 各计1分（最多3分）
        count += selectedValues(answers["ghq_20"]).count { it in GHQ20_RISK_FACTOR_CODES }
        if (isYes(answers["ghq_21"])) count++           // 因精神/心理问题住过院"是"
        if (isYes(answers["ghq_22"])) count++           // 有自伤/自杀行为"是"
        if (isYes(answers["ghq_23"])) count++           // 有自杀/自伤想法"是"
        if (isYes(answers["ghq_24"])) count++           // 家族有精神/心理疾病"是"
        return count
    }

    /** PHQ-9 总分（0-27）。 */
    fun phq9Total(answers: Map<String, Any>): Int = sumRange(answers, "phq9_", 1..9)

    /** GAD-7 总分（0-21）。 */
    fun gad7Total(answers: Map<String, Any>): Int = sumRange(answers, "gad7_", 1..7)

    /** 自伤行为问卷任一条>=1次（甲方文档编码 0次=0，故编码>=1）。 */
    fun nssiAnyPositive(answers: Map<String, Any>): Boolean =
        (1..5).any { i -> intValue(answers["nssi_$i"]) >= 1 }

    /**
     * 独立 ADHD 名单判定（附录8）：四个阈值须同时满足。
     * AD分=adhd_1..9，HD分=adhd_10..18，总分=AD+HD，童年表现=adhd_19。
     */
    fun adhdScreen(answers: Map<String, Any>, phq9Score: Int = phq9Total(answers)): AdhdScreeningResult {
        val adScore = sumRange(answers, "adhd_", 1..9)
        val hdScore = sumRange(answers, "adhd_", 10..18)
        val asrsTotal = adScore + hdScore
        val childhoodScore = intValue(answers["adhd_19"])
        val metTotalThreshold = asrsTotal >= ADHD_TOTAL_THRESHOLD
        val metDimensionThreshold = adScore >= ADHD_DIMENSION_THRESHOLD || hdScore >= ADHD_DIMENSION_THRESHOLD
        val metChildhoodThreshold = childhoodScore >= ADHD_CHILDHOOD_THRESHOLD
        val metPhqExclusion = phq9Score < ADHD_PHQ9_EXCLUSION
        val isAdhdList = metTotalThreshold && metDimensionThreshold && metChildhoodThreshold && metPhqExclusion
        return AdhdScreeningResult(
            isAdhdList = isAdhdList,
            adScore = adScore,
            hdScore = hdScore,
            asrsTotal = asrsTotal,
            childhoodScore = childhoodScore,
            phq9Score = phq9Score,
            metTotalThreshold = metTotalThreshold,
            metDimensionThreshold = metDimensionThreshold,
            metChildhoodThreshold = metChildhoodThreshold,
            metPhqExclusion = metPhqExclusion
        )
    }

    // ---- 内部工具 ----

    private fun sumRange(answers: Map<String, Any>, prefix: String, range: IntRange): Int =
        range.sumOf { i -> intValue(answers["$prefix$i"]) }

    private fun intValue(value: Any?): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull() ?: 0
        else -> 0
    }

    /** 是非题"是"判定：甲方文档编码 "是"=0、"否"=1。多选列表视为"是"（含任一选中项）。 */
    private fun isYes(value: Any?): Boolean {
        if (value == null) return false
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() == 0
            is String -> {
                val t = value.trim()
                t == "0" || t == "是" || t.equals("yes", ignoreCase = true) || t.equals("true", ignoreCase = true)
            }
            is List<*> -> value.any { isYes(it) }
            else -> false
        }
    }

    /** 多选题选中值集合（支持 List 或逗号分隔字符串）。 */
    private fun selectedValues(value: Any?): Set<String> = when (value) {
        null -> emptySet()
        is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }.toSet()
        is String -> value.split(",", "，", ";", "；").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        else -> value.toString().trim().takeIf { it.isNotEmpty() }?.let { setOf(it) } ?: emptySet()
    }

    /** 受试者编号的尾号（最后一位数字），非数字返回 -1。 */
    private fun tailDigit(studentNumber: String): Int {
        val last = studentNumber.trim().lastOrNull() ?: return -1
        return if (last.isDigit()) last.digitToInt() else -1
    }
}