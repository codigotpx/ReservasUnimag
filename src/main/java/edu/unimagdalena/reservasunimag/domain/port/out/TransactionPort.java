package edu.unimagdalena.reservasunimag.domain.port.out;

import java.util.function.Supplier;

/**
 * Ejecuta un bloque de forma atómica. Una excepción que salga del bloque deshace todo lo que escribió. Existe para
 * que el dominio pueda decir qué debe ser atómico —la reserva y su evento van juntos— sin conocer Spring.
 */
public interface TransactionPort {

    <T> T required(Supplier<T> work);
}
