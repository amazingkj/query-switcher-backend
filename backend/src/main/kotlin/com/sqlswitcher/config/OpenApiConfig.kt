package com.sqlswitcher.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("SQL Switcher API")
                    .description("A RESTful API for converting SQL queries between different database dialects (MySQL, PostgreSQL, Oracle, Tibero)")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("SQL Switcher Team")
                            .email("support@sqlswitcher.com")
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:8080")
                        .description("Development server"),
                    Server()
                        .url("https://api.sqlswitcher.com")
                        .description("Production server")
                )
            )
    }
}
