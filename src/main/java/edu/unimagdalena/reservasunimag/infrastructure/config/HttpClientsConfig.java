package edu.unimagdalena.reservasunimag.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** Un {@link RestClient} por módulo externo, cada uno con su URL base y su timeout. Sin autenticación entre módulos. */
@Configuration(proxyBeanMethods = false)
class HttpClientsConfig {

    @Bean
    RestClient module1RestClient(RestClient.Builder builder, ReservationProperties properties) {
        return build(builder, properties.module1().baseUrl(), properties.module1().timeout());
    }

    @Bean
    RestClient module3RestClient(RestClient.Builder builder, ReservationProperties properties) {
        return build(builder, properties.module3().baseUrl(), properties.module3().timeout());
    }

    private static RestClient build(RestClient.Builder builder, URI baseUrl, Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return builder.clone()
                .baseUrl(baseUrl.toString())
                .requestFactory(requestFactory)
                .build();
    }
}
