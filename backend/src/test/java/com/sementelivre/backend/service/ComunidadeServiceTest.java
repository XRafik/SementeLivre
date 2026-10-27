package com.sementelivre.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sementelivre.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sementelivre.backend.dto.ComunidadeRequestDTO;
import com.sementelivre.backend.dto.LogradouroDTO;
import com.sementelivre.backend.dto.ComunidadeResponseDTO;
import com.sementelivre.backend.entity.Comunidade;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.enums.StatusComunidade;
import com.sementelivre.backend.exception.DependenciaVinculadaException;
import com.sementelivre.backend.exception.NomeSimilarException;
import com.sementelivre.backend.exception.TransicaoStatusInvalidaException;
import com.sementelivre.backend.repository.ComunidadeRepository;
import com.sementelivre.backend.repository.LogradouroRepository;
import com.sementelivre.backend.repository.PropriedadeRepository;

@ExtendWith(MockitoExtension.class)
class ComunidadeServiceTest {

    @Mock private ComunidadeRepository comunidadeRepository;
    @Mock private LogradouroRepository logradouroRepository;
    @Mock private PropriedadeRepository propriedadeRepository;

    private ComunidadeService comunidadeService;
    private UUID comunidadeId;
    private UUID logradouroId;
    private Logradouro logradouro;

    @BeforeEach
    void setUp() {
        comunidadeService = new ComunidadeService(comunidadeRepository, logradouroRepository, propriedadeRepository);
        comunidadeId = UUID.randomUUID();
        logradouroId = UUID.randomUUID();
        logradouro = Logradouro.builder().id(logradouroId).municipio("Rio Pomba").uf("MG").build();
    }

    private Comunidade comunidade(String nome, StatusComunidade status) {
        return Comunidade.builder().id(comunidadeId).nome(nome).logradouro(logradouro).status(status).build();
    }

    @Test
    void criarComNomeSimilarSemAcentoLancaExcecaoComSugestao() {
        when(logradouroRepository.findById(logradouroId)).thenReturn(Optional.of(logradouro));
        when(comunidadeRepository.findAllNames()).thenReturn(List.of("Comunidade Esperança"));

        NomeSimilarException ex = assertThrows(NomeSimilarException.class,
                () -> comunidadeService.criar(new ComunidadeRequestDTO("comunidade esperanca", logradouroId)));

        assertTrue(ex.getMessage().contains("Comunidade Esperança"));
        assertTrue(ex.getMessage().contains("Rio Pomba"));
        verify(comunidadeRepository, never()).save(any());
    }

    @Test
    void criarComNomeDiferenteSalvaComoPendente() {
        when(logradouroRepository.findById(logradouroId)).thenReturn(Optional.of(logradouro));
        when(comunidadeRepository.findAllNames()).thenReturn(List.of("Quilombo do Campinho"));
        when(comunidadeRepository.save(any(Comunidade.class))).thenAnswer(inv -> inv.getArgument(0));

        ComunidadeResponseDTO resposta = comunidadeService.criar(new ComunidadeRequestDTO("Vale Verde", logradouroId));

        assertEquals("Vale Verde", resposta.nome());
        assertEquals(StatusComunidade.PENDENTE_APROVACAO, resposta.status());
    }

    @Test
    void criarComEnderecoCriaOLogradouroDaComunidade() {
        LogradouroDTO endereco = new LogradouroDTO();
        endereco.setLogradouro("Córrego Fundo");
        endereco.setMunicipio("Mercês");
        endereco.setUf("MG");
        when(logradouroRepository.save(any(Logradouro.class))).thenAnswer(inv -> inv.getArgument(0));
        when(comunidadeRepository.findAllNames()).thenReturn(List.of("Quilombo do Campinho"));
        when(comunidadeRepository.save(any(Comunidade.class))).thenAnswer(inv -> inv.getArgument(0));

        ComunidadeResponseDTO resposta = comunidadeService.criar(new ComunidadeRequestDTO("Vale Verde", null, endereco));

        assertEquals("Mercês", resposta.logradouroMunicipio());
        assertEquals(StatusComunidade.PENDENTE_APROVACAO, resposta.status());
        verify(logradouroRepository, never()).findById(any());
    }

    @Test
    void criarSemEnderecoNemLogradouroIdRecusa() {
        assertThrows(IllegalArgumentException.class,
                () -> comunidadeService.criar(new ComunidadeRequestDTO("Vale Verde", null)));
        verify(comunidadeRepository, never()).save(any());
    }

    @Test
    void atualizarParaNomeSimilarAOutraComunidadeLancaExcecao() {
        when(comunidadeRepository.findById(comunidadeId))
                .thenReturn(Optional.of(comunidade("Vale Verde", StatusComunidade.ATIVA)));
        when(comunidadeRepository.findAllNamesExceto(comunidadeId)).thenReturn(List.of("Quilombo do Campinho"));

        assertThrows(NomeSimilarException.class,
                () -> comunidadeService.atualizar(comunidadeId, new ComunidadeRequestDTO("Quilombo do Campinho", logradouroId)));

        verify(comunidadeRepository, never()).save(any());
    }

    @Test
    void deletarComPropriedadesVinculadasListaAsDependencias() {
        when(comunidadeRepository.findById(comunidadeId))
                .thenReturn(Optional.of(comunidade("Vale Verde", StatusComunidade.ATIVA)));
        when(propriedadeRepository.findNomesByComunidadeId(comunidadeId)).thenReturn(List.of("Sítio A", "Sítio B"));

        DependenciaVinculadaException ex = assertThrows(DependenciaVinculadaException.class,
                () -> comunidadeService.deletar(comunidadeId));

        assertTrue(ex.getMessage().contains("Sítio A"));
        assertTrue(ex.getMessage().contains("Sítio B"));
        verify(comunidadeRepository, never()).delete(any());
    }

    @Test
    void deletarSemDependenciasExclui() {
        Comunidade c = comunidade("Vale Verde", StatusComunidade.ATIVA);
        when(comunidadeRepository.findById(comunidadeId)).thenReturn(Optional.of(c));
        when(propriedadeRepository.findNomesByComunidadeId(comunidadeId)).thenReturn(List.of());

        comunidadeService.deletar(comunidadeId);

        verify(comunidadeRepository).delete(c);
    }

    @Test
    void aprovarComunidadePendenteAtivaEPreencheDataAprovacao() {
        when(comunidadeRepository.findById(comunidadeId))
                .thenReturn(Optional.of(comunidade("Vale Verde", StatusComunidade.PENDENTE_APROVACAO)));
        when(comunidadeRepository.save(any(Comunidade.class))).thenAnswer(inv -> inv.getArgument(0));

        ComunidadeResponseDTO resposta = comunidadeService.aprovar(comunidadeId);

        assertEquals(StatusComunidade.ATIVA, resposta.status());
        assertNotNull(resposta.dataAprovacao());
    }

    @Test
    void aprovarComunidadeJaRejeitadaLancaTransicaoInvalida() {
        when(comunidadeRepository.findById(comunidadeId))
                .thenReturn(Optional.of(comunidade("Vale Verde", StatusComunidade.REJEITADA)));

        assertThrows(TransicaoStatusInvalidaException.class, () -> comunidadeService.aprovar(comunidadeId));

        verify(comunidadeRepository, never()).save(any());
    }

    @Test
    void buscarPorIdInexistenteLancaResourceNotFound() {
        when(comunidadeRepository.findById(comunidadeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> comunidadeService.buscarPorId(comunidadeId));
    }

    @Test
    void buscarPorIdExistenteRetornaDTO() {
        when(comunidadeRepository.findById(comunidadeId))
                .thenReturn(Optional.of(comunidade("Vale Verde", StatusComunidade.ATIVA)));

        ComunidadeResponseDTO resposta = comunidadeService.buscarPorId(comunidadeId);

        assertEquals("Vale Verde", resposta.nome());
    }

    @Test
    void listarRetornaTodasConvertidas() {
        when(comunidadeRepository.findAll()).thenReturn(List.of(
                comunidade("Vale Verde", StatusComunidade.ATIVA),
                comunidade("Quilombo do Campinho", StatusComunidade.PENDENTE_APROVACAO)
        ));

        List<ComunidadeResponseDTO> resultado = comunidadeService.listar();

        assertEquals(2, resultado.size());
    }

    @Test
    void atualizarComNomeIgualNaoRevalidaSimilaridade() {
        Comunidade existente = comunidade("Vale Verde", StatusComunidade.ATIVA);
        when(comunidadeRepository.findById(comunidadeId)).thenReturn(Optional.of(existente));
        when(comunidadeRepository.save(any(Comunidade.class))).thenAnswer(inv -> inv.getArgument(0));

        comunidadeService.atualizar(comunidadeId, new ComunidadeRequestDTO("Vale Verde", logradouroId));

        verify(comunidadeRepository, never()).findAllNamesExceto(any());
        verify(comunidadeRepository).save(any(Comunidade.class));
    }

    @Test
    void rejeitarComunidadePendenteMudaStatus() {
        when(comunidadeRepository.findById(comunidadeId))
                .thenReturn(Optional.of(comunidade("Vale Verde", StatusComunidade.PENDENTE_APROVACAO)));
        when(comunidadeRepository.save(any(Comunidade.class))).thenAnswer(inv -> inv.getArgument(0));

        ComunidadeResponseDTO resposta = comunidadeService.rejeitar(comunidadeId);

        assertEquals(StatusComunidade.REJEITADA, resposta.status());
    }
}