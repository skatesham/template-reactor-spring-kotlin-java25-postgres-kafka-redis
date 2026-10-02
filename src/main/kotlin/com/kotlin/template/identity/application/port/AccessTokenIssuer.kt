package com.kotlin.template.identity.application.port

import com.kotlin.template.identity.domain.model.User

interface AccessTokenIssuer {
    fun issue(user: User): AccessToken
}
