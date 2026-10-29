package com.medicalsystem.backend.config

import com.medicalsystem.backend.dto.StudentDto
import com.medicalsystem.backend.entity.*
import com.medicalsystem.backend.model.*
import com.medicalsystem.backend.model.*
import com.medicalsystem.backend.repository.*
import com.medicalsystem.backend.service.StudentService
import com.medicalsystem.backend.entity.ReferralDestinationEntity
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.time.LocalDateTime

@Configuration
class DataInitializer {

    @Bean
    fun initData(
        studentService: StudentService,
        userRepository: com.medicalsystem.backend.repository.UserJpaRepository,
        collegeRepository: com.medicalsystem.backend.repository.CollegeJpaRepository,
        majorRepository: com.medicalsystem.backend.repository.MajorJpaRepository,
        studentRepository: com.medicalsystem.backend.repository.StudentJpaRepository,
        degreeLevelRepository: com.medicalsystem.backend.repository.DegreeLevelJpaRepository,
        ethnicityRepository: com.medicalsystem.backend.repository.EthnicityJpaRepository,
        schoolRepository: com.medicalsystem.backend.repository.SchoolJpaRepository,
        referralRepository: ReferralJpaRepository,
        hospitalRepository: HospitalRepository,
        hospitalDepartmentRepository: HospitalDepartmentRepository,
        doctorRepository: DoctorRepository,
        teacherRepository: TeacherRepository,
        trialAdminRepository: TrialAdminRepository,
        headCounsellorJpaRepository: HeadCounsellorJpaRepository,
        schoolDepartmentJpaRepository: SchoolDepartmentJpaRepository,
        studentHealthProfileRepository: StudentHealthProfileJpaRepository,
        assessmentAssignmentRepository: AssessmentAssignmentJpaRepository,
        jdbcTemplate: JdbcTemplate
    ) = CommandLineRunner {
        val seedEnabled = System.getenv("SEED_DEMO_DATA")?.equals("true", ignoreCase = true) ?: true
        if (!seedEnabled) {
            return@CommandLineRunner
        }
        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0")
            val tables = jdbcTemplate.queryForList("SHOW TABLES", String::class.java)
            tables.forEach { table ->
                jdbcTemplate.execute("TRUNCATE TABLE $table")
            }
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1")
        } catch (e: Exception) {
            // H2 doesn't support SHOW TABLES / TRUNCATE in the same way; fallback to repository deleteAll
            assessmentAssignmentRepository.deleteAll()
            referralRepository.deleteAll()
            studentHealthProfileRepository.deleteAll()
            studentRepository.deleteAll()
            trialAdminRepository.deleteAll()
            headCounsellorJpaRepository.deleteAll()
            teacherRepository.deleteAll()
            doctorRepository.deleteAll()
            userRepository.deleteAll()
            hospitalDepartmentRepository.deleteAll()
            hospitalRepository.deleteAll()
            schoolDepartmentJpaRepository.deleteAll()
            majorRepository.deleteAll()
            collegeRepository.deleteAll()
            degreeLevelRepository.deleteAll()
            ethnicityRepository.deleteAll()
            schoolRepository.deleteAll()
        }

        val medCollege = collegeRepository.save(CollegeEntity(name = "医学院"))
        val csCollege = collegeRepository.save(CollegeEntity(name = "计算机学院"))
        val engCollege = collegeRepository.save(CollegeEntity(name = "工程学院"))
        val busCollege = collegeRepository.save(CollegeEntity(name = "商学院"))

        val medMajor = majorRepository.save(MajorEntity(name = "临床医学", college = medCollege))
        val csMajor = majorRepository.save(MajorEntity(name = "计算机科学", college = csCollege))
        val engMajor = majorRepository.save(MajorEntity(name = "机械工程", college = engCollege))
        val busMajor = majorRepository.save(MajorEntity(name = "工商管理", college = busCollege))
        val psychMajor = majorRepository.save(MajorEntity(name = "心理学", college = medCollege))

        val standardEthnicities = listOf(
            "汉族", "蒙古族", "回族", "藏族", "维吾尔族", "苗族", "彝族", "壮族", "布依族", "朝鲜族",
            "满族", "侗族", "瑶族", "白族", "土家族", "哈尼族", "哈萨克族", "傣族", "黎族", "傈僳族",
            "佤族", "畲族", "高山族", "拉祜族", "水族", "东乡族", "纳西族", "景颇族", "柯尔克孜族", "土族",
            "达斡尔族", "仫佬族", "羌族", "布朗族", "撒拉族", "毛南族", "仡佬族", "锡伯族", "阿昌族", "普米族",
            "塔吉克族", "怒族", "乌孜别克族", "俄罗斯族", "鄂温克族", "德昂族", "保安族", "裕固族", "京族", "塔塔尔族",
            "独龙族", "鄂伦春族", "赫哲族", "门巴族", "珞巴族", "基诺族", "少数民族", "其他"
        )
        val ethnicityMap = standardEthnicities.associateWith { name ->
            ethnicityRepository.save(EthnicityEntity(name = name))
        }
        val han = ethnicityMap["汉族"]!!
        val hui = ethnicityMap["回族"]!!
        val man = ethnicityMap["满族"]!!

        val bachelorDegree = degreeLevelRepository.save(DegreeLevelEntity(name = "BACHELOR"))
        val masterDegree = degreeLevelRepository.save(DegreeLevelEntity(name = "MASTER"))
        val phdDegree = degreeLevelRepository.save(DegreeLevelEntity(name = "PHD"))
        val otherDegree = degreeLevelRepository.save(DegreeLevelEntity(name = "OTHER"))

        val mainSchool = schoolRepository.save(SchoolEntity(name = "某重点大学"))
        val csuSchool = schoolRepository.save(SchoolEntity(name = "中南大学"))
        val hnuSchool = schoolRepository.save(SchoolEntity(name = "湖南大学"))
        val pkuSchool = schoolRepository.save(SchoolEntity(name = "北京大学"))
        val hnnSchool = schoolRepository.save(SchoolEntity(name = "湖南师范大学"))
        val csustSchool = schoolRepository.save(SchoolEntity(name = "长沙理工大学"))
        val csuftSchool = schoolRepository.save(SchoolEntity(name = "中南林业科技大学"))
        val otherSchool = schoolRepository.save(SchoolEntity(name = "其他高校"))

        val schoolDeptNames = listOf(
            "心理健康教育与咨询中心",
            "咨询中心",
            "学生工作部（处）",
            "教务处",
            "计算机学院",
            "湘雅医学院",
            "工程学院",
            "商学院",
            "文学院",
            "外国语学院",
            "数学与统计学院",
            "物理与电子学院",
            "化学化工学院",
            "法学院",
            "马克思主义学院",
            "其他部门"
        )
        val allSchools = listOf(mainSchool, csuSchool, hnuSchool, pkuSchool, hnnSchool, csustSchool, csuftSchool, otherSchool)
        allSchools.forEach { s ->
            schoolDeptNames.forEach { deptName ->
                schoolDepartmentJpaRepository.save(SchoolDepartmentEntity(name = deptName, school = s))
            }
        }

        val s1Demo = StudentDemographicsEntity(gender = Gender.MALE, dateOfBirth = LocalDate.of(2004, 5, 12), ethnicity = han, idCardNumber = "110105200405123457", contactNumber = "13800138000", email = "liming@univ.edu.cn", homeAddress = "北京市朝阳区某街道", emergencyContactName = "李建国", emergencyContactPhone = "13900139000", school = mainSchool)
        val s2Demo = StudentDemographicsEntity(gender = Gender.FEMALE, dateOfBirth = LocalDate.of(2003, 8, 24), ethnicity = hui, idCardNumber = "310101200308241240", contactNumber = "13700137000", email = "wangfang@univ.edu.cn", homeAddress = "上海市黄浦区某街道", emergencyContactName = "王强", emergencyContactPhone = "13600136000", school = mainSchool)
        val s3Demo = StudentDemographicsEntity(gender = Gender.MALE, dateOfBirth = LocalDate.of(2005, 11, 2), ethnicity = han, idCardNumber = "440106200511025677", contactNumber = "13500135000", email = "zhangwei@univ.edu.cn", homeAddress = "广州市天河区某街道", emergencyContactName = "张明", emergencyContactPhone = "13400134000", school = mainSchool)
        val s4Demo = StudentDemographicsEntity(gender = Gender.FEMALE, dateOfBirth = LocalDate.of(2002, 3, 15), ethnicity = man, idCardNumber = "210102200203158902", contactNumber = "13300133000", email = "chenxiu@univ.edu.cn", homeAddress = "沈阳市和平区某街道", emergencyContactName = "陈军", emergencyContactPhone = "13200132000", school = mainSchool)

        val passwordEncoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        val defaultStaffPasswordHash = passwordEncoder.encode("123456")
        fun studentPasswordHash(idCard: String?): String? {
            val raw = if (idCard != null && idCard.length >= 6) idCard.takeLast(6) else "123456"
            return passwordEncoder.encode(raw)
        }

        val referrerBase = userRepository.save(UserEntity(name = "艾米丽·沃森", email = EmailAddress("emily@univ.edu.cn"), role = UserRole.TEACHER, passwordHash = defaultStaffPasswordHash))
        val referrer = teacherRepository.save(TeacherEntity(userId = referrerBase.id, employeeNumber = "EMP-00001", college = medCollege))
        val teacher2Base = userRepository.save(UserEntity(name = "李老师", email = EmailAddress("teacher2@univ.edu.cn"), role = UserRole.TEACHER, passwordHash = defaultStaffPasswordHash))
        val teacher2 = teacherRepository.save(TeacherEntity(userId = teacher2Base.id, employeeNumber = "EMP-00002", college = medCollege))

        val u1 = userRepository.save(UserEntity(name = "李明", email = EmailAddress("liming@univ.edu.cn"), role = UserRole.STUDENT, phone = s1Demo.contactNumber, passwordHash = studentPasswordHash(s1Demo.idCardNumber)))
        val u2 = userRepository.save(UserEntity(name = "王芳", email = EmailAddress("wangfang@univ.edu.cn"), role = UserRole.STUDENT, phone = s2Demo.contactNumber, passwordHash = studentPasswordHash(s2Demo.idCardNumber)))
        val u3 = userRepository.save(UserEntity(name = "张伟", email = EmailAddress("zhangwei@univ.edu.cn"), role = UserRole.STUDENT, phone = s3Demo.contactNumber, passwordHash = studentPasswordHash(s3Demo.idCardNumber)))
        val u4 = userRepository.save(UserEntity(name = "陈秀", email = EmailAddress("chenxiu@univ.edu.cn"), role = UserRole.STUDENT, phone = s4Demo.contactNumber, passwordHash = studentPasswordHash(s4Demo.idCardNumber)))
        val s1 = studentRepository.save(StudentEntity(id = u1.id, studentNumber = "S2023001", name = "李明", major = csMajor, enrollmentDate = LocalDate.of(2024, 9, 1), degreeLevel = bachelorDegree, demographics = s1Demo, assignedTeacher = referrer))
        val s2 = studentRepository.save(StudentEntity(id = u2.id, studentNumber = "S2023002", name = "王芳", major = engMajor, enrollmentDate = LocalDate.of(2023, 9, 1), degreeLevel = masterDegree, demographics = s2Demo, assignedTeacher = referrer))
        val s3 = studentRepository.save(StudentEntity(id = u3.id, studentNumber = "2021001", name = "张伟", major = busMajor, enrollmentDate = LocalDate.of(2025, 9, 1), degreeLevel = bachelorDegree, demographics = s3Demo))
        val s4 = studentRepository.save(StudentEntity(id = u4.id, studentNumber = "S2023004", name = "陈秀", major = medMajor, enrollmentDate = LocalDate.of(2022, 9, 1), degreeLevel = phdDegree, demographics = s4Demo))

        val s5Demo = StudentDemographicsEntity(gender = Gender.FEMALE, dateOfBirth = LocalDate.of(2004, 1, 10), ethnicity = han, idCardNumber = "110101200401105555", contactNumber = "13911112222", email = "zhaomin@univ.edu.cn", homeAddress = "上海市徐汇区", emergencyContactName = "赵强", emergencyContactPhone = "13911113333", school = mainSchool)
        val s6Demo = StudentDemographicsEntity(gender = Gender.MALE, dateOfBirth = LocalDate.of(2003, 6, 22), ethnicity = han, idCardNumber = "110101200306226666", contactNumber = "13822223333", email = "liuhai@univ.edu.cn", homeAddress = "广州市越秀区", emergencyContactName = "刘军", emergencyContactPhone = "13822224444", school = mainSchool)
        val s7Demo = StudentDemographicsEntity(gender = Gender.FEMALE, dateOfBirth = LocalDate.of(2005, 9, 5), ethnicity = hui, idCardNumber = "110101200509057777", contactNumber = "13733334444", email = "sunli@univ.edu.cn", homeAddress = "深圳市南山区", emergencyContactName = "孙燕", emergencyContactPhone = "13733335555", school = mainSchool)
        val s8Demo = StudentDemographicsEntity(gender = Gender.MALE, dateOfBirth = LocalDate.of(2004, 11, 18), ethnicity = man, idCardNumber = "110101200411188888", contactNumber = "13644445555", email = "wudong@univ.edu.cn", homeAddress = "成都市武侯区", emergencyContactName = "吴刚", emergencyContactPhone = "13644446666", school = mainSchool)
        val s9Demo = StudentDemographicsEntity(gender = Gender.FEMALE, dateOfBirth = LocalDate.of(2002, 12, 12), ethnicity = han, idCardNumber = "110101200212129999", contactNumber = "13555556666", email = "zhoumei@univ.edu.cn", homeAddress = "武汉市洪山区", emergencyContactName = "周华", emergencyContactPhone = "13555557777", school = mainSchool)
        val s10Demo = StudentDemographicsEntity(gender = Gender.MALE, dateOfBirth = LocalDate.of(2003, 4, 30), ethnicity = han, idCardNumber = "110101200304300000", contactNumber = "13466667777", email = "zhengqi@univ.edu.cn", homeAddress = "杭州市西湖区", emergencyContactName = "郑伟", emergencyContactPhone = "13466668888", school = mainSchool)

        val u5 = userRepository.save(UserEntity(name = "赵敏", email = EmailAddress("zhaomin@univ.edu.cn"), role = UserRole.STUDENT, phone = s5Demo.contactNumber, passwordHash = studentPasswordHash(s5Demo.idCardNumber)))
        val u6 = userRepository.save(UserEntity(name = "刘海", email = EmailAddress("liuhai@univ.edu.cn"), role = UserRole.STUDENT, phone = s6Demo.contactNumber, passwordHash = studentPasswordHash(s6Demo.idCardNumber)))
        val u7 = userRepository.save(UserEntity(name = "孙丽", email = EmailAddress("sunli@univ.edu.cn"), role = UserRole.STUDENT, phone = s7Demo.contactNumber, passwordHash = studentPasswordHash(s7Demo.idCardNumber)))
        val u8 = userRepository.save(UserEntity(name = "吴东", email = EmailAddress("wudong@univ.edu.cn"), role = UserRole.STUDENT, phone = s8Demo.contactNumber, passwordHash = studentPasswordHash(s8Demo.idCardNumber)))
        val u9 = userRepository.save(UserEntity(name = "周梅", email = EmailAddress("zhoumei@univ.edu.cn"), role = UserRole.STUDENT, phone = s9Demo.contactNumber, passwordHash = studentPasswordHash(s9Demo.idCardNumber)))
        val u10 = userRepository.save(UserEntity(name = "郑奇", email = EmailAddress("zhengqi@univ.edu.cn"), role = UserRole.STUDENT, phone = s10Demo.contactNumber, passwordHash = studentPasswordHash(s10Demo.idCardNumber)))
        val s5 = studentRepository.save(StudentEntity(id = u5.id, studentNumber = "S2023005", name = "赵敏", major = medMajor, enrollmentDate = LocalDate.of(2024, 9, 1), degreeLevel = bachelorDegree, demographics = s5Demo, assignedTeacher = referrer))
        val s6 = studentRepository.save(StudentEntity(id = u6.id, studentNumber = "S2023006", name = "刘海", major = csMajor, enrollmentDate = LocalDate.of(2023, 9, 1), degreeLevel = bachelorDegree, demographics = s6Demo, assignedTeacher = referrer))
        val s7 = studentRepository.save(StudentEntity(id = u7.id, studentNumber = "S2023007", name = "孙丽", major = engMajor, enrollmentDate = LocalDate.of(2025, 9, 1), degreeLevel = masterDegree, demographics = s7Demo, assignedTeacher = referrer))
        val s8 = studentRepository.save(StudentEntity(id = u8.id, studentNumber = "S2023008", name = "吴东", major = busMajor, enrollmentDate = LocalDate.of(2022, 9, 1), degreeLevel = bachelorDegree, demographics = s8Demo))
        val s9 = studentRepository.save(StudentEntity(id = u9.id, studentNumber = "S2023009", name = "周梅", major = medMajor, enrollmentDate = LocalDate.of(2024, 9, 1), degreeLevel = otherDegree, demographics = s9Demo))
        val s10 = studentRepository.save(StudentEntity(id = u10.id, studentNumber = "S2023010", name = "郑奇", major = csMajor, enrollmentDate = LocalDate.of(2023, 9, 1), degreeLevel = bachelorDegree, demographics = s10Demo))

        val hp1 = studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s1.id, scidDiagnosis = "重度抑郁症，伴随焦虑症状"))
        val hp2 = studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s2.id))
        val hp3 = studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s3.id, scidDiagnosis = "广泛性焦虑障碍"))
        val hp4 = studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s4.id))

        val rf1 = RiskFlagEntity(name = com.medicalsystem.backend.model.RiskFlagName.SUICIDAL_IDEATION, status = com.medicalsystem.backend.model.FlagStatus.POSITIVE, healthProfile = hp1)
        val rf2 = RiskFlagEntity(name = com.medicalsystem.backend.model.RiskFlagName.SELF_HARM, status = com.medicalsystem.backend.model.FlagStatus.NEGATIVE, healthProfile = hp1)
        hp1.riskFlags.addAll(listOf(rf1, rf2))
        
        val pt1 = PsychometricTestEntity(testType = com.medicalsystem.backend.model.PsychometricTestType.GAD_7, score = 15, maxScore = 21, testDate = LocalDate.now().minusDays(30), healthProfile = hp1)
        val pt2 = PsychometricTestEntity(testType = com.medicalsystem.backend.model.PsychometricTestType.GAD_7, score = 18, maxScore = 21, testDate = LocalDate.now().minusDays(15), healthProfile = hp1)
        val pt3 = PsychometricTestEntity(testType = com.medicalsystem.backend.model.PsychometricTestType.GAD_7, score = 20, maxScore = 21, testDate = LocalDate.now().minusDays(5), healthProfile = hp1)
        val pt4 = PsychometricTestEntity(testType = com.medicalsystem.backend.model.PsychometricTestType.PHQ_9, score = 22, maxScore = 27, testDate = LocalDate.now().minusDays(5), healthProfile = hp1)
        val pt5 = PsychometricTestEntity(testType = com.medicalsystem.backend.model.PsychometricTestType.PSQI, score = 16, maxScore = 21, testDate = LocalDate.now().minusDays(5), healthProfile = hp1)
        hp1.psychometricTests.addAll(listOf(pt1, pt2, pt3, pt4, pt5))
        studentHealthProfileRepository.save(hp1)

        val rf3 = RiskFlagEntity(name = com.medicalsystem.backend.model.RiskFlagName.SUICIDE_ATTEMPT, status = com.medicalsystem.backend.model.FlagStatus.POSITIVE, healthProfile = hp3)
        hp3.riskFlags.add(rf3)
        val pt6 = PsychometricTestEntity(testType = com.medicalsystem.backend.model.PsychometricTestType.GAD_7, score = 12, maxScore = 21, testDate = LocalDate.now().minusDays(10), healthProfile = hp3)
        hp3.psychometricTests.add(pt6)
        studentHealthProfileRepository.save(hp3)

        studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s5.id))
        studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s6.id, scidDiagnosis = "轻度焦虑"))
        studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s7.id))
        studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s8.id, scidDiagnosis = "双相情感障碍"))
        studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s9.id))
        studentHealthProfileRepository.save(StudentHealthProfileEntity(studentId = s10.id, scidDiagnosis = "睡眠障碍"))

        val hosp1 = hospitalRepository.save(HospitalEntity(name = "中南大学湘雅医院", address = "湖南省长沙市开福区湘雅路87号", contactPhone = "0731-84328888"))
        val hosp2 = hospitalRepository.save(HospitalEntity(name = "中南大学湘雅二医院", address = "湖南省长沙市芙蓉区人民中路139号", contactPhone = "0731-85295888"))
        val hosp3 = hospitalRepository.save(HospitalEntity(name = "中南大学湘雅三医院", address = "湖南省长沙市岳麓区桐梓坡路138号", contactPhone = "0731-88618888"))
        val hosp4 = hospitalRepository.save(HospitalEntity(name = "湖南省人民医院", address = "湖南省长沙市芙蓉区解放西路61号", contactPhone = "0731-83929114"))
        val hosp5 = hospitalRepository.save(HospitalEntity(name = "湖南省脑科医院（湖南省第二人民医院）", address = "湖南省长沙市雨花区芙蓉中路三段427号", contactPhone = "0731-85232233"))
        val hospOther = hospitalRepository.save(HospitalEntity(name = "其他医疗机构", address = "其他地区", contactPhone = "0731-80000000"))

        val hospDeptNames = listOf(
            "心理咨询科",
            "精神科",
            "临床心理科",
            "心身医学科",
            "门诊分诊部",
            "急诊科",
            "神经内科",
            "内科",
            "其他科室"
        )
        val allHospitals = listOf(hosp1, hosp2, hosp3, hosp4, hosp5, hospOther)
        allHospitals.forEach { h ->
            hospDeptNames.forEach { deptName ->
                hospitalDepartmentRepository.save(HospitalDepartmentEntity(name = deptName, hospital = h))
            }
        }

        val hosp = hosp1
        val medDept = hospitalDepartmentRepository.findByHospitalIdOrderByIdAsc(hosp.id).find { it.name == "内科" }!!
        val psychDept = hospitalDepartmentRepository.findByHospitalIdOrderByIdAsc(hosp.id).find { it.name == "心理咨询科" }!!

        val triageAdminBase = userRepository.save(UserEntity(name = "张老师", email = EmailAddress("zhang@univ.edu.cn"), role = UserRole.TRIAL_ADMIN, passwordHash = defaultStaffPasswordHash))
        val triageAdmin = trialAdminRepository.save(TrialAdminEntity(userId = triageAdminBase.id, employeeNumber = "TA-00001", hospital = hosp))

        val doctorBase = userRepository.save(UserEntity(name = "李医生", email = EmailAddress("li@univ.edu.cn"), role = UserRole.DOCTOR, passwordHash = defaultStaffPasswordHash))
        val doctor = doctorRepository.save(DoctorEntity(userId = doctorBase.id, employeeNumber = "DOC-00001", department = medDept))

        val doctorWangBase = userRepository.save(UserEntity(name = "王医生", email = EmailAddress("wang@univ.edu.cn"), role = UserRole.DOCTOR, passwordHash = defaultStaffPasswordHash))
        val doctorWang = doctorRepository.save(DoctorEntity(userId = doctorWangBase.id, employeeNumber = "DOC-00002", department = psychDept))

        val headCounsellorBase = userRepository.save(UserEntity(name = "王主任", email = EmailAddress("wang_head@univ.edu.cn"), role = UserRole.HEAD_COUNSELLOR, passwordHash = defaultStaffPasswordHash))
        val scDept = schoolDepartmentJpaRepository.findBySchoolIdOrderByIdAsc(mainSchool.id).find { it.name == "咨询中心" }!!
        val headCounsellor = headCounsellorJpaRepository.save(HeadCounsellorEntity(userId = headCounsellorBase.id, employeeNumber = "HC-00001", schoolId = mainSchool.id, departmentId = scDept.id))

        val adminBase = userRepository.save(UserEntity(name = "系统管理员", email = EmailAddress("admin@univ.edu.cn"), role = UserRole.SYSTEM_ADMIN, status = AccountStatus.ACTIVE, passwordHash = defaultStaffPasswordHash))
        val pendingTeacher = userRepository.save(UserEntity(name = "赵老师", email = EmailAddress("zhao_pending@univ.edu.cn"), role = UserRole.TEACHER, status = AccountStatus.PENDING_APPROVAL, passwordHash = defaultStaffPasswordHash))
        val pendingDoctor = userRepository.save(UserEntity(name = "刘医生", email = EmailAddress("liu_pending@univ.edu.cn"), role = UserRole.DOCTOR, status = AccountStatus.PENDING_APPROVAL, passwordHash = defaultStaffPasswordHash))

        val dest = ReferralDestinationEntity(
            hospital = hosp,
            department = medDept,
            doctor = doctor,
            triageAdmin = triageAdmin,
            transferDate = LocalDate.now().plusDays(2)
        )

        val ref1 = ReferralEntity(
            studentId = s1.id,
            type = ReferralType.INITIAL,
            title = "期中考试后急性焦虑",
            description = "期中考试后出现急性恐慌发作和睡眠剥夺",
            riskLevel = RiskStatus.HIGH,
            status = ReferralStatus.WAITING_FOR_SCHEDULING,
            referredById = referrer.userId,
            createdAt = LocalDateTime.now().minusDays(1),
            destination = dest
        )
        
        val step1 = ReferralStepEntity(
            referral = ref1,
            type = ReferralStepType.INITIATION,
            time = LocalDateTime.now().minusDays(1),
            status = ReferralStepStatus.COMPLETED,
            actorId = referrer.userId
        )
        val step2 = ReferralStepEntity(
            referral = ref1,
            type = ReferralStepType.REVIEW,
            time = LocalDateTime.now().minusHours(20),
            status = ReferralStepStatus.COMPLETED,
            actorId = referrer.userId
        )
        val step3 = ReferralStepEntity(
            referral = ref1,
            type = ReferralStepType.TRIAGE,
            time = LocalDateTime.now().minusHours(18),
            status = ReferralStepStatus.COMPLETED,
            actorId = triageAdmin.userId
        )
        val step4 = ReferralStepEntity(
            referral = ref1,
            type = ReferralStepType.SCHEDULING,
            time = LocalDateTime.now().minusHours(5),
            status = ReferralStepStatus.ACTIVE,
            actorId = triageAdmin.userId
        )
        
        ref1.steps.addAll(listOf(step1, step2, step3, step4))

        val dest2 = ReferralDestinationEntity(
            hospital = hosp,
            department = null,
            doctor = null,
            triageAdmin = null,
            transferDate = null
        )

        val ref2 = ReferralEntity(
            studentId = s2.id,
            type = ReferralType.FOLLOW_UP,
            title = "每周治疗随访",
            description = "情绪持续低落",
            riskLevel = RiskStatus.MEDIUM,
            status = ReferralStatus.AWAITING_TRIAGE,
            referredById = referrer.userId,
            createdAt = LocalDateTime.now().minusDays(2),
            destination = dest2
        )

        val ref2Step1 = ReferralStepEntity(
            referral = ref2,
            type = ReferralStepType.INITIATION,
            time = LocalDateTime.now().minusDays(2),
            status = ReferralStepStatus.COMPLETED,
            actorId = referrer.userId
        )
        val ref2Step2 = ReferralStepEntity(
            referral = ref2,
            type = ReferralStepType.REVIEW,
            time = LocalDateTime.now().minusHours(24),
            status = ReferralStepStatus.COMPLETED,
            actorId = referrer.userId
        )
        val ref2Step3 = ReferralStepEntity(
            referral = ref2,
            type = ReferralStepType.TRIAGE,
            time = LocalDateTime.now().minusHours(12),
            status = ReferralStepStatus.ACTIVE,
            actorId = triageAdmin.userId
        )
        ref2.steps.addAll(listOf(ref2Step1, ref2Step2, ref2Step3))

        referralRepository.saveAll(listOf(ref1, ref2))

        val assignerUser = referrerBase
        val a1 = AssessmentAssignmentEntity(
            student = s1,
            assignedByUser = assignerUser,
            batteryCode = "comprehensive_student_intake_survey",
            status = AssessmentStatus.PENDING,
            assignedAt = LocalDateTime.now().minusDays(1),
            dueDate = LocalDate.now().plusDays(6)
        )
        val a2 = AssessmentAssignmentEntity(
            student = s4,
            assignedByUser = assignerUser,
            batteryCode = "comprehensive_student_intake_survey",
            status = AssessmentStatus.COMPLETED,
            assignedAt = LocalDateTime.now().minusDays(5),
            completedAt = LocalDateTime.now().minusDays(3),
            dueDate = LocalDate.now().plusDays(2),
            answersJson = "{\"gad7_1\":2,\"gad7_2\":2,\"gad7_3\":2,\"gad7_4\":2,\"gad7_5\":2,\"gad7_6\":2,\"gad7_7\":2}"
        )
        val a3 = AssessmentAssignmentEntity(
            student = s2,
            assignedByUser = assignerUser,
            batteryCode = "comprehensive_student_intake_survey",
            status = AssessmentStatus.PENDING,
            assignedAt = LocalDateTime.now(),
            dueDate = LocalDate.now().plusDays(7)
        )
        val a4 = AssessmentAssignmentEntity(
            student = s3,
            assignedByUser = assignerUser,
            batteryCode = "comprehensive_student_intake_survey",
            status = AssessmentStatus.PENDING,
            assignedAt = LocalDateTime.now().minusHours(6),
            dueDate = LocalDate.now().plusDays(10)
        )
        assessmentAssignmentRepository.saveAll(listOf(a1, a2, a3, a4))

        println("Initialized Colleges, Majors, Students, Referrals, and Assessments into the database.")
    }
}
