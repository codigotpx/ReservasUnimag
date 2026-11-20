package edu.unimagdalena.reservasunimag.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Un {@link RestClient} por módulo externo, cada uno con su URL base y su timeout. El Módulo 3 va sin autenticación;
 * el Módulo 1 lleva HTTP Basic cuando hay credenciales, porque su API real lo exige.
 */
@Configuration(proxyBeanMethods = false)
class HttpClientsConfig {

    @Bean
    RestClient module1RestClient(RestClient.Builder builder, ReservationProperties properties) {
        ReservationProperties.Module1 module1 = properties.module1();
        RestClient.Builder withAuth = builder.clone();
        if (module1.hasCredentials()) {
            withAuth.defaultHeaders(headers ->
                    headers.setBasicAuth(module1.username(), module1.password() == null ? "" : module1.password(),
                            StandardCharsets.UTF_8));
        }
        return build(withAuth, module1.baseUrl(), module1.timeout());
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
