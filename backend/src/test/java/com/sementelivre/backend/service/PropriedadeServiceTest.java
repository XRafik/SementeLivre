package com.sementelivre.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sementelivre.backend.dto.PropriedadeRequestDTO;
import com.sementelivre.backend.dto.PropriedadeResponseDTO;
import com.sementelivre.backend.entity.Comunidade;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.Propriedade;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.PedidoRepository;
import com.sementelivre.backend.exception.DependenciaVinculadaException;
import com.sementelivre.backend.exception.ResourceNotFoundException;
import com.sementelivre.backend.repository.ComunidadeRepository;
import com.sementelivre.backend.repository.LogradouroRepository;
import com.sementelivre.backend.repository.PropriedadeRepository;
import com.sementelivre.backend.repository.ProprietarioRepository;

@ExtendWith(MockitoExtension.class)
class PropriedadeServiceTest {

    @Mock private PropriedadeRepository propriedadeRepository;
    @Mock private ComunidadeRepository comunidadeRepository;
    @Mock private ProprietarioRepository proprietarioRepository;
    @Mock private LogradouroRepository logradouroRepository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private PedidoRepository pedidoRepository;

    private PropriedadeService propriedadeService;

    private UUID propriedadeId;
    private UUID comunidadeId;
    private UUID proprietarioId;
    private UUID logradouroId;

    private Comunidade comunidade;
    private Proprietario proprietario;
    private Logradouro logradouro;

    @BeforeEach
    void setUp() {
        propriedadeService = new PropriedadeService(propriedadeRepository, comunidadeRepository,
                proprietarioRepository, logradouroRepository, estoqueRepository, pedidoRepository);

        propriedadeId = UUID.randomUUID();
        comunidadeId = UUID.randomUUID();
        proprietarioId = UUID.randomUUID();
        logradouroId = UUID.randomUUID();

        comunidade = Comunidade.builder().id(comunidadeId).nome("Vale Verde").build();
        proprietario = new Proprietario();
        proprietario.setId(proprietarioId);
        proprietario.setNome("João");
        logradouro = Logradouro.builder().id(logradouroId).uf("MG").municipio("Rio Pomba").build();
    }

    private Propriedade propriedade() {
        return Propriedade.builder()
                .id(propriedadeId)
                .nome("Sítio Boa Vista")
                .tamanhoHectares(BigDecimal.TEN)
                .comunidade(comunidade)
                .proprietario(proprietario)
                .logradouro(logradouro)
                .build();
    }

    private PropriedadeRequestDTO dto() {
        return new PropriedadeRequestDTO("Sítio Boa Vista", BigDecimal.TEN, logradouroId, proprietarioId, comunidadeId);
    }

    @Test
    void criarComReferenciasValidasSalva() {
        when(comunidadeRepository.findById(comunidadeId)).thenReturn(Optional.of(comunidade));
        when(proprietarioRepository.findById(proprietarioId)).thenReturn(Optional.of(proprietario));
        when(logradouroRepository.findById(logradouroId)).thenReturn(Optional.of(logradouro));
        when(propriedadeRepository.save(any(Propriedade.class))).thenAnswer(inv -> inv.getArgument(0));

        PropriedadeResponseDTO resposta = propriedadeService.criar(dto());

        assertEquals("Sítio Boa Vista", resposta.nome());
    }

    @Test
    void criarComComunidadeInexistenteLancaResourceNotFound() {
        when(comunidadeRepository.findById(comunidadeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> propriedadeService.criar(dto()));
        verify(propriedadeRepository, never()).save(any());
    }

    @Test
    void buscarPorIdInexistenteLancaResourceNotFound() {
        when(propriedadeRepository.findById(propriedadeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> propriedadeService.buscarPorId(propriedadeId));
    }

    @Test
    void listarRetornaTodasConvertidas() {
        when(propriedadeRepository.findAll()).thenReturn(List.of(propriedade()));

        List<PropriedadeResponseDTO> resultado = propriedadeService.listar();

        assertEquals(1, resultado.size());
    }

    @Test
    void atualizarComMesmasReferenciasNaoBuscaNovamente() {
        when(propriedadeRepository.findById(propriedadeId)).thenReturn(Optional.of(propriedade()));
        when(propriedadeRepository.save(any(Propriedade.class))).thenAnswer(inv -> inv.getArgument(0));

        propriedadeService.atualizar(propriedadeId, dto());

        verify(comunidadeRepository, never()).findById(any());
        verify(proprietarioRepository, never()).findById(any());
        verify(logradouroRepository, never()).findById(any());
    }

    @Test
    void deletarComEstoqueEPedidosListaAmbasAsDependencias() {
        when(propriedadeRepository.findById(propriedadeId)).thenReturn(Optional.of(propriedade()));
        when(estoqueRepository.countByProprietarioId(proprietarioId)).thenReturn(3L);
        when(pedidoRepository.countByProprietarioRecebedorId(proprietarioId)).thenReturn(2L);

        DependenciaVinculadaException ex = assertThrows(DependenciaVinculadaException.class,
                () -> propriedadeService.deletar(propriedadeId));

        assertTrue(ex.getMessage().contains("3 estoque(s)"));
        assertTrue(ex.getMessage().contains("2 pedido(s)"));
        verify(propriedadeRepository, never()).delete(any());
    }

    @Test
    void deletarSemDependenciasExclui() {
        Propriedade p = propriedade();
        when(propriedadeRepository.findById(propriedadeId)).thenReturn(Optional.of(p));
        when(estoqueRepository.countByProprietarioId(proprietarioId)).thenReturn(0L);
        when(pedidoRepository.countByProprietarioRecebedorId(proprietarioId)).thenReturn(0L);

        propriedadeService.deletar(propriedadeId);

        verify(propriedadeRepository).delete(p);
    }
}