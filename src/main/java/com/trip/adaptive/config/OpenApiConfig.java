package com.trip.adaptive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI api() {
    return new OpenAPI()
        .info(new Info().title("行迹应变 API").description("多人出行动态规划与中断恢复平台").version("1.0.0"));
  }
}
