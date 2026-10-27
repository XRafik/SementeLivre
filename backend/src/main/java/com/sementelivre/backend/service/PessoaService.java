package com.sementelivre.backend.service;

import com.sementelivre.backend.dto.LogradouroDTO;
import com.sementelivre.backend.dto.PessoaUpdateRequestDTO;
import com.sementelivre.backend.exception.DependenciaVinculadaException;
import com.sementelivre.backend.exception.EmailJaCadastradoException;
import com.sementelivre.backend.exception.PessoaNaoEncontradaException;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.Pessoa;
import com.sementelivre.backend.repository.CompradorRepository;
import com.sementelivre.backend.repository.EstoqueRepository;
import com.sementelivre.backend.repository.ItensRepository;
import com.sementelivre.backend.repository.PedidoRepository;
import com.sementelivre.backend.repository.LogradouroRepository;
import com.sementelivre.backend.repository.NotificacaoRepository;
import com.sementelivre.backend.repository.PessoaRepository;
import com.sementelivre.backend.repository.PropriedadeRepository;
import com.sementelivre.backend.repository.RelatorioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PessoaService {

    private final PessoaRepository pessoaRepository;
    private final LogradouroRepository logradouroRepository;
    private final UsuarioService usuarioService;
    private final EstoqueRepository estoqueRepository;
    private final PedidoRepository pedidoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final RelatorioRepository relatorioRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final ItensRepository itensRepository;
    private final CompradorRepository compradorRepository;

    public PessoaService(PessoaRepository pessoaRepository, LogradouroRepository logradouroRepository,
                         UsuarioService usuarioService, EstoqueRepository estoqueRepository,
                         PedidoRepository pedidoRepository, NotificacaoRepository notificacaoRepository,
                         RelatorioRepository relatorioRepository, PropriedadeRepository propriedadeRepository,
                         ItensRepository itensRepository, CompradorRepository compradorRepository) {
        this.pessoaRepository = pessoaRepository;
        this.logradouroRepository = logradouroRepository;
        this.usuarioService = usuarioService;
        this.estoqueRepository = estoqueRepository;
        this.pedidoRepository = pedidoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.relatorioRepository = relatorioRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.itensRepository = itensRepository;
        this.compradorRepository = compradorRepository;
    }

    @Transactional(readOnly = true)
    public List<Pessoa> listarTodas() {
        return pessoaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Pessoa buscarPorId(UUID id) {
        return pessoaRepository.findById(id)
                .orElseThrow(() -> new PessoaNaoEncontradaException("Pessoa não encontrada com o ID: " + id));
    }

    @Transactional
    public Pessoa atualizar(UUID id, PessoaUpdateRequestDTO dto) {
        Pessoa pessoa = buscarPorId(id);

        String email = Pessoa.normalizarEmail(dto.getEmail());
        if (!pessoa.getEmail().equalsIgnoreCase(email) && pessoaRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailJaCadastradoException("E-mail já cadastrado no sistema.");
        }

        pessoa.setNome(dto.getNome());
        pessoa.setTelefone(dto.getTelefone());
        pessoa.setEmail(dto.getEmail());

        if (dto.getEndereco() != null) {
            Logradouro logradouro = pessoa.getLogradouro();
            if (logradouro == null) {
                logradouro = new Logradouro();
            }
            mapLogradouro(dto.getEndereco(), logradouro);
            logradouroRepository.save(logradouro);
            pessoa.setLogradouro(logradouro);
        }

        return pessoaRepository.save(pessoa);
    }

    /**
     * Exclusão administrativa: só apaga pessoa sem dados vinculados. Para levar
     * junto estoque, pedidos etc., existe {@link #excluirConta}.
     */
    @Transactional
    public void deletar(UUID id) {
        Pessoa pessoa = buscarPorId(id);
        validarSemVinculos(pessoa);

        List<UUID> enderecos = enderecoDa(pessoa);
        usuarioService.excluirContaDaPessoa(id);
        pessoaRepository.delete(pessoa);
        removerEnderecosSemUso(enderecos);
    }

    /**
     * Exclusão de conta a pedido do titular (direito ao esquecimento, LGPD).
     *
     * <p>Todas as tabelas filhas usam ON DELETE RESTRICT, então a conta só sai
     * depois que os dados operacionais da pessoa são removidos. A ordem importa:
     * itens e pedidos primeiro, depois o que o proprietário possui, login e
     * cadastro. Por último, comprador e endereços que ficaram sem uso: são dados
     * pessoais e as FKs para eles não apagam nada sozinhas.</p>
     *
     * <p>Os produtos em si não são apagados: são catálogo compartilhado entre
     * produtores e não pertencem à pessoa. O que sai é o estoque dela.</p>
     */
    @Transactional
    public void excluirConta(UUID id) {
        Pessoa pessoa = buscarPorId(id);

        // Coletados antes: depois dos deletes não há mais como chegar neles.
        List<UUID> compradores = pedidoRepository.findCompradorIdsEnvolvendoPessoa(id);
        List<UUID> enderecos = enderecoDa(pessoa);
        enderecos.addAll(propriedadeRepository.findLogradouroIdsByProprietarioId(id));

        itensRepository.deleteAllDePedidosEnvolvendoPessoa(id);
        pedidoRepository.deleteAllEnvolvendoPessoa(id);
        notificacaoRepository.deleteAllByProprietarioId(id);
        relatorioRepository.deleteAllByProprietarioId(id);
        propriedadeRepository.deleteAllByProprietarioId(id);
        estoqueRepository.deleteAllByProprietarioId(id);

        usuarioService.excluirContaDaPessoa(id);
        pessoaRepository.deleteById(id);

        if (!compradores.isEmpty()) {
            compradorRepository.deleteSemPedidoByIdIn(compradores);
        }
        removerEnderecosSemUso(enderecos);
    }

    // As FKs para pessoa são ON DELETE RESTRICT: sem esta checagem o banco recusava
    // a exclusão e a API respondia só "Violação de integridade nos dados.".
    private void validarSemVinculos(Pessoa pessoa) {
        UUID id = pessoa.getId();
        List<String> vinculos = new ArrayList<>();
        adicionarVinculo(vinculos, estoqueRepository.countByProprietarioId(id), "estoque(s)");
        adicionarVinculo(vinculos, pedidoRepository.countEnvolvendoPessoa(id), "pedido(s)");
        adicionarVinculo(vinculos, propriedadeRepository.countByProprietarioId(id), "propriedade(s)");
        adicionarVinculo(vinculos, notificacaoRepository.countByProprietarioId(id), "notificação(ões)");
        adicionarVinculo(vinculos, relatorioRepository.countByProprietarioId(id), "relatório(s)");

        if (!vinculos.isEmpty()) {
            throw new DependenciaVinculadaException(
                    "Não é possível excluir \"" + pessoa.getNome() + "\", pois a pessoa possui: "
                            + String.join(", ", vinculos)
                            + ". Para apagar a conta com todos os dados, use a exclusão de conta.");
        }
    }

    private static void adicionarVinculo(List<String> vinculos, long quantidade, String descricao) {
        if (quantidade > 0) {
            vinculos.add(quantidade + " " + descricao);
        }
    }

    private static List<UUID> enderecoDa(Pessoa pessoa) {
        List<UUID> ids = new ArrayList<>();
        if (pessoa.getLogradouro() != null) {
            ids.add(pessoa.getLogradouro().getId());
        }
        return ids;
    }

    private void removerEnderecosSemUso(List<UUID> enderecos) {
        if (!enderecos.isEmpty()) {
            logradouroRepository.deleteSemUsoByIdIn(enderecos);
        }
    }

    private void mapLogradouro(LogradouroDTO dto, Logradouro logradouro) {
        logradouro.setLogradouro(dto.getLogradouro());
        logradouro.setNumero(dto.getNumero());
        logradouro.setComplemento(dto.getComplemento());
        logradouro.setBairro(dto.getBairro());
        logradouro.setMunicipio(dto.getMunicipio());
        logradouro.setUf(dto.getUf());
        logradouro.setCep(dto.getCep());
    }
}
