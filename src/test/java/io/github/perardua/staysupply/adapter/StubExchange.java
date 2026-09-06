package io.github.perardua.staysupply.adapter;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * 정해진 응답 하나만 돌려주는 WebClient
 * 상태 코드와 본문이 예외 타입으로 어떻게 처리되는지 확인하기 위함
 */
public final class StubExchange {

    private StubExchange() {
    }

    public static WebClient respondingWith(int statusCode, String body) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse
                        .create(HttpStatusCode.valueOf(statusCode))
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()))
                .build();
    }
}
