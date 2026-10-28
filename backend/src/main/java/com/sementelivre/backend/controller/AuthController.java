package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.sementelivre.backend.dto.*;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.service.AuthService;
import com.sementelivre.backend.service.LimiteAutenticacaoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Cadastro de proprietário, login, refresh do token, recuperação e troca de senha, perfil da conta logada.")
public class AuthController {

    private final AuthService authService;
    private final LimiteAutenticacaoService limite;

    public AuthController(AuthService authService, LimiteAutenticacaoService limite) {
        this.authService = authService;
        this.limite = limite;
    }

    // As rotas abertas passam pelo limite de tentativas (#200): acima dele, 429 com Retry-After

    @PostMapping("/cadastrar")
    public ResponseEntity<UsuarioResponseDTO> cadastrar(
            @RequestBody @Valid ProprietarioCreateRequestDTO dto, HttpServletRequest request) {
        limite.antesDoCadastro(request);
        UsuarioResponseDTO usuarioCriado = authService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioCriado);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto, HttpServletRequest request) {
        limite.antesDoLogin(request, dto.email());
        TokenResponseDTO tokenResponse;
        try {
            tokenResponse = authService.login(dto);
        } catch (AuthenticationException e) {
            limite.senhaErrada(dto.email());
            throw e;
        }
        limite.loginCerto(dto.email());
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDTO> refresh(@RequestBody @Valid RefreshTokenRequestDTO dto, HttpServletRequest request) {
        limite.antesDoRefresh(request);
        TokenResponseDTO tokenResponse = authService.refreshToken(dto);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/recuperar-senha")
    public ResponseEntity<Map<String, String>> solicitarRecuperacaoSenha(
            @RequestBody @Valid SolicitarRecuperacaoDTO dto, HttpServletRequest request) {
        limite.antesDaRecuperacao(request, dto.email());
        authService.solicitarRecuperacaoSenha(dto);
        return ResponseEntity.ok(Map.of("mensagem", "Se o e-mail estiver cadastrado, as instruções foram enviadas."));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Map<String, String>> redefinirSenha(
            @RequestBody @Valid RedefinirSenhaDTO dto, HttpServletRequest request) {
        limite.antesDaRedefinicao(request);
        authService.redefinirSenha(dto);
        return ResponseEntity.ok(Map.of("mensagem", "Senha redefinida com sucesso."));
    }

    @PostMapping("/alterar-senha")
    public ResponseEntity<Map<String, String>> alterarSenha(
            @AuthenticationPrincipal Usuario usuarioAutenticado, @RequestBody @Valid AlterarSenhaDTO dto) {
        if (usuarioAutenticado == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.alterarSenha(usuarioAutenticado.getId(), dto);
        return ResponseEntity.ok(Map.of("mensagem", "Senha alterada com sucesso."));
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> obterPerfilAtual(@AuthenticationPrincipal Usuario usuarioAutenticado) {
        if (usuarioAutenticado == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.perfil(usuarioAutenticado.getId()));
    }
}
