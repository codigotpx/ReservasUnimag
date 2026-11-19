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
        // HTTP/1.1 a propósito: con cuerpo, el cliente del JDK intenta subir a h2c y varios servidores lo rechazan.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return builder.clone()
                .baseUrl(baseUrl.toString())
                .requestFactory(requestFactory)
                .build();
    }
}
