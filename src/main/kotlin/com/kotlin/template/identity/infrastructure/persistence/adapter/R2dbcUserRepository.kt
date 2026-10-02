package com.kotlin.template.identity.infrastructure.persistence.adapter

import com.kotlin.template.identity.application.exception.EmailAlreadyRegistered
import com.kotlin.template.identity.application.port.UserRepository
import com.kotlin.template.identity.domain.model.Role
import com.kotlin.template.identity.domain.model.User
import io.r2dbc.postgresql.api.PostgresqlException
import java.util.UUID
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
class R2dbcUserRepository(private val db: DatabaseClient) : UserRepository {

    override fun findByEmail(email: String) = find("email", email)

    override fun findById(id: UUID) = find("id", id)

    private fun find(column: String, value: Any): Mono<User> = db.sql("SELECT * FROM users WHERE $column=:value")
        .bind("value", value).map { row, _ ->
            User(row.get("id", UUID::class.java)!!, row.get("name", String::class.java)!!,
                row.get("email", String::class.java)!!, row.get("password_hash", String::class.java)!!, setOf(Role.USER))
        }.one().flatMap { user ->
            db.sql("SELECT role_name FROM user_roles WHERE user_id=:id").bind("id", user.id)
                .map { row, _ -> Role.valueOf(row.get("role_name", String::class.java)!!) }.all().collectList()
                .map { roles -> User(user.id, user.name, user.email, user.passwordHash, roles.toSet()) }
        }

    override fun create(user: User): Mono<User> = db.sql(
        "INSERT INTO users(id, name, email, password_hash) VALUES (:id, :name, :email, :hash)"
    ).bind("id", user.id).bind("name", user.name).bind("email", user.email).bind("hash", user.passwordHash)
        .fetch().rowsUpdated().thenMany(Flux.fromIterable(user.roles).concatMap { role ->
            db.sql("INSERT INTO user_roles(user_id, role_name) VALUES (:id, :role)").bind("id", user.id)
                .bind("role", role.name).fetch().rowsUpdated()
        }).then(Mono.just(user)).onErrorMap { error ->
            if (generateSequence(error) { it.cause }.filterIsInstance<PostgresqlException>()
                .any { it.errorDetails.constraintName.orElse(null) == "uk_users_email" }) EmailAlreadyRegistered() else error
        }
}
