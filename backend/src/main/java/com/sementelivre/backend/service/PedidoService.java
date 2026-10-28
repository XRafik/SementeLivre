package com.sementelivre.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sementelivre.backend.dto.CompradorDTO;
import com.sementelivre.backend.dto.ItemPedidoRequestDTO;
import com.sementelivre.backend.dto.ItemPedidoResponseDTO;
import com.sementelivre.backend.dto.PedidoFiltroDTO;
import com.sementelivre.backend.dto.PedidoPublicoRequestDTO;
import com.sementelivre.backend.dto.PedidoPublicoResponseDTO;
import com.sementelivre.backend.dto.PedidoRequestDTO;
import com.sementelivre.backend.dto.PedidoResponseDTO;
import com.sementelivre.backend.dto.PedidoUpdateDTO;
import com.sementelivre.backend.entity.Comprador;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.OrigemMovimentacao;
import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoMovimentacao;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.PedidoRepository;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.exception.EstoqueInsuficienteException;
import com.sementelivre.backend.exception.NegociacaoNaoPermitidaException;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import com.sementelivre.backend.exception.TransicaoStatusInvalidaException;
import com.sementelivre.backend.repository.UsuarioRepository;

/**
 * Regras de negocio do pedido (issue #67).
 *
 * Fila de pedidos (#183): o estoque so baixa quando o produtor aceita.
 *   - criar/alterar  : so confere se o saldo atual cobre o pedido; nasce PENDENTE, na fila
 *   - confirmar      : baixa o estoque e recusa os PENDENTE do mesmo produto que nao cabem mais
 *   - cancelar       : devolve o estoque se o pedido estava CONFIRMADO
 *   - excluir        : idem (CDU-14)
 *
 * Sem reserva no registro, quem pede tudo de ma-fe nao trava o estoque dos
 * outros: os pedidos esperam na fila e o produtor escolhe quem atender.
 *
 * Um segundo cancelamento e barrado pela transicao de status, o que evita
 * devolver o estoque duas vezes. As operacoes que mudam o pedido travam a
 * linha dele (buscarParaAtualizacao). A confirmacao trava antes as linhas de
 * estoque: duas confirmacoes do mesmo produto entram em fila no estoque em
 * vez de uma travar o pedido que a outra quer recusar (deadlock).
 */
@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final EstoqueRepository estoqueRepository;
    private final EstoqueService estoqueService;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;
    private final ApplicationEventPublisher eventos;

    // Ofertas que aparecem no catálogo público e o tipo de pedido que cada uma gera
    private static final Map<Disponibilidade, TipoPedido> TIPO_POR_DISPONIBILIDADE_PUBLICA = Map.of(
            Disponibilidade.PARA_VENDA, TipoPedido.VENDA,
            Disponibilidade.PARA_TROCA, TipoPedido.TROCA,
            Disponibilidade.PARA_DOACAO, TipoPedido.DOACAO);

    public PedidoService(
            PedidoRepository pedidoRepository,
            ProdutoRepository produtoRepository,
            EstoqueRepository estoqueRepository,
            EstoqueService estoqueService,
            UsuarioRepository usuarioRepository,
            NotificacaoService notificacaoService,
            ApplicationEventPublisher eventos) {

        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.estoqueRepository = estoqueRepository;
        this.estoqueService = estoqueService;
        this.usuarioRepository = usuarioRepository;
        this.notificacaoService = notificacaoService;
        this.eventos = eventos;
    }

    // CREATE
    @Transactional
    public PedidoResponseDTO criar(PedidoRequestDTO dto) {

        Pedido pedido = new Pedido();
        pedido.setTipoPedido(dto.tipoPedido());
        pedido.setMensagemOpcional(dto.mensagemOpcional());
        pedido.setStatus(StatusPedido.PENDENTE);
        pedido.setUsuarioSolicitante(buscarUsuario(dto.usuarioSolicitanteId()));
        pedido.setProprietarioRecebedor(referenciaProprietario(dto.proprietarioRecebedorId()));
        pedido.setComprador(novoComprador(dto.comprador()));

        preencherItens(pedido, dto.itens());

        conferirSaldo(pedido);

        // Os itens sao persistidos junto pelo cascade configurado em Pedido.itens
        Pedido registrado = pedidoRepository.save(pedido);
        notificacaoService.criarParaPedidoRegistrado(registrado);
        publicarCopiaDoPedido(registrado);

        return mapToResponse(registrado);
    }

    // CREATE - pedido feito pelo site público, sem login.
    // Produto, produtor e preço saem da oferta. O tipo é o da oferta, a não
    // ser que o produtor aceite negociar e quem pede proponha outro.
    @Transactional
    public PedidoPublicoResponseDTO criarPublico(PedidoPublicoRequestDTO dto) {

        Estoque oferta = estoqueRepository.findById(dto.estoqueId())
                .filter(estoque -> TIPO_POR_DISPONIBILIDADE_PUBLICA.containsKey(estoque.getDisponibilidade()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Oferta não encontrada no catálogo: " + dto.estoqueId()));

        TipoPedido tipo = tipoDoPedidoPublico(oferta, dto.tipoPedido());

        Pedido pedido = new Pedido();
        pedido.setTipoPedido(tipo);
        pedido.setMensagemOpcional(textoOuNulo(dto.mensagemOpcional()));
        pedido.setStatus(StatusPedido.PENDENTE);
        pedido.setProprietarioRecebedor(oferta.getProprietario());
        pedido.setComprador(Comprador.builder()
                .nome(dto.nome().trim())
                .email(dto.email().trim())
                .telefone(textoOuNulo(dto.telefone()))
                .build());
        pedido.adicionarItem(Itens.builder()
                .produto(oferta.getProduto())
                .quantidade(dto.quantidade())
                // Preço só vale para venda: troca ou doação proposta não tem total
                .precoUnitario(tipo == TipoPedido.VENDA ? oferta.getPreco() : null)
                .build());

        conferirSaldo(pedido);

        Pedido registrado = pedidoRepository.save(pedido);
        notificacaoService.criarParaPedidoRegistrado(registrado);
        publicarCopiaDoPedido(registrado);

        return new PedidoPublicoResponseDTO(
                registrado.getId(),
                registrado.getTipoPedido(),
                registrado.getStatus(),
                registrado.getDataPedido(),
                oferta.getProduto().getNomePopular(),
                dto.quantidade());
    }

    // READ - listar pedidos do proprietario
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos(UUID proprietarioId) {
        return listarTodos(proprietarioId, PedidoFiltroDTO.vazio());
    }

    // READ - historico filtrado (periodo, tipo, semente, status), mais recente primeiro.
    // O volume por proprietario e pequeno, entao filtrar em memoria sobre a
    // consulta que ja traz os itens evita uma query dinamica so para isso.
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos(UUID proprietarioId, PedidoFiltroDTO filtro) {
        return pedidoRepository.findAllByProprietarioRecebedorId(proprietarioId)
                .stream()
                .filter(pedido -> atendeFiltro(pedido, filtro))
                .sorted(Comparator.comparing(Pedido::getDataPedido,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToResponse)
                .toList();
    }

    /** Usado no @PreAuthorize do controller: o pedido existe e foi recebido por esse proprietário. */
    public boolean ehDoProprietario(UUID pedidoId, UUID proprietarioId) {
        return pedidoRepository.existsByIdAndProprietarioRecebedorId(pedidoId, proprietarioId);
    }

    // READ - por ID
    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(UUID id) {
        return mapToResponse(buscarEntidadeComItens(id));
    }

    // UPDATE (CDU-13)
    @Transactional
    public PedidoResponseDTO atualizar(UUID id, PedidoUpdateDTO dto) {

        Pedido pedido = buscarParaAtualizacao(id);

        // So faz sentido alterar um pedido que ainda esta na fila. Alterar um
        // CONFIRMADO exigiria estornar a baixa e refazer; o fluxo correto nesse
        // caso e cancelar e abrir outro pedido.
        if (pedido.getStatus() != StatusPedido.PENDENTE) {
            throw new TransicaoStatusInvalidaException(
                    "Apenas pedidos PENDENTE podem ser alterados. Status atual: "
                            + pedido.getStatus());
        }

        pedido.setTipoPedido(dto.tipoPedido());
        pedido.setMensagemOpcional(dto.mensagemOpcional());

        // Substitui a lista inteira: o orphanRemoval apaga os itens que sairam
        pedido.getItens().clear();
        preencherItens(pedido, dto.itens());

        conferirSaldo(pedido);

        return mapToResponse(pedidoRepository.save(pedido));
    }

    // DELETE (CDU-14)
    @Transactional
    public void excluir(UUID id) {

        Pedido pedido = buscarParaAtualizacao(id);

        // Se o estoque chegou a ser baixado, devolve antes de apagar o pedido,
        // senao a quantidade some do sistema junto com o registro.
        if (baixouEstoque(pedido)) {
            devolverEstoque(pedido);
        }

        notificacaoService.desvincularPedido(pedido.getId());

        pedidoRepository.delete(pedido);
    }

    // CICLO DE VIDA

    @Transactional
    public PedidoResponseDTO confirmar(UUID id) {

        travarEstoquesDoPedido(id);

        Pedido pedido = buscarParaAtualizacao(id);
        validarTransicao(pedido, StatusPedido.CONFIRMADO);

        baixarEstoque(pedido);

        pedido.setStatus(StatusPedido.CONFIRMADO);
        Pedido confirmado = pedidoRepository.save(pedido);
        notificacaoService.criarParaPedidoConfirmado(confirmado);
        // E-mail com o WhatsApp do produtor para combinarem a entrega (#184)
        CopiaDoPedidoEmail.avisoDeConfirmacao(confirmado).ifPresent(eventos::publishEvent);

        recusarPedidosQueNaoCabemMais(confirmado);

        return mapToResponse(confirmado);
    }

    @Transactional
    public PedidoResponseDTO cancelar(UUID id) {

        Pedido pedido = buscarParaAtualizacao(id);
        validarTransicao(pedido, StatusPedido.CANCELADO);

        boolean devolveu = baixouEstoque(pedido);
        if (devolveu) {
            devolverEstoque(pedido);
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        Pedido cancelado = pedidoRepository.save(pedido);
        notificacaoService.criarParaPedidoCancelado(cancelado, devolveu);

        return mapToResponse(cancelado);
    }

    // REGRAS DE ESTOQUE

    /**
     * Entrada na fila: o pedido nao pode passar do saldo de agora, mas nao
     * baixa nada. Varios pendentes podem somar mais que o estoque; quem nao
     * couber depois de uma confirmacao e recusado (recusarPedidosQueNaoCabemMais).
     */
    private void conferirSaldo(Pedido pedido) {

        UUID proprietarioId = pedido.getProprietarioRecebedor().getId();

        quantidadePorProduto(pedido).forEach((produtoId, demanda) ->
                validarSaldo(estoqueRepository.findByProprietarioIdAndProdutoId(proprietarioId, produtoId), demanda));
    }

    /**
     * Trava as linhas de estoque do pedido antes da linha do pedido. Os ids
     * vem de consultas escalares para nenhuma entidade ser carregada sem lock
     * (o Hibernate devolveria essa copia antiga no findParaAtualizacao).
     */
    private void travarEstoquesDoPedido(UUID pedidoId) {

        UUID proprietarioId = pedidoRepository.findProprietarioRecebedorId(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado: " + pedidoId));

        new TreeSet<>(pedidoRepository.findProdutoIdsDoPedido(pedidoId))
                .forEach(produtoId -> estoqueRepository.findParaAtualizacao(proprietarioId, produtoId));
    }

    /** Baixa o estoque na confirmacao, sob lock pessimista. */
    private void baixarEstoque(Pedido pedido) {

        UUID proprietarioId = pedido.getProprietarioRecebedor().getId();

        quantidadePorProduto(pedido).forEach((produtoId, demanda) -> {

            Estoque estoque = validarSaldo(estoqueRepository.findParaAtualizacao(proprietarioId, produtoId), demanda);

            double saldoAnterior = estoque.getQuantidade();
            estoque.setQuantidade(saldoAnterior - demanda.quantidade());
            estoqueRepository.save(estoque);
            estoqueService.registrarLancamentoDePedido(estoque, tipoDeSaida(pedido.getTipoPedido()),
                    OrigemMovimentacao.PEDIDO, saldoAnterior, "Pedido de " + quemPediu(pedido));
        });
    }

    private Estoque validarSaldo(Optional<Estoque> encontrado, Demanda demanda) {

        Estoque estoque = encontrado.orElseThrow(() -> new EstoqueInsuficienteException(
                "O produto " + demanda.nome()
                        + " não está disponível no estoque deste proprietário."));

        // O proprietario tirou o produto de circulacao: ter saldo nao basta
        if (estoque.getDisponibilidade() == Disponibilidade.INDISPONIVEL) {
            throw new EstoqueInsuficienteException(
                    "O produto " + demanda.nome()
                            + " está marcado como indisponível no estoque deste proprietário.");
        }

        if (estoque.getQuantidade() < demanda.quantidade()) {
            throw new EstoqueInsuficienteException(
                    "Estoque insuficiente para o produto " + demanda.nome()
                            + ": disponível " + formatarQuantidade(estoque.getQuantidade())
                            + ", solicitado " + formatarQuantidade(demanda.quantidade()) + ".");
        }

        return estoque;
    }

    /**
     * Depois de uma confirmacao, os pedidos da fila que pedem algum dos mesmos
     * produtos e nao cabem mais no saldo sao recusados (viram CANCELADO) e quem
     * pediu e avisado. Os que ainda cabem continuam na fila.
     *
     * Ex.: 50 kg; pedidos de 49 e 10. Confirmado o de 49, sobra 1 e o de 10 e
     * recusado. Com pedidos de 40 e 10, sobram 10 e o segundo segue na fila.
     */
    private void recusarPedidosQueNaoCabemMais(Pedido confirmado) {

        UUID proprietarioId = confirmado.getProprietarioRecebedor().getId();
        Map<UUID, Double> saldoPorProduto = new TreeMap<>();

        quantidadePorProduto(confirmado).keySet().forEach(produtoId ->
                estoqueRepository.findParaAtualizacao(proprietarioId, produtoId)
                        .ifPresent(estoque -> saldoPorProduto.put(produtoId, estoque.getQuantidade())));

        if (saldoPorProduto.isEmpty()) {
            return;
        }

        // Ids primeiro, e cada pedido carregado ja travado: um cancelamento ou
        // exclusao simultaneo nao deixa o status lido aqui desatualizado
        pedidoRepository.findIdsNaFila(proprietarioId, saldoPorProduto.keySet(), confirmado.getId())
                .forEach(pedidoId -> pedidoRepository.findParaAtualizacao(pedidoId)
                        .filter(pedido -> pedido.getStatus() == StatusPedido.PENDENTE)
                        .filter(pedido -> !cabeNoSaldo(pedido, saldoPorProduto))
                        .ifPresent(pedido -> recusarPorFaltaDeEstoque(pedido, saldoPorProduto)));
    }

    private boolean cabeNoSaldo(Pedido pedido, Map<UUID, Double> saldoPorProduto) {
        return quantidadePorProduto(pedido).entrySet().stream()
                .filter(demanda -> saldoPorProduto.containsKey(demanda.getKey()))
                .allMatch(demanda -> demanda.getValue().quantidade() <= saldoPorProduto.get(demanda.getKey()));
    }

    private void recusarPorFaltaDeEstoque(Pedido pedido, Map<UUID, Double> saldoPorProduto) {

        pedido.setStatus(StatusPedido.CANCELADO);
        Pedido recusado = pedidoRepository.save(pedido);
        notificacaoService.criarParaPedidoRecusado(recusado);
        CopiaDoPedidoEmail.avisoDeRecusa(recusado, saldoPorProduto).ifPresent(eventos::publishEvent);
    }

    /**
     * Soma de volta o que foi baixado na confirmacao. Se a linha de estoque
     * sumiu no meio do caminho nao ha o que restaurar, e o cancelamento nao
     * pode falhar por causa disso.
     */
    private void devolverEstoque(Pedido pedido) {

        UUID proprietarioId = pedido.getProprietarioRecebedor().getId();

        quantidadePorProduto(pedido).forEach((produtoId, demanda) ->
                estoqueRepository
                        .findParaAtualizacao(proprietarioId, produtoId)
                        .ifPresent(estoque -> {
                            double saldoAnterior = estoque.getQuantidade();
                            estoque.setQuantidade(saldoAnterior + demanda.quantidade());
                            estoqueRepository.save(estoque);
                            estoqueService.registrarLancamentoDePedido(estoque, TipoMovimentacao.ENTRADA,
                                    OrigemMovimentacao.DEVOLUCAO, saldoAnterior,
                                    "Devolução do pedido de " + quemPediu(pedido));
                        }));
    }

    /** Quanto de um produto o pedido movimenta, com o nome para as mensagens de erro. */
    private record Demanda(String nome, double quantidade) {

        Demanda somar(Demanda outra) {
            return new Demanda(nome, quantidade + outra.quantidade);
        }
    }

    /**
     * Agrupa por produto: pedidos antigos podem ter o mesmo produto em mais de
     * um item, e validar item a item deixaria passar uma soma que estoura o
     * estoque. O TreeMap trava as linhas de estoque sempre na mesma ordem
     * (por id), o que evita deadlock entre dois pedidos com os mesmos produtos
     * em ordens diferentes.
     */
    private Map<UUID, Demanda> quantidadePorProduto(Pedido pedido) {

        Map<UUID, Demanda> total = new TreeMap<>();

        for (Itens item : pedido.getItens()) {
            Produto produto = item.getProduto();
            total.merge(produto.getId(),
                    new Demanda(produto.getNomePopular(), item.getQuantidade()),
                    Demanda::somar);
        }

        return total;
    }

    private static TipoMovimentacao tipoDeSaida(TipoPedido tipo) {
        return switch (tipo) {
            case VENDA -> TipoMovimentacao.SAIDA_VENDA;
            case TROCA -> TipoMovimentacao.SAIDA_TROCA;
            case DOACAO -> TipoMovimentacao.SAIDA_DOACAO;
        };
    }

    // Nome que aparece no historico do estoque: o comprador do site ou a conta que registrou
    private static String quemPediu(Pedido pedido) {
        if (pedido.getComprador() != null && pedido.getComprador().getNome() != null) {
            return pedido.getComprador().getNome();
        }
        if (pedido.getUsuarioSolicitante() != null && pedido.getUsuarioSolicitante().getPessoa() != null) {
            return pedido.getUsuarioSolicitante().getNome();
        }
        return "comprador não informado";
    }

    // So o pedido aceito pelo produtor tirou do estoque; o PENDENTE esta na fila
    private boolean baixouEstoque(Pedido pedido) {
        return pedido.getStatus() == StatusPedido.CONFIRMADO;
    }

    // 10.0 -> "10", 2.50 -> "2.5": a mensagem vai direto para a tela
    private static String formatarQuantidade(double quantidade) {
        return BigDecimal.valueOf(quantidade).stripTrailingZeros().toPlainString();
    }

    private void validarTransicao(Pedido pedido, StatusPedido destino) {

        if (!pedido.getStatus().podeTransicionarPara(destino)) {
            throw new TransicaoStatusInvalidaException(
                    "Transição inválida: " + pedido.getStatus() + " -> " + destino
                            + ". Transições permitidas a partir de " + pedido.getStatus()
                            + ": " + pedido.getStatus().proximosEstados());
        }
    }

    // AUXILIARES

    private boolean atendeFiltro(Pedido pedido, PedidoFiltroDTO filtro) {

        return atendePeriodo(pedido, filtro)
                && (filtro.tipoPedido() == null || pedido.getTipoPedido() == filtro.tipoPedido())
                && (filtro.status() == null || pedido.getStatus() == filtro.status())
                && (filtro.produtoId() == null || pedido.getItens().stream()
                        .anyMatch(item -> filtro.produtoId().equals(item.getProduto().getId())));
    }

    private boolean atendePeriodo(Pedido pedido, PedidoFiltroDTO filtro) {

        if (filtro.dataInicio() == null && filtro.dataFim() == null) {
            return true;
        }

        LocalDate dia = pedido.getDataPedido().toLocalDate();

        return (filtro.dataInicio() == null || !dia.isBefore(filtro.dataInicio()))
                && (filtro.dataFim() == null || !dia.isAfter(filtro.dataFim()));
    }

    // O CopiaDoPedidoListener só envia depois do commit
    private void publicarCopiaDoPedido(Pedido pedido) {
        CopiaDoPedidoEmail.de(pedido).ifPresent(eventos::publishEvent);
    }

    private static TipoPedido tipoDoPedidoPublico(Estoque oferta, TipoPedido proposto) {

        TipoPedido daOferta = TIPO_POR_DISPONIBILIDADE_PUBLICA.get(oferta.getDisponibilidade());

        if (proposto == null || proposto == daOferta) {
            return daOferta;
        }
        if (!oferta.isAceitaNegociacao()) {
            throw new NegociacaoNaoPermitidaException(
                    "O produtor não aceita negociar esta oferta: o pedido precisa ser de "
                            + ROTULO_TIPO.get(daOferta) + ".");
        }
        return proposto;
    }

    private static final Map<TipoPedido, String> ROTULO_TIPO = Map.of(
            TipoPedido.VENDA, "venda",
            TipoPedido.TROCA, "troca",
            TipoPedido.DOACAO, "doação");

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Comprador novoComprador(CompradorDTO dto) {
        if (dto == null) {
            return null;
        }
        return Comprador.builder()
                .nome(dto.nome().trim())
                .telefone(dto.telefone())
                .email(textoOuNulo(dto.email()))
                .build();
    }

    /**
     * Um produto repetido na requisicao vira um unico item com as quantidades
     * somadas, para que os itens gravados batam um a um com os produtos e com
     * o que saiu do estoque. Precos diferentes para o mesmo produto nao tem
     * como ser conciliados, entao a requisicao e recusada.
     */
    private void preencherItens(Pedido pedido, List<ItemPedidoRequestDTO> itens) {

        Map<UUID, Itens> porProduto = new LinkedHashMap<>();

        for (ItemPedidoRequestDTO itemDto : itens) {

            Itens existente = porProduto.get(itemDto.produtoId());

            if (existente != null) {
                somarAoItem(existente, itemDto);
                continue;
            }

            Produto produto = produtoRepository.findById(itemDto.produtoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Produto não encontrado: " + itemDto.produtoId()));

            porProduto.put(itemDto.produtoId(), Itens.builder()
                    .produto(produto)
                    .quantidade(itemDto.quantidade())
                    .precoUnitario(itemDto.precoUnitario())
                    .build());
        }

        porProduto.values().forEach(pedido::adicionarItem);
    }

    private void somarAoItem(Itens item, ItemPedidoRequestDTO itemDto) {

        Double preco = item.getPrecoUnitario();
        Double novoPreco = itemDto.precoUnitario();

        if (preco != null && novoPreco != null && Double.compare(preco, novoPreco) != 0) {
            throw new IllegalArgumentException(
                    "O produto " + item.getProduto().getNomePopular()
                            + " foi informado mais de uma vez com preços diferentes.");
        }

        item.setQuantidade(item.getQuantidade() + itemDto.quantidade());
        if (preco == null) {
            item.setPrecoUnitario(novoPreco);
        }
    }

    private Pedido buscarEntidadeComItens(UUID id) {
        return pedidoRepository.findByIdComItens(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pedido não encontrado: " + id));
    }

    private Pedido buscarParaAtualizacao(UUID id) {
        return pedidoRepository.findParaAtualizacao(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pedido não encontrado: " + id));
    }

    private Usuario buscarUsuario(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuário solicitante não encontrado: " + id));
    }

    // Mesma abordagem do EstoqueService: referencia so pelo id, sem ir ao banco
    private Proprietario referenciaProprietario(UUID id) {
        Proprietario proprietario = new Proprietario();
        proprietario.setId(id);
        return proprietario;
    }

    private PedidoResponseDTO mapToResponse(Pedido pedido) {

        return new PedidoResponseDTO(
                pedido.getId(),
                pedido.getTipoPedido(),
                pedido.getMensagemOpcional(),
                pedido.getDataPedido(),
                pedido.getStatus(),
                pedido.getUsuarioSolicitante() != null ? pedido.getUsuarioSolicitante().getId() : null,
                pedido.getProprietarioRecebedor().getId(),
                pedido.getItens().stream().map(this::mapToItemResponse).toList(),
                mapToCompradorResponse(pedido.getComprador())
        );
    }

    private CompradorDTO mapToCompradorResponse(Comprador comprador) {
        if (comprador == null) {
            return null;
        }
        return new CompradorDTO(comprador.getNome(), comprador.getTelefone(), comprador.getEmail());
    }

    private ItemPedidoResponseDTO mapToItemResponse(Itens item) {

        return new ItemPedidoResponseDTO(
                item.getId(),
                item.getProduto().getId(),
                item.getProduto().getNomePopular(),
                item.getQuantidade(),
                item.getPrecoUnitario()
        );
    }
}
