package com.sementelivre.backend.integration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sementelivre.backend.DatabaseFixture;
import com.sementelivre.backend.dto.EstoqueRequestDTO;
import com.sementelivre.backend.dto.ItemPedidoRequestDTO;
import com.sementelivre.backend.dto.PedidoRequestDTO;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.FormatoProduto;
import com.sementelivre.backend.entity.enums.Pesagem;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.entity.enums.TipoProduto;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.service.EstoqueService;

import jakarta.persistence.EntityManager;

final class CrossDomainFixture {

    private CrossDomainFixture() {
    }

    static Scenario create(
            EntityManager entityManager,
            ProdutoRepository produtoRepository,
            EstoqueService estoqueService,
            double estoqueInicial) {

        String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Proprietario proprietario = DatabaseFixture.persistProprietario(entityManager, sufixo);

        // Pedido ainda referencia usuario_t: o solicitante e a conta de outro proprietario.
        String sufixoSolicitante = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Proprietario solicitante = DatabaseFixture.persistProprietario(entityManager, sufixoSolicitante);
        Usuario usuario = DatabaseFixture.persistUsuario(entityManager, solicitante);

        Produto produto = produtoRepository.saveAndFlush(Produto.builder()
                .nomePopular("Produto Cross Domain " + sufixo)
                .nomeCientifico("Zea mays")
                .urlFoto("https://example.com/produto.png")
                .tipo(TipoProduto.CEREAL)
                .especie(EspecieGeral.MILHO)
                .formato(FormatoProduto.SEMENTE)
                .dataInclusao(LocalDateTime.now())
                .dataUltimaAlteracao(LocalDateTime.now())
                .build());

        estoqueService.criar(new EstoqueRequestDTO(
                proprietario.getId(),
                produto.getId(),
                "Estoque Cross Domain",
                10.0,
                estoqueInicial,
                Pesagem.KG,
                Disponibilidade.PARA_VENDA,
                TipoMovimentacao.ENTRADA));

        return new Scenario(proprietario, usuario, produto);
    }

    static PedidoRequestDTO pedido(Scenario scenario, double quantidade) {
        return new PedidoRequestDTO(
                TipoPedido.VENDA,
                "Pedido Cross Domain",
                scenario.usuario().getId(),
                scenario.proprietario().getId(),
                List.of(new ItemPedidoRequestDTO(scenario.produto().getId(), quantidade, 10.0)),
                null);
    }

    record Scenario(Proprietario proprietario, Usuario usuario, Produto produto) {
    }
}
