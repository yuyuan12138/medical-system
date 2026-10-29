package com.medicalsystem.backend.controller

import com.medicalsystem.backend.dto.StudentDto
import com.medicalsystem.backend.service.StudentService
import com.medicalsystem.backend.security.CurrentUser
import com.medicalsystem.backend.model.User
import com.medicalsystem.backend.exception.ForbiddenException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.medicalsystem.backend.dto.PsychometricsSummaryDto

import jakarta.validation.Valid

@RestController
@RequestMapping("/api/students")
class StudentController(private val studentService: StudentService) {

    @GetMapping
    fun fetchAllStudents(@CurrentUser user: User?): List<StudentDto> {
        if (user == null) throw ForbiddenException("Authorized user not found")
        return studentService.fetchAllStudents(user)
    }

    @GetMapping("/{id}")
    fun fetchStudentDetails(@PathVariable id: Long, @CurrentUser user: User?): StudentDto {
        if (user == null) throw ForbiddenException("Authorized user not found")
        return studentService.fetchStudentDetails(id, user)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun registerStudent(@Valid @RequestBody dto: StudentDto): StudentDto {
        return studentService.registerStudent(dto)
    }

    @GetMapping("/high-risk")
    fun fetchHighRiskStudents(
        @RequestParam(defaultValue = "false") includeMedium: Boolean,
        @CurrentUser user: User?
    ): List<com.medicalsystem.backend.dto.HighRiskStudentDto> {
        if (user == null) throw ForbiddenException("Authorized user not found")
        return studentService.fetchHighRiskStudents(user, includeMedium)
    }

    @GetMapping("/{id}/psychometrics")
    fun fetchPsychometricSummary(@PathVariable id: Long, @CurrentUser user: User?): com.medicalsystem.backend.dto.PsychometricsSummaryDto {
        if (user == null) throw ForbiddenException("Authorized user not found")
        return studentService.fetchPsychometricSummary(id, user)
    }
}
