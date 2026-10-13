package com.sementelivre.backend.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quem recebe o pedido (MER v2.0: PEDIDO_T }o--|| COMPRADOR_T).
 *
 * Fica fora da heranca de Pessoa: pessoa_t exige documento unico, e no
 * registro do pedido o produtor so informa nome e telefone do comprador.
 */
@Entity
@Table(name = "comprador_t")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comprador {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 20)
    private String telefone;

    // Contato de quem pediu pelo site (V10)
    @Column(length = 254)
    private String email;
}
