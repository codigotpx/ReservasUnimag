package edu.unimagdalena.reservasunimag;

import org.springframework.boot.SpringApplication;

public class TestReservasUnimagApplication {

    public static void main(String[] args) {
        SpringApplication.from(ReservasUnimagApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
