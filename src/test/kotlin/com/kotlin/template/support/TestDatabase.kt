package com.kotlin.template.support

import io.r2dbc.spi.Row
import java.sql.Timestamp
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.r2dbc.core.DatabaseClient

/** Synchronous fixture/assertion facade over the same reactive driver, for JUnit threads only. */
class TestDatabase(private val db: DatabaseClient) {

    private fun statement(sql: String, args: Array<out Any?>): DatabaseClient.GenericExecuteSpec {
        var index = 0
        val named = sql.replace(Regex("\\?")) { ":p${index++}" }
        check(index == args.size)
        var statement = db.sql(named)
        args.forEachIndexed { i, value -> statement = if (value == null) statement.bindNull("p$i", UUID::class.java)
            else statement.bind("p$i", if (value is Timestamp) value.toInstant().atOffset(java.time.ZoneOffset.UTC) else value) }
        return statement
    }

    fun update(sql: String, vararg args: Any?): Int = statement(sql, args).fetch().rowsUpdated().block(Duration.ofSeconds(15))!!.toInt()

    fun <T : Any> queryForObject(sql: String, type: Class<T>, vararg args: Any?): T? =
        statement(sql, args).map { row, _ -> convert(row.get(0)!!, type) }.one().block(Duration.ofSeconds(15))

    fun <T : Any> queryForList(sql: String, type: Class<T>, vararg args: Any?): List<T> =
        statement(sql, args).map { row, _ -> convert(row.get(0)!!, type) }.all().collectList().block(Duration.ofSeconds(15))!!

    fun <T : Any> query(sql: String, mapper: (TestRow, Int) -> T, vararg args: Any?): List<T> =
        statement(sql, args).map { row, _ -> mapper(TestRow(row), 0) }.all().collectList().block(Duration.ofSeconds(15))!!
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> convert(value: Any, type: Class<T>): T = when {
        type == Int::class.java || type == Int::class.javaObjectType -> (value as Number).toInt()
        type == Long::class.java || type == Long::class.javaObjectType -> (value as Number).toLong()
        else -> value
    } as T
    class TestRow(private val row: Row) {
        fun <T : Any> getObject(name: String, type: Class<T>): T = row.get(name, type)!!
        fun getLong(name: String): Long = (row.get(name) as Number).toLong()
        fun getString(name: String): String = row.get(name, String::class.java)!!
        fun getTimestamp(name: String): Timestamp = Timestamp.from(row.get(name, OffsetDateTime::class.java)!!.toInstant())
    }
}
