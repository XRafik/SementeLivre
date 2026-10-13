package com.sementelivre.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.sementelivre.backend.dto.NotificacaoRequestDTO;
import com.sementelivre.backend.dto.NotificacaoResponseDTO;
import com.sementelivre.backend.entity.Itens;
import com.sementelivre.backend.entity.Notificacao;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import com.sementelivre.backend.repository.NotificacaoRepository;

import jakarta.persistence.EntityManager;

@Service
public class NotificacaoService
        implements CrudService<NotificacaoRequestDTO, NotificacaoResponseDTO, UUID> {

    private final NotificacaoRepository notificacaoRepository;
    private final EntityManager entityManager;

    public NotificacaoService(
            NotificacaoRepository notificacaoRepository,
            EntityManager entityManager) {

        this.notificacaoRepository = notificacaoRepository;
        this.entityManager = entityManager;
    }

    // Só o dono pode trocar o dono: um POST autenticado comum não cria
    // notificação arbitrária em nome de outra pessoa.
    @Override
    public NotificacaoResponseDTO criar(NotificacaoRequestDTO dto) {

        Notificacao notificacao = Notificacao.builder()
                .titulo(dto.titulo())
                .mensagem(dto.mensagem())
                .proprietario(buscarProprietario(dto.proprietarioId()))
                .pedidoRelacionado(buscarPedido(dto.pedidoRelacionadoId()))
                .build();

        // saveAndFlush grava na hora, assim a dataGeracao ja volta preenchida
        Notificacao salva = notificacaoRepository.saveAndFlush(notificacao);

        return toResponseDTO(salva);
    }

    // ---- CDU-26: notificacoes automaticas do ciclo de vida do pedido ----
    // Cada evento que mexe no estoque (registro reserva, cancelamento devolve)
    // ou no status gera um aviso para o proprietario que recebeu o pedido.

    public NotificacaoResponseDTO criarParaPedidoRegistrado(Pedido pedido) {
        return criarParaPedido(pedido, "Novo pedido recebido", "registrado", null);
    }

    public NotificacaoResponseDTO criarParaPedidoConfirmado(Pedido pedido) {
        return criarParaPedido(pedido, "Pedido confirmado", "confirmado", null);
    }

    public NotificacaoResponseDTO criarParaPedidoCancelado(Pedido pedido) {
        return criarParaPedido(pedido, "Pedido cancelado", "cancelado",
                "O estoque reservado foi devolvido.");
    }

    private NotificacaoResponseDTO criarParaPedido(
            Pedido pedido, String titulo, String evento, String complemento) {

        entityManager.flush();

        String mensagem = montarMensagemDoPedido(pedido, evento);
        if (complemento != null) {
            mensagem += " " + complemento;
        }

        Proprietario proprietarioGerenciado = entityManager.getReference(
            Proprietario.class, pedido.getProprietarioRecebedor().getId());

        return gravarParaPedido(pedido, proprietarioGerenciado, titulo, mensagem);
    }

    /**
     * Avisa o dono do estoque de que chegou um pedido novo.
     *
     * Fica no backend de propósito: se dependesse do navegador do solicitante,
     * uma queda entre gravar o pedido e criar a notificação deixaria o
     * produtor sem saber que alguém pediu.
     */
    public NotificacaoResponseDTO criarParaPedidoRecebido(Pedido pedido) {
        entityManager.flush();

        Proprietario recebedor = buscarProprietario(pedido.getProprietarioRecebedor().getId());

        return gravarParaPedido(
                pedido,
                recebedor,
                "Novo pedido recebido",
                montarMensagemDoPedido(pedido, "recebido")
        );
    }

    /**
     * Avisa o solicitante de que o pedido saiu de PENDENTE — cancelado,
     * recusado ou concluído. Sem isso ele nunca descobre o desfecho.
     */
    public NotificacaoResponseDTO criarParaPedidoCancelado(Pedido pedido, String motivo) {
        entityManager.flush();

        Proprietario solicitante = buscarProprietario(pedido.getUsuarioSolicitante().getId());

        StringBuilder texto = new StringBuilder(montarMensagemDoPedido(pedido, "cancelado"));

        if (motivo != null && !motivo.isBlank()) {
            texto.append(" Motivo: ").append(motivo).append(".");
        }

        return gravarParaPedido(pedido, solicitante, "Pedido cancelado", texto.toString());
    }

    private NotificacaoResponseDTO gravarParaPedido(
            Pedido pedido,
            Proprietario destinatario,
            String titulo,
            String mensagem) {

        Pedido pedidoGerenciado = entityManager.getReference(Pedido.class, pedido.getId());

        Notificacao notificacao = Notificacao.builder()
            .titulo(titulo)
            .mensagem(mensagem)
            .proprietario(destinatario)
            .pedidoRelacionado(pedidoGerenciado)
            .build();

        return toResponseDTO(notificacaoRepository.saveAndFlush(notificacao));
    }

    // Monta o texto da notificacao com os detalhes do pedido, como pede o CDU-26.
    // Exemplo: "Pedido de TROCA confirmado. Solicitante: Maria. Itens: 2 x Feijao Crioulo."
    private String montarMensagemDoPedido(Pedido pedido, String evento) {

        StringBuilder mensagem = new StringBuilder();

        if (pedido.getTipoPedido() != null) {
            mensagem.append("Pedido de ").append(pedido.getTipoPedido()).append(" ").append(evento).append(".");
        } else {
            mensagem.append("Pedido ").append(evento).append(".");
        }

        if (pedido.getUsuarioSolicitante() != null) {
            mensagem.append(" Solicitante: ").append(pedido.getUsuarioSolicitante().getNome()).append(".");
        }

        if (pedido.getComprador() != null) {
            mensagem.append(" Comprador: ").append(pedido.getComprador().getNome()).append(".");
        }

        String itens = montarListaDeItens(pedido);

        if (!itens.isEmpty()) {
            mensagem.append(" Itens: ").append(itens).append(".");
        }

        return mensagem.toString();
    }

    // Junta os itens em um texto so: "2 x Feijao Crioulo, 1 x Milho"
    private String montarListaDeItens(Pedido pedido) {

        if (pedido.getItens() == null) {
            return "";
        }

        List<String> itens = new ArrayList<>();

        for (Itens item : pedido.getItens()) {
            String nomeProduto = "produto";

            if (item.getProduto() != null && item.getProduto().getNomePopular() != null) {
                nomeProduto = item.getProduto().getNomePopular();
            }

            itens.add(formatarQuantidade(item.getQuantidade()) + " x " + nomeProduto);
        }

        return String.join(", ", itens);
    }

    // A quantidade e Double: mostra 2 em vez de 2.0 quando nao tem casa decimal
    private String formatarQuantidade(Double quantidade) {

        if (quantidade == null) {
            return "0";
        }

        if (quantidade == Math.floor(quantidade)) {
            return String.valueOf(quantidade.longValue());
        }

        return String.valueOf(quantidade);
    }

    public void desvincularPedido(UUID pedidoId) {
        notificacaoRepository.findByPedidoRelacionadoId(pedidoId)
                .forEach(notificacao -> notificacao.setPedidoRelacionado(null));
        notificacaoRepository.flush();
    }

    // READ - por ID
    @Override
    public NotificacaoResponseDTO buscarPorId(UUID id) {
        Notificacao notificacao = buscarEntidadeDoDono(id);

        return toResponseDTO(notificacao);
    }

    // READ - do usuário logado
    //
    // Antes devolvia findAll(), ou seja, as notificações de todos osCadastros
    // para qualquer pessoa autenticada. Agora só o dono vê o próprio.
    @Override
    public List<NotificacaoResponseDTO> listar() {
        return notificacaoRepository
                .findByProprietarioIdOrderByDataGeracaoDesc(usuarioAtualId())
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // READ - historico de um proprietario
    public List<NotificacaoResponseDTO> listarPorProprietario(UUID proprietarioId) {
        return notificacaoRepository.findByProprietarioIdOrderByDataGeracaoDesc(proprietarioId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // Quantas ainda não foram lidas, para o badge do cabeçalho.
    public long contarNaoLidas() {
        return notificacaoRepository.countByProprietarioIdAndLidaFalse(usuarioAtualId());
    }

    // UPDATE
    //
    // Só o conteúdo pode ser corrigido. Trocar o dono (proprietarioId) permitiria
    //.AUTHenticated sequestrar a notificação de outra pessoa, então o campo é
    // ignorado de propósito.
    @Override
    public NotificacaoResponseDTO atualizar(UUID id, NotificacaoRequestDTO dto) {

        Notificacao notificacao = buscarEntidadeDoDono(id);

        notificacao.setTitulo(dto.titulo());
        notificacao.setMensagem(dto.mensagem());
        notificacao.setPedidoRelacionado(buscarPedido(dto.pedidoRelacionadoId()));

        Notificacao atualizada = notificacaoRepository.save(notificacao);

        return toResponseDTO(atualizada);
    }

    // UPDATE - marcar como lida
    public NotificacaoResponseDTO marcarComoLida(UUID id) {

        Notificacao notificacao = buscarEntidadeDoDono(id);

        notificacao.marcarComoLida();

        Notificacao atualizada = notificacaoRepository.save(notificacao);

        return toResponseDTO(atualizada);
    }

    // DELETE
    @Override
    public void deletar(UUID id) {
        Notificacao notificacao = buscarEntidadeDoDono(id);

        notificacaoRepository.delete(notificacao);
    }

    private Notificacao buscarEntidadePorId(UUID id) {
        return notificacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Notificação não encontrada: " + id
                ));
    }

    /**
     * Carrega a notificação só se ela for do usuário logado.
     *
     * Devolve 404 (e não 403) de propósito: um 403 confirmaria que o id existe,
     * o que já permitiria sondar a base de outra pessoa.
     */
    private Notificacao buscarEntidadeDoDono(UUID id) {
        Notificacao notificacao = buscarEntidadePorId(id);

        if (!notificacao.getProprietario().getId().equals(usuarioAtualId())) {
            throw new RecursoNaoEncontradoException(
                    "Notificação não encontrada: " + id
            );
        }

        return notificacao;
    }

    private UUID usuarioAtualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof Usuario usuario)) {
            throw new RecursoNaoEncontradoException(
                    "Nenhuma sessão autenticada para ler notificações."
            );
        }

        return usuario.getId();
    }

    // Proprietario e Pedido ainda nao tem repository (sao placeholders),
    // entao buscamos direto pelo EntityManager
    private Proprietario buscarProprietario(UUID proprietarioId) {
        Proprietario proprietario = entityManager.find(Proprietario.class, proprietarioId);

        if (proprietario == null) {
            throw new RecursoNaoEncontradoException("Proprietário não encontrado: " + proprietarioId);
        }

        return proprietario;
    }

    // O pedido e opcional
    private Pedido buscarPedido(UUID pedidoId) {
        if (pedidoId == null) {
            return null;
        }

        Pedido pedido = entityManager.find(Pedido.class, pedidoId);

        if (pedido == null) {
            throw new RecursoNaoEncontradoException("Pedido não encontrado: " + pedidoId);
        }

        return pedido;
    }

    private NotificacaoResponseDTO toResponseDTO(Notificacao notificacao) {

        UUID pedidoRelacionadoId = null;

        if (notificacao.getPedidoRelacionado() != null) {
            pedidoRelacionadoId = notificacao.getPedidoRelacionado().getId();
        }

        return new NotificacaoResponseDTO(
                notificacao.getId(),
                notificacao.getTitulo(),
                notificacao.getMensagem(),
                notificacao.isLida(),
                notificacao.getDataGeracao(),
                notificacao.getDataLeitura(),
                notificacao.getProprietario().getId(),
                pedidoRelacionadoId
        );
    }
}
