package com.sementelivre.backend.service;

import com.sementelivre.backend.dto.LogradouroDTO;
import com.sementelivre.backend.dto.ProprietarioCreateRequestDTO;
import com.sementelivre.backend.exception.DocumentoJaCadastradoException;
import com.sementelivre.backend.exception.EmailJaCadastradoException;
import com.sementelivre.backend.exception.RgJaCadastradoException;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.enums.PerfilEnum;
import com.sementelivre.backend.entity.enums.TipoDocumento;
import com.sementelivre.backend.repository.LogradouroRepository;
import com.sementelivre.backend.repository.PessoaRepository;
import com.sementelivre.backend.repository.ProprietarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProprietarioServiceTest {

    @Mock
    private ProprietarioRepository proprietarioRepository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private LogradouroRepository logradouroRepository;

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private ProprietarioService proprietarioService;

    private ProprietarioCreateRequestDTO dtoValido() {
        ProprietarioCreateRequestDTO dto = new ProprietarioCreateRequestDTO();
        dto.setNome("Teste Proprietario");
        dto.setTipoDocumento(TipoDocumento.CPF);
        dto.setDocumento("52998224725");
        dto.setEmail("prop@email.com");
        dto.setSenha("senhaSegura123");
        dto.setRg("MG-123456");
        return dto;
    }

    @Test
    public void deveLancarConflitoQuandoEmailDuplicado() {
        ProprietarioCreateRequestDTO dto = dtoValido();
        when(pessoaRepository.existsByEmailIgnoreCase(dto.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> proprietarioService.criar(dto))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    public void deveLancarConflitoQuandoDocumentoDuplicado() {
        ProprietarioCreateRequestDTO dto = dtoValido();
        when(pessoaRepository.existsByEmailIgnoreCase(dto.getEmail())).thenReturn(false);
        when(pessoaRepository.existsByDocumento(dto.getDocumento())).thenReturn(true);

        assertThatThrownBy(() -> proprietarioService.criar(dto))
                .isInstanceOf(DocumentoJaCadastradoException.class);
    }

    @Test
    public void deveLancarConflitoQuandoRgDuplicado() {
        ProprietarioCreateRequestDTO dto = dtoValido();
        lenient().when(pessoaRepository.existsByEmailIgnoreCase(dto.getEmail())).thenReturn(false);
        lenient().when(pessoaRepository.existsByDocumento(dto.getDocumento())).thenReturn(false);
        when(proprietarioRepository.existsByRg(dto.getRg())).thenReturn(true);

        assertThatThrownBy(() -> proprietarioService.criar(dto))
                .isInstanceOf(RgJaCadastradoException.class)
                .hasMessageContaining("RG");
    }

    @Test
    public void deveRejeitarDocumentoComDigitoVerificadorInvalido() {
        ProprietarioCreateRequestDTO dto = dtoValido();
        dto.setDocumento("52998224726");

        assertThatThrownBy(() -> proprietarioService.criar(dto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void deveCriarProprietarioComSucessoComTodosOsCampos() {
        ProprietarioCreateRequestDTO dto = dtoValido();
        dto.setTelefone("(32) 99999-0000");
        dto.setExibirNoSitePublico(true);
        LogradouroDTO endereco = new LogradouroDTO();
        endereco.setLogradouro("Rua A");
        endereco.setMunicipio("Cidade");
        endereco.setUf("MG");
        dto.setEndereco(endereco);

        when(pessoaRepository.existsByEmailIgnoreCase(dto.getEmail())).thenReturn(false);
        when(pessoaRepository.existsByDocumento(dto.getDocumento())).thenReturn(false);
        when(proprietarioRepository.existsByRg(dto.getRg())).thenReturn(false);
        when(proprietarioRepository.save(any(Proprietario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Proprietario proprietario = proprietarioService.criar(dto);

        assertThat(proprietario.getNome()).isEqualTo(dto.getNome());
        assertThat(proprietario.getTipoDocumento()).isEqualTo(TipoDocumento.CPF);
        assertThat(proprietario.getDocumento()).isEqualTo(dto.getDocumento());
        assertThat(proprietario.getTelefone()).isEqualTo(dto.getTelefone());
        assertThat(proprietario.getEmail()).isEqualTo(dto.getEmail());
        assertThat(proprietario.getRg()).isEqualTo(dto.getRg());
        assertThat(proprietario.isExibirNoSitePublico()).isTrue();
        // A senha vai para a conta de login do proprietario, não para a pessoa
        verify(usuarioService).criarConta(proprietario, dto.getSenha(), PerfilEnum.ROLE_PROPRIETARIO);
        assertThat(proprietario.getLogradouro()).isNotNull();
        assertThat(proprietario.getLogradouro().getLogradouro()).isEqualTo("Rua A");
        verify(logradouroRepository).save(any(Logradouro.class));
    }

    @Test
    public void deveGravarEmailNormalizadoEBarrarDuplicadoComOutraCaixa() {
        ProprietarioCreateRequestDTO dto = dtoValido();
        dto.setEmail("  Prop@Email.COM ");
        when(pessoaRepository.existsByEmailIgnoreCase("prop@email.com")).thenReturn(true);

        assertThatThrownBy(() -> proprietarioService.criar(dto))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    public void deveGravarEmailEmMinusculasESemEspacos() {
        ProprietarioCreateRequestDTO dto = dtoValido();
        dto.setEmail("  Prop@Email.COM ");
        when(pessoaRepository.existsByEmailIgnoreCase("prop@email.com")).thenReturn(false);
        when(pessoaRepository.existsByDocumento(dto.getDocumento())).thenReturn(false);
        when(proprietarioRepository.existsByRg(dto.getRg())).thenReturn(false);
        when(proprietarioRepository.save(any(Proprietario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Proprietario proprietario = proprietarioService.criar(dto);

        assertThat(proprietario.getEmail()).isEqualTo("prop@email.com");
    }
}
