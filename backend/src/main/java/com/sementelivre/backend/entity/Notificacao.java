package com.sementelivre.backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Notificação enviada a um proprietário (CDU-26, requisitos RF-05 e RF-08).
 *
 * <p>É gerada automaticamente pelo sistema quando um pedido é concluído, e
 * fica guardada com o estado "lida"/"não lida" para montar o histórico de
 * notificações do proprietário.</p>
 *
 * <p>Mapeia a tabela {@code notificacao_t} descrita em
 * docs/modelo-dados/Modelo-Conceitual-Banco-Dados.md.</p>
 */
@Entity
@Table(name = "notificacao_t")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Notificacao {

    // @GeneratedValue(UUID) deixa o Hibernate gerar o identificador, do mesmo
    // jeito que Comunidade e Propriedade já fazem.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String titulo;

    // columnDefinition = "TEXT" porque a mensagem não tem tamanho máximo no
    // modelo de dados; sem isso o Hibernate assumiria VARCHAR(255).
    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    // @Builder.Default é obrigatório aqui: sem ele o Lombok ignoraria o valor
    // inicial "false" quando o objeto fosse criado pelo builder, e o campo
    // chegaria nulo no banco (que é NOT NULL).
    @Builder.Default
    @Column(nullable = false)
    private boolean lida = false;

    // @CreationTimestamp: o Hibernate preenche a data no momento do INSERT.
    // updatable = false garante que ela nunca seja sobrescrita em um UPDATE.
    @CreationTimestamp
    @Column(name = "data_geracao", nullable = false, updatable = false)
    private LocalDateTime dataGeracao;

    // Fica nula enquanto a notificação não for lida (ver marcarComoLida()).
    @Column(name = "data_leitura")
    private LocalDateTime dataLeitura;

    // Relacionamento obrigatório: toda notificação pertence a um proprietário.
    // FetchType.LAZY evita que o proprietário seja carregado junto em toda
    // listagem de notificações (o padrão de @ManyToOne é EAGER, que geraria um
    // SELECT extra por linha). optional = false diz ao Hibernate que a FK é
    // NOT NULL, permitindo que ele otimize a consulta.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proprietario_id", nullable = false)
    private Proprietario proprietario;

    // Relacionamento opcional: nem toda notificação vem de um pedido, por isso
    // a FK aceita nulo (no modelo de dados: ON DELETE SET NULL).
    // ATENÇÃO: Pedido ainda é um placeholder (ver a classe Pedido, task #35).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_relacionado_id")
    private Pedido pedidoRelacionado;

    /**
     * Marca a notificação como lida e registra o instante da leitura.
     *
     * <p>Os campos {@code lida} e {@code dataLeitura} sempre andam juntos —
     * concentrar essa mudança em um único método evita que algum ponto do
     * sistema atualize um e esqueça o outro. Chamadas repetidas não alteram a
     * data original.</p>
     */
    public void marcarComoLida() {
        if (!this.lida) {
            this.lida = true;
            this.dataLeitura = LocalDateTime.now();
        }
    }

    // Volta para nao lida e apaga a data de leitura junto
    public void marcarComoNaoLida() {
        this.lida = false;
        this.dataLeitura = null;
    }
}
