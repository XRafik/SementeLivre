package com.sementelivre.backend.service;

import com.sementelivre.backend.dto.LogradouroDTO;
import com.sementelivre.backend.dto.PessoaUpdateRequestDTO;
import com.sementelivre.backend.exception.EmailJaCadastradoException;
import com.sementelivre.backend.exception.PessoaNaoEncontradaException;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.Pessoa;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.repository.CompradorRepository;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.ItensRepository;
import com.sementelivre.backend.repository.PedidoRepository;
import com.sementelivre.backend.exception.DependenciaVinculadaException;
import com.sementelivre.backend.repository.LogradouroRepository;
import com.sementelivre.backend.repository.NotificacaoRepository;
import com.sementelivre.backend.repository.PropriedadeRepository;
import com.sementelivre.backend.repository.RelatorioRepository;
import com.sementelivre.backend.repository.PessoaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PessoaServiceTest {

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private LogradouroRepository logradouroRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private EstoqueRepository estoqueRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private RelatorioRepository relatorioRepository;

    @Mock
    private PropriedadeRepository propriedadeRepository;

    @Mock
    private ItensRepository itensRepository;

    @Mock
    private CompradorRepository compradorRepository;

    @InjectMocks
    private PessoaService pessoaService;

    private Proprietario pessoaExistente(UUID id, String email) {
        Proprietario pessoa = new Proprietario();
        pessoa.setId(id);
        pessoa.setNome("Nome Antigo");
        pessoa.setEmail(email);
        return pessoa;
    }

    @Test
    public void deveRemoverContaDeLoginAntesDeExcluirPessoa() {
        UUID id = UUID.randomUUID();
        Proprietario pessoa = pessoaExistente(id, "excluir@teste.com");
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoa));

        pessoaService.deletar(id);

        InOrder ordem = inOrder(usuarioService, pessoaRepository);
        ordem.verify(usuarioService).excluirContaDaPessoa(id);
        ordem.verify(pessoaRepository).delete(pessoa);
    }

    @Test
    public void deveLancarExcecaoQuandoNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pessoaService.buscarPorId(id))
                .isInstanceOf(PessoaNaoEncontradaException.class)
                .hasMessage("Pessoa não encontrada com o ID: " + id);
    }

    @Test
    public void deveLancarExcecaoAoAtualizarPessoaInexistente() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.findById(id)).thenReturn(Optional.empty());

        PessoaUpdateRequestDTO dto = new PessoaUpdateRequestDTO();
        dto.setNome("Nome Novo");
        dto.setEmail("novo@teste.com");

        assertThatThrownBy(() -> pessoaService.atualizar(id, dto))
                .isInstanceOf(PessoaNaoEncontradaException.class);
    }

    @Test
    public void deveAtualizarComSucessoQuandoEmailMudaParaUmDisponivel() {
        UUID id = UUID.randomUUID();
        Proprietario pessoa = pessoaExistente(id, "antigo@teste.com");
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoa));
        when(pessoaRepository.existsByEmailIgnoreCase("novo@teste.com")).thenReturn(false);
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LogradouroDTO endereco = new LogradouroDTO();
        endereco.setLogradouro("Rua Nova");
        endereco.setMunicipio("Cidade Nova");
        endereco.setUf("MG");

        PessoaUpdateRequestDTO dto = new PessoaUpdateRequestDTO();
        dto.setNome("Nome Novo");
        dto.setTelefone("32999998888");
        dto.setEmail("novo@teste.com");
        dto.setEndereco(endereco);

        Pessoa atualizado = pessoaService.atualizar(id, dto);

        assertThat(atualizado.getNome()).isEqualTo("Nome Novo");
        assertThat(atualizado.getTelefone()).isEqualTo("32999998888");
        assertThat(atualizado.getEmail()).isEqualTo("novo@teste.com");
        assertThat(atualizado.getLogradouro()).isNotNull();
        assertThat(atualizado.getLogradouro().getLogradouro()).isEqualTo("Rua Nova");
        verify(logradouroRepository).save(any(Logradouro.class));
    }

    @Test
    public void deveLancarConflitoAoAtualizarParaEmailDeOutraPessoa() {
        UUID id = UUID.randomUUID();
        Proprietario pessoa = pessoaExistente(id, "antigo@teste.com");
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoa));
        when(pessoaRepository.existsByEmailIgnoreCase("jaexiste@teste.com")).thenReturn(true);

        PessoaUpdateRequestDTO dto = new PessoaUpdateRequestDTO();
        dto.setNome("Nome Novo");
        dto.setEmail("jaexiste@teste.com");

        assertThatThrownBy(() -> pessoaService.atualizar(id, dto))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    public void deveAtualizarSemConflitoQuandoEmailPermaneceOMesmo() {
        UUID id = UUID.randomUUID();
        Proprietario pessoa = pessoaExistente(id, "mesmo@teste.com");
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoa));
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PessoaUpdateRequestDTO dto = new PessoaUpdateRequestDTO();
        dto.setNome("Nome Atualizado");
        dto.setEmail("mesmo@teste.com");

        Pessoa atualizado = pessoaService.atualizar(id, dto);

        assertThat(atualizado.getNome()).isEqualTo("Nome Atualizado");
        assertThat(atualizado.getEmail()).isEqualTo("mesmo@teste.com");
        verify(pessoaRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    public void deveLancarConflitoAoAtualizarParaEmailDeOutraPessoaComOutraCaixa() {
        UUID id = UUID.randomUUID();
        Proprietario pessoa = pessoaExistente(id, "antigo@teste.com");
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoa));
        when(pessoaRepository.existsByEmailIgnoreCase("jaexiste@teste.com")).thenReturn(true);

        PessoaUpdateRequestDTO dto = new PessoaUpdateRequestDTO();
        dto.setNome("Nome Novo");
        dto.setEmail(" JaExiste@Teste.com ");

        assertThatThrownBy(() -> pessoaService.atualizar(id, dto))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    public void naoDeveExcluirPessoaComVinculosEDeveDizerQuais() {
        UUID id = UUID.randomUUID();
        Proprietario pessoa = pessoaExistente(id, "vinculos@teste.com");
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoa));
        when(estoqueRepository.countByProprietarioId(id)).thenReturn(2L);
        when(pedidoRepository.countEnvolvendoPessoa(id)).thenReturn(1L);

        assertThatThrownBy(() -> pessoaService.deletar(id))
                .isInstanceOf(DependenciaVinculadaException.class)
                .hasMessageContaining("2 estoque(s)")
                .hasMessageContaining("1 pedido(s)");
        verify(usuarioService, never()).excluirContaDaPessoa(any());
        verify(pessoaRepository, never()).delete(any());
    }

    @Test
    public void exclusaoDeContaDeveApagarItensAntesDosPedidosELimparDadosOrfaos() {
        UUID id = UUID.randomUUID();
        UUID compradorId = UUID.randomUUID();
        UUID enderecoPropriedade = UUID.randomUUID();
        Proprietario pessoa = pessoaExistente(id, "conta@teste.com");
        Logradouro endereco = new Logradouro();
        endereco.setId(UUID.randomUUID());
        pessoa.setLogradouro(endereco);
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoa));
        when(pedidoRepository.findCompradorIdsEnvolvendoPessoa(id)).thenReturn(List.of(compradorId));
        when(propriedadeRepository.findLogradouroIdsByProprietarioId(id)).thenReturn(List.of(enderecoPropriedade));

        pessoaService.excluirConta(id);

        InOrder ordem = inOrder(itensRepository, pedidoRepository, usuarioService, pessoaRepository,
                compradorRepository, logradouroRepository);
        ordem.verify(itensRepository).deleteAllDePedidosEnvolvendoPessoa(id);
        ordem.verify(pedidoRepository).deleteAllEnvolvendoPessoa(id);
        ordem.verify(usuarioService).excluirContaDaPessoa(id);
        ordem.verify(pessoaRepository).deleteById(id);
        ordem.verify(compradorRepository).deleteSemPedidoByIdIn(List.of(compradorId));
        ordem.verify(logradouroRepository).deleteSemUsoByIdIn(List.of(endereco.getId(), enderecoPropriedade));
    }

    @Test
    public void exclusaoDeContaSemPedidosNaoMexeEmCompradores() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoaExistente(id, "sem@teste.com")));

        pessoaService.excluirConta(id);

        verify(compradorRepository, never()).deleteSemPedidoByIdIn(anyCollection());
        verify(logradouroRepository, never()).deleteSemUsoByIdIn(anyCollection());
    }
}
