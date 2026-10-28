package com.sementelivre.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sementelivre.backend.security.HashDeToken;
import com.sementelivre.backend.security.JwtService;

import io.jsonwebtoken.Claims;

/**
 * Fluxos de autenticacao alem do login/cadastro (issues #60 e #90): refresh do token,
 * recuperacao e redefinicao de senha, e as claims levadas no JWT.
 */
class FluxosAuthIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String MENSAGEM_RECUPERACAO = "Se o e-mail estiver cadastrado, as instruções foram enviadas.";
    private static final String REFRESH_INVALIDO = "Sessão inválida ou expirada. Faça login novamente.";
    private static final String RECUPERACAO_INVALIDA = "Link de recuperação inválido ou expirado. Peça um novo.";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions solicitarRecuperacao(String email) throws Exception {
        return mockMvc.perform(post("/auth/recuperar-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions redefinirSenha(String codigo, String novaSenha) throws Exception {
        return mockMvc.perform(post("/auth/redefinir-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"novaSenha\":\"%s\"}".formatted(codigo, novaSenha)));
    }

    /**
     * O codigo vai por e-mail e o banco so guarda o hash dele (#206). Nos testes
     * (sem SMTP) o ultimo token da conta passa a ser o hash de um codigo conhecido.
     */
    private String ultimoCodigoDeRecuperacao(Conta conta) {
        String codigo = "codigo-" + UUID.randomUUID();
        int trocados = jdbcTemplate.update("""
                UPDATE token_recuperacao_senha_t SET token = ?
                WHERE id = (SELECT id FROM token_recuperacao_senha_t WHERE usuario_id = ?
                            ORDER BY data_expiracao DESC LIMIT 1)
                """, HashDeToken.sha256(codigo), UUID.fromString(conta.id()));
        assertThat(trocados).isEqualTo(1);
        return codigo;
    }

    // ── refresh ──────────────────────────────────────────────────────────────

    @Test
    void refreshDeveRenovarOsTokensSemNovoLogin() throws Exception {
        Conta conta = cadastrar();
        Tokens login = loginTokens(conta.email(), SENHA);

        String resposta = refresh(login.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800))
                .andReturn().getResponse().getContentAsString();
        Tokens renovados = new Tokens(JsonPath.read(resposta, "$.accessToken"), JsonPath.read(resposta, "$.refreshToken"));

        assertThat(renovados.refreshToken()).isNotEqualTo(login.refreshToken());
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, renovados.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conta.id()));
    }

    @Test
    void bancoDeveGuardarSoOHashDoRefreshToken() throws Exception {
        Conta conta = cadastrar();
        Tokens login = loginTokens(conta.email(), SENHA);

        String salvo = jdbcTemplate.queryForObject(
                "SELECT token FROM refresh_token_t WHERE usuario_id = ?", String.class, UUID.fromString(conta.id()));

        assertThat(salvo).isNotEqualTo(login.refreshToken());
        assertThat(salvo).isEqualTo(HashDeToken.sha256(login.refreshToken()));
        // O valor do banco não serve como refresh token
        refresh(salvo).andExpect(status().isBadRequest());
        refresh(login.refreshToken()).andExpect(status().isOk());
    }

    // A V12 converte os tokens que já existiam com o SQL abaixo: precisa dar o mesmo
    // hash do Java, senão quem estava logado perde a sessão na atualização
    @Test
    void hashDaMigracaoDeveSerIgualAoDoJava() {
        String token = UUID.randomUUID().toString();
        String doBanco = jdbcTemplate.queryForObject(
                "SELECT encode(sha256(convert_to(?, 'UTF8')), 'hex')", String.class, token);

        assertThat(doBanco).isEqualTo(HashDeToken.sha256(token));
    }

    @Test
    void bancoDeveGuardarSoOHashDoTokenDeRecuperacao() throws Exception {
        Conta conta = cadastrar();
        solicitarRecuperacao(conta.email()).andExpect(status().isOk());

        String salvo = jdbcTemplate.queryForObject(
                "SELECT token FROM token_recuperacao_senha_t WHERE usuario_id = ?", String.class,
                UUID.fromString(conta.id()));

        // SHA-256 em hex; o token mandado por e-mail é um UUID
        assertThat(salvo).matches("[0-9a-f]{64}");
        redefinirSenha(salvo, "Recuperada7").andExpect(status().isBadRequest());
    }

    @Test
    void sairDeveRevogarORefreshToken() throws Exception {
        Conta conta = cadastrar();
        Tokens login = loginTokens(conta.email(), SENHA);

        mockMvc.perform(post("/auth/sair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(login.refreshToken())))
                .andExpect(status().isNoContent());

        refresh(login.refreshToken()).andExpect(status().isBadRequest());
        // Sair de novo (token que já não existe) não é erro
        mockMvc.perform(post("/auth/sair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(login.refreshToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshTokenJaUsadoOuInvalidoDeveSerRecusado() throws Exception {
        Conta conta = cadastrar();
        Tokens login = loginTokens(conta.email(), SENHA);
        refresh(login.refreshToken()).andExpect(status().isOk());

        // rotacao: o refresh token anterior deixa de valer. Revogado e inexistente
        // recebem a mesma mensagem, para nao revelar se o token existe (#204)
        refresh(login.refreshToken()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(REFRESH_INVALIDO));
        refresh("refresh-inexistente").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(REFRESH_INVALIDO));
        refresh("").andExpect(status().isBadRequest());
    }

    // ── recuperar / redefinir senha ─────────────────────────────────────────

    @Test
    void recuperarSenhaDeveGerarCodigoSemRevelarSeOEmailExiste() throws Exception {
        Conta conta = cadastrar();

        solicitarRecuperacao(conta.email())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM_RECUPERACAO));
        assertThat(ultimoCodigoDeRecuperacao(conta)).isNotBlank();

        // e-mail inexistente recebe a mesma resposta
        solicitarRecuperacao("ninguem-" + sufixoUnico() + "@teste.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM_RECUPERACAO));
    }

    @Test
    void redefinirSenhaComCodigoValidoDeveTrocarASenhaUmaVezSo() throws Exception {
        Conta conta = cadastrar();
        solicitarRecuperacao(conta.email()).andExpect(status().isOk());
        String codigo = ultimoCodigoDeRecuperacao(conta);

        redefinirSenha(codigo, "Recuperada7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Senha redefinida com sucesso."));

        loginTokens(conta.email(), "Recuperada7");
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(conta.email(), SENHA)))
                .andExpect(status().isUnauthorized());

        // o mesmo codigo nao serve de novo
        redefinirSenha(codigo, "OutraSenha8")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(RECUPERACAO_INVALIDA));
    }

    @Test
    void redefinirSenhaComCodigoInvalidoOuExpiradoDeveSerRecusado() throws Exception {
        Conta conta = cadastrar();

        // inexistente, expirado e usado: a mesma mensagem (#204)
        redefinirSenha("codigo-inexistente", "Recuperada7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(RECUPERACAO_INVALIDA));

        solicitarRecuperacao(conta.email()).andExpect(status().isOk());
        String codigo = ultimoCodigoDeRecuperacao(conta);
        jdbcTemplate.update("UPDATE token_recuperacao_senha_t SET data_expiracao = now() - interval '1 minute' WHERE token = ?",
                HashDeToken.sha256(codigo));

        redefinirSenha(codigo, "Recuperada7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(RECUPERACAO_INVALIDA));
        loginTokens(conta.email(), SENHA); // senha original continua valendo
    }

    // ── claims do JWT ────────────────────────────────────────────────────────

    @Test
    void tokenDeveLevarEmailIdERolesNasClaims() throws Exception {
        Conta conta = cadastrar();
        Claims proprietario = jwtService.extrairClaims(loginTokens(conta.email(), SENHA).accessToken());

        assertThat(proprietario.getSubject()).isEqualTo(conta.email());
        assertThat(proprietario.get("id", String.class)).isEqualTo(conta.id());
        assertThat(proprietario.get("roles", String.class)).isEqualTo("ROLE_PROPRIETARIO");

        String emailAdmin = criarAdmin();
        Claims admin = jwtService.extrairClaims(loginTokens(emailAdmin, SENHA).accessToken());
        assertThat(admin.get("roles", String.class)).isEqualTo("ROLE_ADMIN");
    }
}
