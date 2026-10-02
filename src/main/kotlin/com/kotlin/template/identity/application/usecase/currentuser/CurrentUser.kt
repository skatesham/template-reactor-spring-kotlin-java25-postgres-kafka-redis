package com.kotlin.template.identity.application.usecase.currentuser

import com.kotlin.template.identity.application.exception.UserNotFound
import com.kotlin.template.identity.application.port.UserRepository
import com.kotlin.template.identity.application.result.UserDetails
import java.util.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class CurrentUser(private val users: UserRepository) {

    @Transactional(readOnly = true)
    fun execute(id: UUID): Mono<UserDetails> = users.findById(id).switchIfEmpty(Mono.error(UserNotFound())).map(UserDetails::from)
}
