package com.sementelivre.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sementelivre.backend.dto.NotificacaoRequestDTO;
import com.sementelivre.backend.dto.NotificacaoResponseDTO;
import com.sementelivre.backend.entity.Notificacao;
import com.sementelivre.backend.entity.Pedido;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.exception.RecursoNaoEncontradoException;
import com.sementelivre.backend.repository.NotificacaoRepository;
import com.sementelivre.backend.util.MensagemPedido;
import com.sementelivre.backend.util.UsuarioLogado;

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

    // Cada um so cria notificacao para si mesmo, nao em nome de outra pessoa
    @Override
    public NotificacaoResponseDTO criar(NotificacaoRequestDTO dto) {

        if (!dto.proprietarioId().equals(UsuarioLogado.id())) {
            throw new RecursoNaoEncontradoException("Proprietário não encontrado: " + dto.proprietarioId());
        }

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
    // Na confirmacao e no cancelamento quem pediu tambem e avisado,
    // senao ele nunca descobre o que aconteceu com o pedido.

    public NotificacaoResponseDTO criarParaPedidoRegistrado(Pedido pedido) {
        return criarParaPedido(pedido, "Novo pedido recebido", "registrado", null);
    }

    public NotificacaoResponseDTO criarParaPedidoConfirmado(Pedido pedido) {
        NotificacaoResponseDTO resposta = criarParaPedido(pedido, "Pedido confirmado", "confirmado", null);

        avisarSolicitante(pedido, "Seu pedido foi confirmado", "confirmado");

        return resposta;
    }

    // Pedido ainda na fila nao tinha baixado nada: so o CONFIRMADO devolve estoque (#183)
    public NotificacaoResponseDTO criarParaPedidoCancelado(Pedido pedido, boolean devolveuEstoque) {
        NotificacaoResponseDTO resposta = criarParaPedido(pedido, "Pedido cancelado", "cancelado",
                devolveuEstoque ? "A quantidade voltou para o estoque." : null);

        avisarSolicitante(pedido, "Seu pedido foi cancelado", "cancelado");

        return resposta;
    }

    // Recusa automatica da fila: outro pedido confirmado deixou o saldo menor que este
    public NotificacaoResponseDTO criarParaPedidoRecusado(Pedido pedido) {
        NotificacaoResponseDTO resposta = criarParaPedido(pedido, "Pedido recusado", "recusado",
                "O estoque não cobre mais este pedido depois da confirmação de outro.");

        avisarSolicitante(pedido, "Seu pedido foi recusado", "recusado");

        return resposta;
    }

    private NotificacaoResponseDTO criarParaPedido(
            Pedido pedido, String titulo, String evento, String complemento) {

        entityManager.flush();

        String mensagem = MensagemPedido.montar(pedido, evento);
        if (complemento != null) {
            mensagem += " " + complemento;
        }

        Proprietario proprietarioGerenciado = entityManager.getReference(
            Proprietario.class, pedido.getProprietarioRecebedor().getId());

        return gravarParaPedido(pedido, proprietarioGerenciado, titulo, mensagem);
    }

    // Avisa quem fez o pedido. Pedido do site publico nao tem conta (so
    // comprador), e o solicitante pode nao ser proprietario: nesses casos nao
    // ha para quem mandar, entao nao faz nada.
    private void avisarSolicitante(Pedido pedido, String titulo, String evento) {

        if (pedido.getUsuarioSolicitante() == null) {
            return;
        }

        UUID solicitanteId = pedido.getUsuarioSolicitante().getId();

        // Quem pediu para si mesmo ja recebeu o aviso como recebedor
        if (solicitanteId == null || solicitanteId.equals(pedido.getProprietarioRecebedor().getId())) {
            return;
        }

        Proprietario solicitante = entityManager.find(Proprietario.class, solicitanteId);

        if (solicitante == null) {
            return;
        }

        gravarParaPedido(pedido, solicitante, titulo, MensagemPedido.montar(pedido, evento));
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
    // Antes também existia ?proprietarioId=, que deixava qualquer pessoa logada
    // ler as notificações de outra. Agora só o dono vê as próprias.
    @Override
    public List<NotificacaoResponseDTO> listar() {
        return notificacaoRepository
                .findByProprietarioIdOrderByDataGeracaoDesc(UsuarioLogado.id())
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // Quantas ainda não foram lidas, para o badge do cabeçalho.
    public long contarNaoLidas() {
        return notificacaoRepository.countByProprietarioIdAndLidaFalse(UsuarioLogado.id());
    }

    // UPDATE
    //
    // Só o conteúdo pode ser corrigido. Trocar o dono (proprietarioId) permitiria
    // sequestrar a notificação de outra pessoa, então o campo é ignorado de propósito.
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

    // UPDATE - marcar como nao lida
    public NotificacaoResponseDTO marcarComoNaoLida(UUID id) {

        Notificacao notificacao = buscarEntidadeDoDono(id);

        notificacao.marcarComoNaoLida();

        Notificacao atualizada = notificacaoRepository.save(notificacao);

        return toResponseDTO(atualizada);
    }

    // DELETE
    @Override
    public void deletar(UUID id) {
        Notificacao notificacao = buscarEntidadeDoDono(id);

        notificacaoRepository.delete(notificacao);
    }

    /**
     * Carrega a notificação só se ela for do usuário logado.
     *
     * Devolve 404 (e não 403) de propósito: um 403 confirmaria que o id existe,
     * o que já permitiria sondar a base de outra pessoa.
     */
    private Notificacao buscarEntidadeDoDono(UUID id) {
        Notificacao notificacao = notificacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Notificação não encontrada: " + id
                ));

        if (!notificacao.getProprietario().getId().equals(UsuarioLogado.id())) {
            throw new RecursoNaoEncontradoException(
                    "Notificação não encontrada: " + id
            );
        }

        return notificacao;
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
