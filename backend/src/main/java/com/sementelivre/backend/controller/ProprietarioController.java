package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.sementelivre.backend.dto.ProprietarioCreateRequestDTO;
import com.sementelivre.backend.dto.ProprietarioResponseDTO;
import com.sementelivre.backend.dto.PessoaUpdateRequestDTO;
import com.sementelivre.backend.entity.Proprietario;
import com.sementelivre.backend.entity.Pessoa;
import com.sementelivre.backend.service.PessoaService;
import com.sementelivre.backend.service.ProprietarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/proprietarios")
@Tag(name = "Proprietários", description = "Dados do proprietário (titular ou admin).")
public class ProprietarioController {

    private final ProprietarioService proprietarioService;
    private final PessoaService pessoaService;

    public ProprietarioController(ProprietarioService proprietarioService, PessoaService pessoaService) {
        this.proprietarioService = proprietarioService;
        this.pessoaService = pessoaService;
    }

    // O cadastro aberto e o POST /auth/cadastrar; por aqui so admin cria contas.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProprietarioResponseDTO> criar(@Valid @RequestBody ProprietarioCreateRequestDTO dto) {
        Proprietario proprietario = proprietarioService.criar(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(proprietario.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapToResponse(proprietario));
    }

    // CPF, RG, e-mail e endereco de todo mundo: so admin lista.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProprietarioResponseDTO>> listar() {
        List<ProprietarioResponseDTO> proprietarios = pessoaService.listarTodas().stream()
                .filter(p -> p instanceof Proprietario)
                .map(p -> mapToResponse((Proprietario) p))
                .collect(Collectors.toList());
        return ResponseEntity.ok(proprietarios);
    }

    // Cada um consulta so os proprios dados; admin consulta qualquer um.
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<ProprietarioResponseDTO> buscarPorId(@PathVariable UUID id) {
        Pessoa pessoa = pessoaService.buscarPorId(id);
        if (!(pessoa instanceof Proprietario)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapToResponse((Proprietario) pessoa));
    }

    // Cada um edita so os proprios dados; admin edita qualquer um.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<ProprietarioResponseDTO> atualizar(@PathVariable UUID id, @Valid @RequestBody PessoaUpdateRequestDTO dto) {
        Pessoa pessoa = pessoaService.buscarPorId(id);
        if (!(pessoa instanceof Proprietario)) {
            return ResponseEntity.notFound().build();
        }
        Proprietario atualizado = (Proprietario) pessoaService.atualizar(id, dto);
        return ResponseEntity.ok(mapToResponse(atualizado));
    }

    // So admin exclui pessoas e contas (#60).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        Pessoa pessoa = pessoaService.buscarPorId(id);
        if (!(pessoa instanceof Proprietario)) {
            return ResponseEntity.notFound().build();
        }
        pessoaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    private ProprietarioResponseDTO mapToResponse(Proprietario p) {
        ProprietarioResponseDTO dto = new ProprietarioResponseDTO();
        dto.setId(p.getId());
        dto.setTipoDocumento(p.getTipoDocumento());
        dto.setDocumento(p.getDocumento());
        dto.setNome(p.getNome());
        dto.setTelefone(p.getTelefone());
        dto.setEmail(p.getEmail());
        dto.setDataCadastro(p.getDataCadastro());
        dto.setDataUltimaAlteracao(p.getDataUltimaAlteracao());
        dto.setTipoPessoa("PROPRIETARIO");
        dto.setRg(p.getRg());
        dto.setExibirNoSitePublico(p.isExibirNoSitePublico());

        if (p.getLogradouro() != null) {
            com.sementelivre.backend.dto.LogradouroDTO end = new com.sementelivre.backend.dto.LogradouroDTO();
            end.setLogradouro(p.getLogradouro().getLogradouro());
            end.setNumero(p.getLogradouro().getNumero());
            end.setComplemento(p.getLogradouro().getComplemento());
            end.setBairro(p.getLogradouro().getBairro());
            end.setMunicipio(p.getLogradouro().getMunicipio());
            end.setUf(p.getLogradouro().getUf());
            end.setCep(p.getLogradouro().getCep());
            dto.setEndereco(end);
        }

        return dto;
    }
}
