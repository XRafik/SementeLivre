package com.sementelivre.backend.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.repository.EstoqueRelatorioRepository;

/**
 * Monta os dados do relatorio de estoque de sementes (RF-07, issue #95).
 *
 * <p>Devolve as linhas ja como texto; quem transforma em PDF ou CSV sao as
 * classes RelatorioPdf e RelatorioCsv.</p>
 */
@Service
public class RelatorioEstoqueService {

    public static final String TITULO = "Relatorio de Estoque de Sementes";

    public static final String[] CABECALHOS = {
            "Produto", "Especie", "Quantidade", "Unidade", "Preco (R$)", "Disponibilidade", "Ultima atualizacao"
    };

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EstoqueRelatorioRepository estoqueRelatorioRepository;

    public RelatorioEstoqueService(EstoqueRelatorioRepository estoqueRelatorioRepository) {
        this.estoqueRelatorioRepository = estoqueRelatorioRepository;
    }

    /**
     * Linhas do relatorio de um proprietario. Os filtros sao opcionais: quando
     * vem nulos, o relatorio traz todos os dados (CDU-25).
     */
    @Transactional(readOnly = true)
    public List<String[]> montarLinhas(UUID proprietarioId, EspecieGeral especie, Disponibilidade disponibilidade) {

        List<Estoque> estoques =
                estoqueRelatorioRepository.findByProprietarioIdOrderByProdutoNomePopular(proprietarioId);

        List<String[]> linhas = new ArrayList<>();

        for (Estoque estoque : estoques) {
            if (passaNosFiltros(estoque, especie, disponibilidade)) {
                linhas.add(montarLinha(estoque));
            }
        }

        return linhas;
    }

    private boolean passaNosFiltros(Estoque estoque, EspecieGeral especie, Disponibilidade disponibilidade) {

        if (especie != null && estoque.getProduto().getEspecie() != especie) {
            return false;
        }

        if (disponibilidade != null && estoque.getDisponibilidade() != disponibilidade) {
            return false;
        }

        return true;
    }

    private String[] montarLinha(Estoque estoque) {
        return new String[] {
                texto(estoque.getProduto().getNomePopular()),
                texto(estoque.getProduto().getEspecie()),
                numero(estoque.getQuantidade()),
                texto(estoque.getTipoPesagem()),
                numero(estoque.getPreco()),
                texto(estoque.getDisponibilidade()),
                data(estoque)
        };
    }

    private String data(Estoque estoque) {
        if (estoque.getDataUltimaAtualizacao() == null) {
            return "-";
        }
        return estoque.getDataUltimaAtualizacao().format(DATA);
    }

    private String texto(Object valor) {
        if (valor == null) {
            return "-";
        }
        return valor.toString();
    }

    // Mostra 2 em vez de 2.0 quando o numero nao tem casa decimal
    private String numero(Double valor) {
        if (valor == null) {
            return "-";
        }
        if (valor == Math.floor(valor)) {
            return String.valueOf(valor.longValue());
        }
        return String.valueOf(valor);
    }
}
