package com.sementelivre.backend.service;

import com.sementelivre.backend.dto.EstoqueRequestDTO;
import com.sementelivre.backend.dto.EstoqueResponseDTO;
import com.sementelivre.backend.dto.MovimentacaoRequestDTO;
import com.sementelivre.backend.dto.MovimentacaoResponseDTO;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Movimentacao;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.OrigemMovimentacao;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import com.sementelivre.backend.repository.MovimentacaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Estoque e o livro-razão que o acompanha.
 *
 * Toda alteração de saldo grava uma linha em movimentacao_t. A tabela
 * estoque_t continua guardando o saldo atual (é o que o catálogo lê), mas o
 * histórico vive na movimentacao_t — é o que a TELA 09 da especificação
 * exige e o que o schema anterior não tinha.
 */
@Service
public class EstoqueService {

    private final EstoqueRepository estoqueRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    public EstoqueService(
            EstoqueRepository estoqueRepository,
            ProdutoRepository produtoRepository,
            MovimentacaoRepository movimentacaoRepository) {

        this.estoqueRepository = estoqueRepository;
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    // CREATE
    @Transactional
    public EstoqueResponseDTO criar(EstoqueRequestDTO dto) {

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto não encontrado: " + dto.produtoId()
                ));

        Proprietario proprietario = new Proprietario();
        proprietario.setId(dto.proprietarioId());

        Estoque estoque = Estoque.builder()
                .proprietario(proprietario)
                .produto(produto)
                .descricao(dto.descricao())
                .preco(dto.preco())
                .quantidade(dto.quantidade())
                .tipoPesagem(dto.tipoPesagem())
                .disponibilidade(dto.disponibilidade())
                .aceitaNegociacao(dto.aceitaNegociacao() == null || dto.aceitaNegociacao())
                .tipoMovimentacao(dto.tipoMovimentacao())
                .dataMovimentacao(LocalDateTime.now())
                .dataUltimaAtualizacao(LocalDateTime.now())
                .build();

        Estoque salvo = estoqueRepository.save(estoque);

        // O primeiro lançamento: saldo nasce em zero e vai para o que foi informado.
        registrarLancamento(
                salvo,
                dto.tipoMovimentacao(),
                OrigemMovimentacao.CADASTRO,
                dto.quantidade(),
                0,
                dto.quantidade(),
                "Estoque cadastrado"
        );

        return toResponseDTO(salvo);
    }

    // READ - todos
    public List<EstoqueResponseDTO> listarTodos() {
        return estoqueRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public List<EstoqueResponseDTO> listarPorProprietario(UUID proprietarioId) {
        return estoqueRepository.findByProprietarioId(proprietarioId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /** Usado no @PreAuthorize do controller: o estoque existe e é desse proprietário. */
    public boolean ehDoProprietario(UUID estoqueId, UUID proprietarioId) {
        return estoqueRepository.existsByIdAndProprietarioId(estoqueId, proprietarioId);
    }

    // READ - por ID
    public EstoqueResponseDTO buscarPorId(UUID id) {
        Estoque estoque = buscarEntidadePorId(id);

        return toResponseDTO(estoque);
    }

    // UPDATE
    @Transactional
    public EstoqueResponseDTO atualizar(UUID id, EstoqueRequestDTO dto) {

        Estoque estoque = buscarEntidadePorId(id);

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto não encontrado: " + dto.produtoId()
                ));

        Proprietario proprietario = new Proprietario();
        proprietario.setId(dto.proprietarioId());

        double saldoAnterior = estoque.getQuantidade();

        estoque.setProprietario(proprietario);
        estoque.setProduto(produto);
        estoque.setDescricao(dto.descricao());
        estoque.setPreco(dto.preco());
        estoque.setQuantidade(dto.quantidade());
        estoque.setTipoPesagem(dto.tipoPesagem());
        estoque.setDisponibilidade(dto.disponibilidade());
        if (dto.aceitaNegociacao() != null) {
            estoque.setAceitaNegociacao(dto.aceitaNegociacao());
        }
        estoque.setTipoMovimentacao(dto.tipoMovimentacao());
        estoque.setDataMovimentacao(LocalDateTime.now());
        estoque.setDataUltimaAtualizacao(LocalDateTime.now());

        Estoque atualizado = estoqueRepository.save(estoque);

        // Só vira linha no histórico se o saldo mexeu; mudar só a descrição ou o
        // preço não é movimentação e não deve poluir a linha do tempo.
        // A descrição do DTO é a da oferta (vitrine), não o motivo do ajuste.
        if (Double.compare(saldoAnterior, dto.quantidade()) != 0) {
            registrarLancamento(
                    atualizado,
                    tipoParaAjuste(dto.tipoMovimentacao(), saldoAnterior, dto.quantidade()),
                    OrigemMovimentacao.AJUSTE_MANUAL,
                    Math.abs(dto.quantidade() - saldoAnterior),
                    saldoAnterior,
                    dto.quantidade(),
                    "Ajuste manual de quantidade"
            );
        }

        return toResponseDTO(atualizado);
    }

    // DELETE
    public void excluir(UUID id) {
        Estoque estoque = buscarEntidadePorId(id);

        estoqueRepository.delete(estoque);
    }

    /**
     * Lançamento avulso de entrada ou saída, sem reenviar o estoque inteiro.
     *
     * É o atalho que a tela de histórico usa ("Nova movimentação"): o produtor
     * diz quanto entrou ou saiu e o saldo é ajustado na mesma transação.
     */
    @Transactional
    public MovimentacaoResponseDTO registrarMovimentacao(
            UUID estoqueId,
            MovimentacaoRequestDTO dto) {

        Estoque estoque = buscarEntidadePorId(estoqueId);

        if (dto.tipo() == TipoMovimentacao.CORRECAO || dto.tipo() == TipoMovimentacao.ZERAMENTO) {
            throw new IllegalArgumentException(
                    "Correção e zeramento são feitos pela edição do estoque (PUT /estoques/"
                            + estoqueId + "), que registra o saldo anterior e o novo."
            );
        }

        if (dto.quantidade() == null || dto.quantidade() <= 0) {
            throw new IllegalArgumentException("A quantidade da movimentação deve ser maior que zero.");
        }

        boolean entrada = dto.tipo() == TipoMovimentacao.ENTRADA;

        double saldoAnterior = estoque.getQuantidade();
        double saldoPosterior = entrada
                ? saldoAnterior + dto.quantidade()
                : saldoAnterior - dto.quantidade();

        // Impede saldo negativo: o CHECK do banco estouraria com 500 genérico.
        if (saldoPosterior < 0) {
            throw new IllegalArgumentException(
                    "Saldo insuficiente: o estoque tem " + saldoAnterior + " e a saída pediu " + dto.quantidade() + "."
            );
        }

        estoque.setQuantidade(saldoPosterior);
        estoque.setTipoMovimentacao(dto.tipo());
        estoque.setDataMovimentacao(LocalDateTime.now());
        estoque.setDataUltimaAtualizacao(LocalDateTime.now());
        estoqueRepository.save(estoque);

        Movimentacao lancamento = registrarLancamento(
                estoque,
                dto.tipo(),
                OrigemMovimentacao.AJUSTE_MANUAL,
                dto.quantidade(),
                saldoAnterior,
                saldoPosterior,
                dto.descricao()
        );

        return toMovimentacaoDTO(lancamento);
    }

    // READ - histórico de um estoque
    @Transactional(readOnly = true)
    public Page<MovimentacaoResponseDTO> historico(UUID estoqueId, Pageable pageable) {
        // Confere a existência e a permissão antes de paginar: sem isso, um id
        // inexistente devolveria uma página vazia em vez de 404.
        buscarEntidadePorId(estoqueId);

        return movimentacaoRepository.buscarPorEstoque(estoqueId, pageable)
                .map(this::toMovimentacaoDTO);
    }

    /**
     * Lançamento de um pedido no histórico (#185). Quem chama já travou a linha
     * e mudou o saldo; sem este registro o histórico deixa de bater com o
     * estoque a cada pedido registrado, cancelado ou excluído.
     */
    @Transactional
    public void registrarLancamentoDePedido(
            Estoque estoque,
            TipoMovimentacao tipo,
            OrigemMovimentacao origem,
            double saldoAnterior,
            String descricao) {

        registrarLancamento(
                estoque,
                tipo,
                origem,
                Math.abs(estoque.getQuantidade() - saldoAnterior),
                saldoAnterior,
                estoque.getQuantidade(),
                descricao
        );
    }

    private TipoMovimentacao tipoParaAjuste(
            TipoMovimentacao informado,
            double saldoAnterior,
            double saldoNovo) {

        boolean aumentou = saldoNovo > saldoAnterior;

        // Um tipo explícito no formulário vale mais que a inferência, desde que
        // ande no mesmo sentido do saldo. O app reenvia o tipo da última
        // movimentação, e uma "saída" que aumentou o saldo ficava no histórico.
        if (informado != null && combinaComOSentido(informado, aumentou, saldoNovo)) {
            return informado;
        }

        if (aumentou) {
            return TipoMovimentacao.ENTRADA;
        }
        return saldoNovo == 0 ? TipoMovimentacao.ZERAMENTO : TipoMovimentacao.CORRECAO;
    }

    private static boolean combinaComOSentido(TipoMovimentacao tipo, boolean aumentou, double saldoNovo) {
        return switch (tipo) {
            case ENTRADA -> aumentou;
            case SAIDA_VENDA, SAIDA_TROCA, SAIDA_DOACAO -> !aumentou;
            case ZERAMENTO -> saldoNovo == 0;
            case CORRECAO -> true;
        };
    }

    private Movimentacao registrarLancamento(
            Estoque estoque,
            TipoMovimentacao tipo,
            OrigemMovimentacao origem,
            double quantidade,
            double saldoAnterior,
            double saldoPosterior,
            String descricao) {

        Movimentacao movimentacao = Movimentacao.builder()
                .estoque(estoque)
                .tipo(tipo)
                .origem(origem)
                .quantidade(quantidade)
                .saldoAnterior(saldoAnterior)
                .saldoPosterior(saldoPosterior)
                .descricao(descricao)
                .usuario(usuarioAtual())
                .dataMovimentacao(LocalDateTime.now())
                .build();

        return movimentacaoRepository.save(movimentacao);
    }

    /**
     * Usuário do token, ou null em operações de sistema. Fica null em vez de
     * estourar para que migração e seed puedan gravar movimentações sem sessão.
     */
    private Usuario usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }

        return null;
    }

    private Estoque buscarEntidadePorId(UUID id) {
        return estoqueRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Estoque não encontrado: " + id
                ));
    }

    private EstoqueResponseDTO toResponseDTO(Estoque estoque) {

        return new EstoqueResponseDTO(
                estoque.getId(),
                estoque.getProprietario().getId(),
                estoque.getProduto().getId(),
                estoque.getDescricao(),
                estoque.getPreco(),
                estoque.getQuantidade(),
                estoque.getTipoPesagem(),
                estoque.getDisponibilidade(),
                estoque.getTipoMovimentacao(),
                estoque.getDataMovimentacao(),
                estoque.getDataUltimaAtualizacao(),
                estoque.isAceitaNegociacao()
        );
    }

    private MovimentacaoResponseDTO toMovimentacaoDTO(Movimentacao m) {
        Usuario usuario = m.getUsuario();

        return new MovimentacaoResponseDTO(
                m.getId(),
                m.getEstoque().getId(),
                m.getTipo(),
                m.getOrigem(),
                m.getQuantidade(),
                m.getSaldoAnterior(),
                m.getSaldoPosterior(),
                m.isAumento(),
                m.getDescricao(),
                usuario != null ? usuario.getPessoa().getNome() : null,
                m.getDataMovimentacao()
        );
    }
}
