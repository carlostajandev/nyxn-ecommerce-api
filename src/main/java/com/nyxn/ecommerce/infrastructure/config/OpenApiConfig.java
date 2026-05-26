package com.nyxn.ecommerce.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("NYXN E-Commerce API")
                .description("RESTful API for NYXN e-commerce platform — Hexagonal Architecture")
                .version("1.0.0")
                .contact(new Contact().name("NYXN Engineering").email("engineering@nyxn.com"))
                .license(new License().name("Private").url("https://nyxn.com")))
        .servers(List.of(new Server().url("/").description("Current environment")));
  }
}
