package com.sementelivre.backend.service;

import com.sementelivre.backend.dto.*;
import com.sementelivre.backend.entity.*;
import com.sementelivre.backend.exception.SenhaAtualIncorretaException;
import com.sementelivre.backend.repository.*;
import com.sementelivre.backend.security.HashDeToken;
import com.sementelivre.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final ProprietarioService proprietarioService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenRecuperacaoSenhaRepository tokenRecuperacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;

    // Uma mensagem só por tipo de token: dizer "não encontrado" ou "expirado" contava
    // a quem tenta adivinhar se aquele token existe (#204)
    static final String REFRESH_INVALIDO = "Sessão inválida ou expirada. Faça login novamente.";
    static final String RECUPERACAO_INVALIDA = "Link de recuperação inválido ou expirado. Peça um novo.";

    @Value("${jwt.refresh-expiration-days:7}")
    private Long refreshExpirationDays;

    public AuthService(UsuarioRepository usuarioRepository, UsuarioService usuarioService,
                       ProprietarioService proprietarioService,
                       RefreshTokenRepository refreshTokenRepository, TokenRecuperacaoSenhaRepository tokenRecuperacaoRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtService jwtService, EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.proprietarioService = proprietarioService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenRecuperacaoRepository = tokenRecuperacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    // O proprietario e o unico perfil com cadastro aberto; admins nao se cadastram por aqui.
    @Transactional
    public UsuarioResponseDTO cadastrar(ProprietarioCreateRequestDTO dto) {
        Proprietario proprietario = proprietarioService.criar(dto);
        return UsuarioResponseDTO.fromEntity(usuarioService.buscarPorId(proprietario.getId()));
    }

    // Recarrega a conta dentro de uma transacao: o Usuario que chega do SecurityFilter foi lido
    // fora de sessao, e o logradouro da pessoa (LAZY) nao carregaria a partir dele.
    @Transactional(readOnly = true)
    public UsuarioResponseDTO perfil(UUID usuarioId) {
        return UsuarioResponseDTO.fromEntity(usuarioService.buscarPorId(usuarioId));
    }

    @Transactional
    public void alterarSenha(UUID usuarioId, AlterarSenhaDTO dto) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            throw new SenhaAtualIncorretaException("Senha atual incorreta.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha()));
        usuarioRepository.save(usuario);
    }

    @Transactional
    public TokenResponseDTO login(LoginRequestDTO dto) {
        var authToken = new UsernamePasswordAuthenticationToken(dto.email(), dto.senha());
        var authentication = authenticationManager.authenticate(authToken);

        Usuario usuario = (Usuario) authentication.getPrincipal();

        String accessToken = jwtService.gerarToken(usuario);
        String refreshToken = criarRefreshToken(usuario);

        return new TokenResponseDTO(accessToken, refreshToken, 1800L);
    }

    @Transactional
    public TokenResponseDTO refreshToken(RefreshTokenRequestDTO dto) {
        RefreshToken tokenSalvo = refreshTokenRepository.findByToken(HashDeToken.sha256(dto.refreshToken()))
                .orElseThrow(() -> new IllegalArgumentException(REFRESH_INVALIDO));

        if (tokenSalvo.getRevogado() || tokenSalvo.getDataExpiracao().isBefore(Instant.now())) {
            throw new IllegalArgumentException(REFRESH_INVALIDO);
        }

        Usuario usuario = tokenSalvo.getUsuario();
        
        // Revoga o token atual (Refresh Token Rotation)
        tokenSalvo.setRevogado(true);
        refreshTokenRepository.save(tokenSalvo);

        // Gera novo par de tokens
        String novoAccessToken = jwtService.gerarToken(usuario);
        String novoRefreshToken = criarRefreshToken(usuario);

        return new TokenResponseDTO(novoAccessToken, novoRefreshToken, 1800L);
    }

    @Transactional
    public void solicitarRecuperacaoSenha(SolicitarRecuperacaoDTO dto) {
        var usuarioOpt = usuarioRepository.findByPessoaEmailIgnoreCase(Pessoa.normalizarEmail(dto.email()));
        if (usuarioOpt.isEmpty()) {
            return; // Retorno silencioso por segurança
        }

        Usuario usuario = usuarioOpt.get();
        String token = UUID.randomUUID().toString();

        TokenRecuperacaoSenha tokenEntity = TokenRecuperacaoSenha.builder()
                .token(HashDeToken.sha256(token))
                .usuario(usuario)
                .dataExpiracao(Instant.now().plus(15, ChronoUnit.MINUTES))
                .usado(false)
                .build();

        tokenRecuperacaoRepository.save(tokenEntity);
        emailService.enviarEmailRecuperacaoSenha(usuario.getEmail(), token);
    }

    @Transactional
    public void redefinirSenha(RedefinirSenhaDTO dto) {
        TokenRecuperacaoSenha tokenEntity = tokenRecuperacaoRepository.findByToken(HashDeToken.sha256(dto.token()))
                .orElseThrow(() -> new IllegalArgumentException(RECUPERACAO_INVALIDA));

        if (tokenEntity.getUsado() || tokenEntity.getDataExpiracao().isBefore(Instant.now())) {
            throw new IllegalArgumentException(RECUPERACAO_INVALIDA);
        }

        Usuario usuario = tokenEntity.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha())); // Atualiza a senha no banco com BCrypt
        usuarioRepository.save(usuario);

        tokenEntity.setUsado(true);
        tokenRecuperacaoRepository.save(tokenEntity);
    }

    /** Grava o hash do token novo e devolve o token, que só o cliente fica sabendo. */
    private String criarRefreshToken(Usuario usuario) {
        refreshTokenRepository.deleteByUsuario(usuario);

        String token = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .usuario(usuario)
                .token(HashDeToken.sha256(token))
                .dataExpiracao(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
                .revogado(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return token;
    }
}
