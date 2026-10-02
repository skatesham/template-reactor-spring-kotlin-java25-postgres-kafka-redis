package com.kotlin.template.identity.application.port

import com.kotlin.template.identity.domain.model.User
import com.kotlin.template.identity.application.result.AccessToken

interface AccessTokenIssuer {
    fun issue(user: User): AccessToken
}
