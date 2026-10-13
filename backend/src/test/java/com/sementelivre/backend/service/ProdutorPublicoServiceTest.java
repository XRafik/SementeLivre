package com.sementelivre.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sementelivre.backend.dto.ProdutorPublicoResponseDTO;
import com.sementelivre.backend.dto.SementePublicaResponseDTO;
import com.sementelivre.backend.entity.Comunidade;
import com.sementelivre.backend.entity.Estoque;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.Produto;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.FormatoProduto;
import com.sementelivre.backend.entity.enums.TipoProduto;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.exception.ResourceNotFoundException;
import com.sementelivre.backend.repository.PropriedadeRepository;
import com.sementelivre.backend.repository.ProprietarioRepository;

/**
 * Regras de visibilidade do perfil publico do produtor (issue #91).
 *
 * Mockito puro, sem contexto Spring: a suite de contexto cheio esbarra em
 * dividas de infra pre-existentes (JavaMailSender ausente no profile de teste
 * e CREATE EXTENSION do Flyway sobre H2) que nao sao desta issue.
 */
@ExtendWith(MockitoExtension.class)
class ProdutorPublicoServiceTest {

    @Mock
    private ProprietarioRepository proprietarioRepository;

    @Mock
    private PropriedadeRepository propriedadeRepository;

    @Mock
    private EstoqueRepository estoqueRepository;

    private ProdutorPublicoService produtorPublicoService;

    private UUID produtorId;
    private Proprietario produtor;

    @BeforeEach
    void setUp() {
        produtorPublicoService = new ProdutorPublicoService(
                proprietarioRepository,
                propriedadeRepository,
                estoqueRepository);

        produtorId = UUID.randomUUID();

        Logradouro endereco = Logradouro.builder()
                .logradouro("Estrada da Serra")
                .municipio("Rio Pomba")
                .uf("MG")
                .build();

        produtor = new Proprietario();
        produtor.setId(produtorId);
        produtor.setNome("Maria das Sementes");
        produtor.setExibirNoSitePublico(true);
        produtor.setLogradouro(endereco);
    }

    // ---------- caso feliz ----------

    @Test
    void deveMontarPerfilPublicoDeProdutorVisivel() {
        Comunidade comunidade = Comunidade.builder()
                .id(UUID.randomUUID())
                .nome("Quilombo São José")
                .build();

        when(proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(produtorId))
                .thenReturn(Optional.of(produtor));
        when(propriedadeRepository.findComunidadesByProprietarioId(produtorId))
                .thenReturn(List.of(comunidade));
        when(estoqueRepository.findVisiveisNoSitePublico(produtorId))
                .thenReturn(List.of(estoqueDe("Feijão Preto", Disponibilidade.PARA_TROCA)));

        ProdutorPublicoResponseDTO perfil = produtorPublicoService.buscarPerfilPublico(produtorId);

        assertThat(perfil.id()).isEqualTo(produtorId);
        assertThat(perfil.nome()).isEqualTo("Maria das Sementes");
        assertThat(perfil.municipio()).isEqualTo("Rio Pomba");
        assertThat(perfil.comunidades()).singleElement()
                .satisfies(c -> assertThat(c.nome()).isEqualTo("Quilombo São José"));
        assertThat(perfil.sementes()).singleElement()
                .satisfies(s -> {
                    assertThat(s.nomePopular()).isEqualTo("Feijão Preto");
                    assertThat(s.disponibilidade()).isEqualTo(Disponibilidade.PARA_TROCA);
                    assertThat(s.formato()).isEqualTo(FormatoProduto.SEMENTE);
                });
    }

    // ---------- 404: privado e inexistente sao indistinguiveis ----------

    @Test
    void produtorPrivadoDeveSer404() {
        // O repositorio ja aplica o filtro exibirNoSitePublico = true, entao um
        // produtor privado chega aqui como Optional.empty -- exatamente igual a
        // um produtor que nao existe. E essa indistinguibilidade que impede o
        // endpoint de revelar que a conta existe.
        when(proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(produtorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtorPublicoService.buscarPerfilPublico(produtorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(produtorId.toString());

        verify(propriedadeRepository, never()).findComunidadesByProprietarioId(any());
        verify(estoqueRepository, never()).findVisiveisNoSitePublico(any());
    }

    @Test
    void produtorInexistenteDeveSer404() {
        UUID inexistente = UUID.randomUUID();
        when(proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(inexistente))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtorPublicoService.buscarPerfilPublico(inexistente))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listarSementesDeProdutorPrivadoDeveSer404SemConsultarEstoque() {
        when(proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(produtorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtorPublicoService.listarSementes(produtorId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(estoqueRepository, never()).findVisiveisNoSitePublico(any());
    }

    // ---------- filtro de disponibilidade ----------

    @Test
    void listarSementesDeveDelegarOFiltroDeIndisponivelAoRepositorio() {
        // A regra "INDISPONIVEL fica de fora" vive na query do repositorio
        // (findVisiveisNoSitePublico). O que este teste garante e que o service
        // usa essa consulta ja filtrada, em vez de chamar findAll e filtrar em
        // memoria -- o que vazaria estoque indisponivel se alguem trocasse o
        // metodo sem perceber.
        when(proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(produtorId))
                .thenReturn(Optional.of(produtor));
        when(estoqueRepository.findVisiveisNoSitePublico(produtorId))
                .thenReturn(List.of(
                        estoqueDe("Milho Crioulo", Disponibilidade.PARA_DOACAO),
                        estoqueDe("Abóbora Moranga", Disponibilidade.A_NEGOCIAR)));

        List<SementePublicaResponseDTO> sementes = produtorPublicoService.listarSementes(produtorId);

        assertThat(sementes).hasSize(2);
        assertThat(sementes).extracting(SementePublicaResponseDTO::disponibilidade)
                .doesNotContain(Disponibilidade.INDISPONIVEL);
        verify(estoqueRepository).findVisiveisNoSitePublico(produtorId);
    }

    // ---------- listas vazias ----------

    @Test
    void produtorPublicoSemSementesNemComunidadesDeveDevolverListasVazias() {
        when(proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(produtorId))
                .thenReturn(Optional.of(produtor));
        when(propriedadeRepository.findComunidadesByProprietarioId(produtorId))
                .thenReturn(List.of());
        when(estoqueRepository.findVisiveisNoSitePublico(produtorId))
                .thenReturn(List.of());

        ProdutorPublicoResponseDTO perfil = produtorPublicoService.buscarPerfilPublico(produtorId);

        assertThat(perfil.comunidades()).isEmpty();
        assertThat(perfil.sementes()).isEmpty();
    }

    @Test
    void listarSementesDeProdutorPublicoSemEstoqueDeveDevolverListaVazia() {
        when(proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(produtorId))
                .thenReturn(Optional.of(produtor));
        when(estoqueRepository.findVisiveisNoSitePublico(produtorId))
                .thenReturn(List.of());

        assertThat(produtorPublicoService.listarSementes(produtorId)).isEmpty();
    }

    // ---------- endereco ausente ----------

    @Test
    void produtorSemLogradouroDeveTerMunicipioNulo() {
        produtor.setLogradouro(null);

        when(proprietarioRepository.findByIdAndExibirNoSitePublicoTrue(produtorId))
                .thenReturn(Optional.of(produtor));
        when(propriedadeRepository.findComunidadesByProprietarioId(produtorId))
                .thenReturn(List.of());
        when(estoqueRepository.findVisiveisNoSitePublico(produtorId))
                .thenReturn(List.of());

        assertThat(produtorPublicoService.buscarPerfilPublico(produtorId).municipio()).isNull();
    }

    private Estoque estoqueDe(String nomePopular, Disponibilidade disponibilidade) {
        Produto produto = Produto.builder()
                .id(UUID.randomUUID())
                .nomePopular(nomePopular)
                .nomeCientifico("Nome cientifico")
                .formato(FormatoProduto.SEMENTE)
                .tipo(TipoProduto.LEGUMINOSA)
                .especie(EspecieGeral.FEIJAO)
                .familiaBotanica("Fabaceae")
                .urlFoto("https://exemplo/foto.jpg")
                .build();

        return Estoque.builder()
                .id(UUID.randomUUID())
                .proprietario(produtor)
                .produto(produto)
                .disponibilidade(disponibilidade)
                .build();
    }
}
