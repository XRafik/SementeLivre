package com.sementelivre.backend.entity;

import com.sementelivre.backend.entity.enums.OrigemMovimentacao;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Uma linha do livro-razão de um estoque.
 *
 * Não guarda o saldo: guarda o quanto mudou e o saldo antes/depois. Assim o
 * histórico continua auditável mesmo depois que o estoque atual muda várias
 * vezes, e dá para reconstruir a linha do tempo da TELA 09.
 */
@Entity
@Table(name = "movimentacao_t")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estoque_id", nullable = false)
    private Estoque estoque;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoMovimentacao tipo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "origem", nullable = false, length = 30)
    private OrigemMovimentacao origem;

    /** Quanto entrou ou saiu. Sempre positivo; o sinal vem de {@link #tipo}. */
    @Column(nullable = false)
    private Double quantidade;

    @Column(name = "saldo_anterior", nullable = false)
    private Double saldoAnterior;

    @Column(name = "saldo_posterior", nullable = false)
    private Double saldoPosterior;

    @Column(length = 255)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "data_movimentacao", nullable = false)
    private LocalDateTime dataMovimentacao;

    /**
     * A movimentação aumentou o saldo?
     *
     * Derivado dos saldos, e não do tipo: CORRECAO pode corrigir para cima ou
     * para baixo, e o tipo sozinho não diz qual dos dois aconteceu.
     */
    public boolean isAumento() {
        return saldoPosterior >= saldoAnterior;
    }

    /** Saldo que passou a valer depois desta movimentação. */
    public double saldoResultante() {
        return saldoPosterior;
    }
}
