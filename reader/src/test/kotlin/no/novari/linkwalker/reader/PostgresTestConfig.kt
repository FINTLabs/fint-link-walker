package no.novari.linkwalker.reader

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestConfig {

    @Bean
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:16-alpine")

    @Bean
    fun postgresProperties(postgres: PostgreSQLContainer): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar { registry ->
            registry.add("fint.database.url") { postgres.jdbcUrl }
            registry.add("fint.database.username") { postgres.username }
            registry.add("fint.database.password") { postgres.password }
        }
}
