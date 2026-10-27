package com.sementelivre.backend.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.sementelivre.backend.BackendApplication;
import com.sementelivre.backend.dto.PedidoPublicoRequestDTO;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.ProdutoRepository;
import com.sementelivre.backend.service.EmailService;
import com.sementelivre.backend.service.EstoqueService;
import com.sementelivre.backend.service.PedidoService;

import jakarta.persistence.EntityManager;

/**
 * E-mails do ciclo do pedido com transação de verdade: saem depois do commit
 * (CopiaDoPedidoListener) e não saem quando a operação falha.
 */
@SpringBootTest(classes = BackendApplication.class)
class EmailDoPedidoIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CONFIRMADO = "Semente Livre - Seu pedido foi confirmado";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void confirmarPedidoDoSiteMandaOWhatsAppDoProdutorParaQuemPediu() {
        CrossDomainFixture.Scenario scenario = cenarioComTelefone("(32) 98888-7777");
        UUID pedidoId = pedidoDoSite(scenario, "cliente@exemplo.com");

        try {
            pedidoService.confirmar(pedidoId);

            verify(emailService, timeout(5000)).enviarCopiaDoPedido(argThat(email ->
                    email.assunto().equals(CONFIRMADO)
                            && email.para().equals("cliente@exemplo.com")
                            && email.texto().contains("https://wa.me/5532988887777?text=")));
        } finally {
            pedidoService.excluir(pedidoId);
        }
    }

    @Test
    void confirmacaoRecusadaNaoMandaEmail() {
        CrossDomainFixture.Scenario scenario = cenarioComTelefone("(32) 98888-7777");
        UUID pedidoId = pedidoDoSite(scenario, "outro@exemplo.com");

        try {
            pedidoService.confirmar(pedidoId);
            // Segunda confirmação do mesmo pedido falha antes de qualquer e-mail
            assertThrows(RuntimeException.class, () -> pedidoService.confirmar(pedidoId));

            verify(emailService, after(1500).times(1)).enviarCopiaDoPedido(argThat(email ->
                    email.assunto().equals(CONFIRMADO) && email.para().equals("outro@exemplo.com")));
        } finally {
            pedidoService.excluir(pedidoId);
        }
    }

    @Test
    void pedidoLancadoNoAppPeloProprioProdutorNaoGeraAvisoDeConfirmacao() {
        CrossDomainFixture.Scenario scenario = cenarioComTelefone("(32) 98888-7777");
        // Comprador sem e-mail: o aviso só pode ir para a conta solicitante, nunca para o produtor
        UUID pedidoId = pedidoService.criar(CrossDomainFixture.pedido(scenario, 1.0)).id();
        String emailDoProdutor = scenario.proprietario().getEmail();

        try {
            pedidoService.confirmar(pedidoId);

            verify(emailService, after(1500).never()).enviarCopiaDoPedido(argThat(email ->
                    email.assunto().equals(CONFIRMADO) && email.para().equalsIgnoreCase(emailDoProdutor)));
        } finally {
            pedidoService.excluir(pedidoId);
        }
    }

    private CrossDomainFixture.Scenario cenarioComTelefone(String telefone) {
        return transactionTemplate.execute(status -> {
            CrossDomainFixture.Scenario scenario = CrossDomainFixture.create(
                    entityManager, produtoRepository, estoqueService, 100.0);
            entityManager.find(Proprietario.class, scenario.proprietario().getId()).setTelefone(telefone);
            return scenario;
        });
    }

    private UUID pedidoDoSite(CrossDomainFixture.Scenario scenario, String email) {
        UUID estoqueId = estoqueRepository
                .findByProprietarioIdAndProdutoId(scenario.proprietario().getId(), scenario.produto().getId())
                .orElseThrow()
                .getId();
        return pedidoService.criarPublico(new PedidoPublicoRequestDTO(
                estoqueId, 2.0, "Cliente do Site", email, null, null)).id();
    }
}
