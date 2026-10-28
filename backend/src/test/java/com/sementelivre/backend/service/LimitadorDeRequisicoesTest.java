package com.sementelivre.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class LimitadorDeRequisicoesTest {

    private static final Duration JANELA = Duration.ofMinutes(10);

    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final LimitadorDeRequisicoes limitador = new LimitadorDeRequisicoes(relogio);

    @Test
    void aceitaAteOLimiteERecusaODepois() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limitador.tentar("a", 3, JANELA).isEmpty(), "tentativa " + (i + 1));
        }
        assertTrue(limitador.tentar("a", 3, JANELA).isPresent());
    }

    @Test
    void chavesDiferentesContamSeparado() {
        for (int i = 0; i < 3; i++) {
            limitador.tentar("a", 3, JANELA);
        }
        assertTrue(limitador.tentar("b", 3, JANELA).isEmpty());
    }

    @Test
    void esperaEOTempoAteAMaisAntigaSairDaJanela() {
        limitador.tentar("a", 2, JANELA);
        relogio.avancar(Duration.ofMinutes(4));
        limitador.tentar("a", 2, JANELA);

        Optional<Duration> espera = limitador.tentar("a", 2, JANELA);

        assertEquals(Optional.of(Duration.ofMinutes(6)), espera);
    }

    @Test
    void liberaQuandoATentativaMaisAntigaSaiDaJanela() {
        limitador.tentar("a", 2, JANELA);
        relogio.avancar(Duration.ofMinutes(4));
        limitador.tentar("a", 2, JANELA);

        relogio.avancar(Duration.ofMinutes(6));

        assertTrue(limitador.tentar("a", 2, JANELA).isEmpty());
        // a segunda (minuto 4) ainda conta, junto com a de agora
        assertTrue(limitador.tentar("a", 2, JANELA).isPresent());
    }

    @Test
    void tentativaRecusadaNaoOcupaVaga() {
        limitador.tentar("a", 1, JANELA);
        for (int i = 0; i < 5; i++) {
            limitador.tentar("a", 1, JANELA);
        }

        relogio.avancar(JANELA);

        assertTrue(limitador.tentar("a", 1, JANELA).isEmpty());
    }

    @Test
    void tentativasSimultaneasNaoPassamDoLimite() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger aceitas = new AtomicInteger();

        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                largada.await();
                if (limitador.tentar("a", 5, JANELA).isEmpty()) {
                    aceitas.incrementAndGet();
                }
                return null;
            });
        }
        largada.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(5, aceitas.get());
    }

    /** Relógio que o teste avança na mão. */
    // consultar/registrar: contam só o que o chamador registra (ex.: senha errada)

    @Test
    void consultarNaoContaTentativa() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limitador.consultar("a", 1, JANELA).isEmpty());
        }
        limitador.registrar("a", JANELA);
        assertEquals(Optional.of(JANELA), limitador.consultar("a", 1, JANELA));
    }

    @Test
    void registradasSaemDaJanelaEEsquecerZera() {
        limitador.registrar("a", JANELA);
        limitador.registrar("a", JANELA);
        assertTrue(limitador.consultar("a", 2, JANELA).isPresent());

        relogio.avancar(JANELA);
        assertTrue(limitador.consultar("a", 2, JANELA).isEmpty());

        limitador.registrar("a", JANELA);
        limitador.registrar("a", JANELA);
        limitador.esquecer("a");
        assertTrue(limitador.consultar("a", 2, JANELA).isEmpty());
    }

    static class RelogioAjustavel extends Clock {

        private Instant agora = Instant.parse("2026-10-13T12:00:00Z");

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
