package io.github.perardua.staysupply;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
@Profile("!mock")
public class OpenApiConfig {

    @Bean
    public OpenAPI staySupplyOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("stay-supply-aggregator API")
                .version("v1")
                .description("""
                        여러 공급사의 숙박 상품을 하나의 표준 모델로 통합해 검색으로 내보내는 서버.

                        설계 판단과 그 근거는 저장소의 `README.md`와 `docs/`에 있다."""));
    }
}
