package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.sementelivre.backend.dto.LogradouroDTO;
import com.sementelivre.backend.dto.PessoaResponseDTO;
import com.sementelivre.backend.dto.PessoaUpdateRequestDTO;
import com.sementelivre.backend.entity.Admin;
import com.sementelivre.backend.entity.Logradouro;
import com.sementelivre.backend.entity.Pessoa;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.service.PessoaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pessoas")
@Tag(name = "Pessoas", description = "Dados pessoais (titular ou admin) e exclusão de conta (LGPD).")
public class PessoaController {

    private final PessoaService pessoaService;

    public PessoaController(PessoaService pessoaService) {
        this.pessoaService = pessoaService;
    }

    // CPF, RG, e-mail e endereco de todo mundo: so admin lista.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PessoaResponseDTO>> listar() {
        List<PessoaResponseDTO> pessoas = pessoaService.listarTodas().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pessoas);
    }

    // Cada um consulta so os proprios dados; admin consulta qualquer um.
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<PessoaResponseDTO> buscarPorId(@PathVariable UUID id) {
        Pessoa pessoa = pessoaService.buscarPorId(id);
        return ResponseEntity.ok(mapToResponse(pessoa));
    }

    // Cada um edita so os proprios dados; admin edita qualquer um.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<PessoaResponseDTO> atualizar(@PathVariable UUID id, @Valid @RequestBody PessoaUpdateRequestDTO dto) {
        Pessoa pessoa = pessoaService.atualizar(id, dto);
        return ResponseEntity.ok(mapToResponse(pessoa));
    }

    // So admin exclui pessoas e contas (#60).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        pessoaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Exclusão de conta pelo proprio titular (LGPD, direito ao esquecimento).
     * Diferente de {@link #deletar}, apaga antes os dados operacionais da
     * pessoa, porque as chaves estrangeiras usam ON DELETE RESTRICT.
     */
    @DeleteMapping("/{id}/conta")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<Void> excluirConta(@PathVariable UUID id) {
        pessoaService.excluirConta(id);
        return ResponseEntity.noContent().build();
    }

    protected PessoaResponseDTO mapToResponse(Pessoa p) {
        PessoaResponseDTO dto = new PessoaResponseDTO();
        dto.setId(p.getId());
        dto.setTipoDocumento(p.getTipoDocumento());
        dto.setDocumento(p.getDocumento());
        dto.setNome(p.getNome());
        dto.setTelefone(p.getTelefone());
        dto.setEmail(p.getEmail());
        dto.setDataCadastro(p.getDataCadastro());
        dto.setDataUltimaAlteracao(p.getDataUltimaAlteracao());

        if (p instanceof Proprietario) {
            dto.setTipoPessoa("PROPRIETARIO");
        } else if (p instanceof Admin) {
            dto.setTipoPessoa("ADMIN");
        }

        if (p.getLogradouro() != null) {
            LogradouroDTO end = new LogradouroDTO();
            Logradouro l = p.getLogradouro();
            end.setLogradouro(l.getLogradouro());
            end.setNumero(l.getNumero());
            end.setComplemento(l.getComplemento());
            end.setBairro(l.getBairro());
            end.setMunicipio(l.getMunicipio());
            end.setUf(l.getUf());
            end.setCep(l.getCep());
            dto.setEndereco(end);
        }

        return dto;
    }
}
