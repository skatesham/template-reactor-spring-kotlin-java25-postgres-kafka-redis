package com.kotlin.template.customer

import com.kotlin.template.TestcontainersConfiguration
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    properties = [
        "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.datasource.password=test",
        "spring.docker.compose.enabled=false",
        "app.customer.outbox.poll-ms=50",
    ]
)
class CustomerScheduledIntegrationTests {
    @Autowired
    lateinit var mvc: MockMvc
    @Autowired
    lateinit var jdbc: JdbcTemplate
    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `scheduler publishes REST mutations to both consumers without manual intervention`() {
        val owner = UUID.randomUUID()
        val auth = jwt().jwt { it.subject(owner.toString()) }
        val response = mvc.perform(
            post("/api/customers").with(auth).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Synthetic Customer","email":"scheduled@example.com"}""")
        )
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = UUID.fromString(mapper.readTree(response)["id"].asString())
        mvc.perform(
            put("/api/customers/$id").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Scheduled Update","email":"scheduled-updated@example.com","revision":1}""")
        )
            .andExpect(status().isOk)
        mvc.perform(delete("/api/customers/$id?revision=2").with(auth)).andExpect(status().isNoContent)
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline && !(count("customer_audit", id) == 3 && count(
                "customer_notifications",
                id
            ) == 3)
        ) Thread.sleep(50)
        assertEquals(3, count("customer_audit", id)); assertEquals(3, count("customer_notifications", id))
        assertEquals(
            3,
            jdbc.queryForObject(
                "SELECT count(*) FROM customer_outbox WHERE customer_id=? AND status='PUBLISHED'",
                Int::class.java,
                id
            )
        )
        mvc.perform(get("/api/notifications").with(auth)).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
    }

    private fun count(table: String, id: UUID) =
        jdbc.queryForObject("SELECT count(*) FROM $table WHERE customer_id=?", Int::class.java, id) ?: 0
}
