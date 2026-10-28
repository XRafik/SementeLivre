package com.sementelivre.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Janela deslizante em memória: no máximo {@code limite} tentativas por chave
 * dentro de {@code janela}.
 *
 * Vale para uma instância só do backend; com várias réplicas cada uma conta
 * separado e o limite efetivo multiplica.
 */
public class LimitadorDeRequisicoes {

    // Acima disso as chaves que já saíram da janela são descartadas
    private static final int LIMPAR_ACIMA_DE = 10_000;

    private final Clock relogio;
    private final ConcurrentHashMap<String, Deque<Instant>> tentativas = new ConcurrentHashMap<>();

    public LimitadorDeRequisicoes(Clock relogio) {
        this.relogio = relogio;
    }

    /**
     * Registra a tentativa se ainda couber no limite.
     *
     * @return vazio quando a tentativa foi aceita; senão, quanto falta para a
     *         mais antiga sair da janela e liberar uma vaga
     */
    public Optional<Duration> tentar(String chave, int limite, Duration janela) {

        Instant agora = relogio.instant();
        Instant inicioDaJanela = agora.minus(janela);
        AtomicReference<Duration> espera = new AtomicReference<>();

        // compute trava a chave: duas requisições simultâneas não passam juntas pela contagem
        tentativas.compute(chave, (k, registro) -> {
            Deque<Instant> lista = registro != null ? registro : new ArrayDeque<>();
            while (!lista.isEmpty() && !lista.peekFirst().isAfter(inicioDaJanela)) {
                lista.pollFirst();
            }
            if (lista.size() >= limite) {
                espera.set(Duration.between(agora, lista.peekFirst().plus(janela)));
            } else {
                lista.addLast(agora);
            }
            return lista;
        });

        if (tentativas.size() > LIMPAR_ACIMA_DE) {
            descartarExpiradas(inicioDaJanela);
        }

        return Optional.ofNullable(espera.get());
    }

    /**
     * Como {@link #tentar}, mas só consulta: não conta tentativa. Para limitar
     * falhas (senha errada), registradas à parte com {@link #registrar}.
     */
    public Optional<Duration> consultar(String chave, int limite, Duration janela) {

        Instant agora = relogio.instant();
        Instant inicioDaJanela = agora.minus(janela);
        AtomicReference<Duration> espera = new AtomicReference<>();

        tentativas.computeIfPresent(chave, (k, lista) -> {
            while (!lista.isEmpty() && !lista.peekFirst().isAfter(inicioDaJanela)) {
                lista.pollFirst();
            }
            if (lista.size() >= limite) {
                espera.set(Duration.between(agora, lista.peekFirst().plus(janela)));
            }
            return lista.isEmpty() ? null : lista;
        });

        return Optional.ofNullable(espera.get());
    }

    /** Conta uma tentativa sem conferir o limite. */
    public void registrar(String chave, Duration janela) {
        Instant agora = relogio.instant();
        tentativas.compute(chave, (k, registro) -> {
            Deque<Instant> lista = registro != null ? registro : new ArrayDeque<>();
            lista.addLast(agora);
            return lista;
        });

        if (tentativas.size() > LIMPAR_ACIMA_DE) {
            descartarExpiradas(agora.minus(janela));
        }
    }

    /** Zera a contagem da chave (ex.: login certo depois de senhas erradas). */
    public void esquecer(String chave) {
        tentativas.remove(chave);
    }

    private void descartarExpiradas(Instant inicioDaJanela) {
        // computeIfPresent usa o mesmo lock da chave; devolver null remove a entrada
        tentativas.keySet().forEach(chave -> tentativas.computeIfPresent(chave, (k, lista) ->
                lista.isEmpty() || !lista.peekLast().isAfter(inicioDaJanela) ? null : lista));
    }
}
