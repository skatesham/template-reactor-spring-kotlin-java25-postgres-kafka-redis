package com.kotlin.template.identity.application.usecase.currentuser

import com.kotlin.template.identity.application.exception.UserNotFound
import com.kotlin.template.identity.application.result.UserDetails
import com.kotlin.template.identity.domain.repository.UserRepository
import java.util.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CurrentUser(private val users: UserRepository) {
    @Transactional(readOnly = true)
    fun execute(id: UUID): UserDetails = UserDetails.from(users.findById(id) ?: throw UserNotFound())
}
