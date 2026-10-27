#!/usr/bin/env python3
"""
Validação do domínio de pessoas (Pessoa, Usuario, Proprietario) num ambiente
real -- issue #133 (Dev 3 · S8).

Executa contra a API os cenários de cadastro, consulta, alteração e exclusão,
unicidade de CPF/RG/e-mail, validações e exposição de dados sensíveis. Só cria
dados de teste (e-mails qa-s8-<execucao>-*@teste.sementelivre.dev) e apaga tudo
no fim pela exclusão de conta do titular.

Uso:
    python3 validar_pessoas.py --base-url http://localhost:8080 \\
        [--admin-email ADMIN --admin-senha SENHA] \\
        [--origem-front http://localhost:3000] [--saida resultado.json]

As credenciais de admin também podem vir de SEMENTE_ADMIN_EMAIL e
SEMENTE_ADMIN_SENHA. Sem elas, os cenários que dependem de admin são pulados.

A integridade dos dados no banco é conferida à parte, por integridade_pessoas.sql.

Só usa a biblioteca padrão (Python 3.10+), para rodar em qualquer máquina.
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field

DOMINIO_QA = "teste.sementelivre.dev"
SENHA_QA = "QaS8-Senha1"

# Valor default de jwt.secret em JwtService. Se a produção não sobrescrever,
# qualquer pessoa com acesso ao repositório consegue assinar tokens.
SEGREDO_JWT_PADRAO = "sementeLivreChaveSecretaSuperSeguraComMaisDe256Bits1234567890!"

CHAVES_SENSIVEIS = {"senha", "senhahash", "senha_hash", "password"}
PADRAO_BCRYPT = re.compile(r"\$2[aby]\$\d\d\$")
PADRAO_VAZAMENTO = re.compile(r"org\.hibernate|org\.postgresql|java\.lang\.|SQLState|\bat com\.sementelivre")
CAMPOS_PERFIL_PUBLICO = {"id", "nome", "municipio", "comunidades", "sementes"}


# --------------------------------------------------------------------------- HTTP

@dataclass
class Resposta:
    status: int
    json: object
    texto: str
    headers: object

    def campos_com_erro(self):
        if isinstance(self.json, dict) and self.json.get("fieldErrors"):
            return [e.split(":", 1)[0].strip() for e in self.json["fieldErrors"]]
        return []

    def resumo(self):
        if isinstance(self.json, dict):
            partes = [str(self.json.get("message") or self.json.get("error") or "")]
            if self.json.get("fieldErrors"):
                partes.append("; ".join(self.json["fieldErrors"]))
            texto = " | ".join(p for p in partes if p)
            if texto:
                return f"{self.status} {texto}"[:220]
        return f"{self.status} {self.texto[:160]}".strip()


class Api:
    def __init__(self, base_url):
        self.base = base_url.rstrip("/")
        self.historico = []

    def req(self, metodo, caminho, corpo=None, token=None, headers=None, bruto=None):
        cabecalhos = {"Accept": "application/json"}
        dados = None
        if corpo is not None:
            dados = json.dumps(corpo).encode()
            cabecalhos["Content-Type"] = "application/json"
        if bruto is not None:
            dados = bruto.encode()
            cabecalhos["Content-Type"] = "application/json"
        if token:
            cabecalhos["Authorization"] = f"Bearer {token}"
        if headers:
            cabecalhos.update(headers)

        pedido = urllib.request.Request(self.base + caminho, data=dados, method=metodo, headers=cabecalhos)
        try:
            with urllib.request.urlopen(pedido, timeout=30) as r:
                status, texto, hdrs = r.status, r.read().decode(errors="replace"), r.headers
        except urllib.error.HTTPError as e:
            status, texto, hdrs = e.code, e.read().decode(errors="replace"), e.headers

        try:
            conteudo = json.loads(texto) if texto else None
        except ValueError:
            conteudo = None

        resposta = Resposta(status, conteudo, texto, hdrs)
        self.historico.append((metodo, caminho, resposta))
        return resposta


# --------------------------------------------------------------------------- relatório

@dataclass
class Resultado:
    id: str
    categoria: str
    descricao: str
    esperado: str
    obtido: str
    status: str  # OK | FALHA | AVISO | PULADO


@dataclass
class Relatorio:
    itens: list = field(default_factory=list)

    def checar(self, id_, categoria, descricao, esperado, resposta_ou_obtido, ok, aviso=False):
        obtido = resposta_ou_obtido.resumo() if isinstance(resposta_ou_obtido, Resposta) else str(resposta_ou_obtido)
        status = "OK" if ok else ("AVISO" if aviso else "FALHA")
        self.itens.append(Resultado(id_, categoria, descricao, esperado, obtido, status))
        marca = {"OK": "ok   ", "FALHA": "FALHA", "AVISO": "aviso"}[status]
        print(f"[{marca}] {id_:8} {descricao}")
        if status != "OK":
            print(f"          esperado: {esperado}\n          obtido:   {obtido}")
        return ok

    def pular(self, id_, categoria, descricao, motivo):
        self.itens.append(Resultado(id_, categoria, descricao, "-", motivo, "PULADO"))
        print(f"[pulou] {id_:8} {descricao} ({motivo})")

    def contagem(self):
        c = {"OK": 0, "FALHA": 0, "AVISO": 0, "PULADO": 0}
        for r in self.itens:
            c[r.status] += 1
        return c


# --------------------------------------------------------------------------- dados de teste

def gerar_cpf():
    while True:
        d = [random.randint(0, 9) for _ in range(9)]
        if len(set(d)) > 1:
            break
    for n in (9, 10):
        resto = sum(v * (n + 1 - i) for i, v in enumerate(d)) % 11
        d.append(0 if resto < 2 else 11 - resto)
    return "".join(map(str, d))


def cpf_com_dv_errado():
    cpf = gerar_cpf()
    return cpf[:10] + str((int(cpf[10]) + 1) % 10)


def jwt_hs256(claims, segredo):
    def b64(dados):
        return base64.urlsafe_b64encode(dados).rstrip(b"=").decode()

    cabecalho = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    corpo = b64(json.dumps(claims).encode())
    assinatura = hmac.new(segredo.encode(), f"{cabecalho}.{corpo}".encode(), hashlib.sha256).digest()
    return f"{cabecalho}.{corpo}.{b64(assinatura)}"


@dataclass
class Conta:
    rotulo: str
    email: str
    senha: str
    documento: str
    rg: str
    id: str = None
    token: str = None
    removida: bool = False


class Validacao:
    def __init__(self, api, relatorio, admin_email, admin_senha, origem_front):
        self.api = api
        self.r = relatorio
        self.origem_front = origem_front
        self.execucao = time.strftime("%Y%m%d%H%M%S")
        self.seq_rg = 0
        self.contas = []
        self.admin_token = None
        self.admin_email = admin_email
        self.admin_senha = admin_senha

    # ---- helpers

    def email(self, rotulo):
        return f"qa-s8-{self.execucao}-{rotulo}@{DOMINIO_QA}"

    def proximo_rg(self):
        self.seq_rg += 1
        return f"QA{self.execucao[-6:]}{self.seq_rg:03d}"

    def payload(self, rotulo, **sobrescrever):
        corpo = {
            "nome": f"QA S8 {rotulo}",
            "tipoDocumento": "CPF",
            "documento": gerar_cpf(),
            "rg": self.proximo_rg(),
            "telefone": "32999990000",
            "email": self.email(rotulo),
            "senha": SENHA_QA,
            "endereco": {
                "logradouro": "Rua de Teste QA",
                "numero": "10",
                "bairro": "Centro",
                "municipio": "Rio Pomba",
                "uf": "MG",
                "cep": "36180000",
            },
        }
        corpo.update(sobrescrever)
        return corpo

    def registrar_se_criou(self, rotulo, corpo, resposta):
        """Toda conta criada entra na lista de limpeza, inclusive as que não deveriam ter sido aceitas."""
        if resposta.status == 201 and isinstance(resposta.json, dict) and resposta.json.get("id"):
            conta = Conta(rotulo, corpo.get("email"), corpo.get("senha"), corpo.get("documento"),
                          corpo.get("rg"), resposta.json["id"])
            self.contas.append(conta)
            return conta
        return None

    def cadastrar(self, rotulo, headers=None, **sobrescrever):
        corpo = self.payload(rotulo, **sobrescrever)
        resposta = self.api.req("POST", "/auth/cadastrar", corpo, headers=headers)
        return corpo, resposta, self.registrar_se_criou(rotulo, corpo, resposta)

    def login(self, email, senha, headers=None):
        resposta = self.api.req("POST", "/auth/login", {"email": email, "senha": senha}, headers=headers)
        token = resposta.json.get("accessToken") if resposta.status == 200 and isinstance(resposta.json, dict) else None
        return resposta, token

    def id_logado(self, token):
        me = self.api.req("GET", "/auth/me", token=token) if token else None
        return me.json.get("id") if me is not None and me.status == 200 and isinstance(me.json, dict) else None

    def restaurar_email(self, conta, email_atual):
        """Desfaz uma troca de e-mail que não deveria ter sido aceita, para não contaminar os próximos cenários."""
        _, token = self.login(email_atual, conta.senha)
        if token:
            self.api.req("PUT", f"/api/proprietarios/{conta.id}",
                         {"nome": "QA S8 a editado", "email": conta.email, "endereco": self.endereco()}, token=token)
        _, conta.token = self.login(conta.email, conta.senha)

    def conta_pronta(self, rotulo, **sobrescrever):
        corpo, resposta, conta = self.cadastrar(rotulo, **sobrescrever)
        if not conta:
            raise RuntimeError(f"não foi possível criar a conta de teste '{rotulo}': {resposta.resumo()}")
        _, conta.token = self.login(conta.email, conta.senha)
        return conta

    def endereco(self, **sobrescrever):
        e = {"logradouro": "Rua de Teste QA", "numero": "10", "bairro": "Centro",
             "municipio": "Rio Pomba", "uf": "MG", "cep": "36180000"}
        e.update(sobrescrever)
        return e

    # ---- cenários

    def executar(self):
        self.saude()
        self.entrar_como_admin()
        a, b = self.crud()
        self.unicidade(a, b)
        self.validacoes(a)
        self.dados_sensiveis(a, b)
        self.fluxo_front()
        self.exclusao_com_vinculos()
        self.limpar()

    def saude(self):
        print("\n== Saúde do ambiente")
        resposta = self.api.req("GET", "/actuator/health")
        corpo = resposta.json if isinstance(resposta.json, dict) else {}
        self.r.checar("AMB-01", "ambiente", "Health check responde UP", "200 status=UP",
                      resposta, resposta.status == 200 and corpo.get("status") == "UP")
        self.r.checar("AMB-02", "ambiente", "Health check não expõe detalhes internos (show-details: never)",
                      "sem 'components'/'details'", resposta,
                      "components" not in corpo and "details" not in corpo)

    def entrar_como_admin(self):
        if not (self.admin_email and self.admin_senha):
            print("\n(sem credenciais de admin: cenários de admin serão pulados)")
            return
        resposta, self.admin_token = self.login(self.admin_email, self.admin_senha)
        self.r.checar("AMB-03", "ambiente", "Login do admin", "200 com accessToken", resposta,
                      self.admin_token is not None)

    def crud(self):
        print("\n== CRUD de pessoa/proprietário")
        corpo_a, resp, a = self.cadastrar("a")
        j = resp.json if isinstance(resp.json, dict) else {}
        self.r.checar("CRUD-01", "crud", "Cadastro público de proprietário (POST /auth/cadastrar)",
                      "201, tipoPessoa=PROPRIETARIO, roles=[ROLE_PROPRIETARIO]", resp,
                      resp.status == 201 and j.get("tipoPessoa") == "PROPRIETARIO"
                      and j.get("roles") == ["ROLE_PROPRIETARIO"]
                      and j.get("documento") == corpo_a["documento"] and j.get("rg") == corpo_a["rg"])
        if not a:
            raise RuntimeError("cadastro básico falhou; sem ele os demais cenários não fazem sentido")

        resp, a.token = self.login(a.email, a.senha)
        self.r.checar("CRUD-02", "crud", "Login do proprietário recém-cadastrado", "200 com accessToken",
                      resp, a.token is not None)

        resp = self.api.req("GET", "/auth/me", token=a.token)
        j = resp.json if isinstance(resp.json, dict) else {}
        self.r.checar("CRUD-03", "crud", "Consulta do próprio perfil (GET /auth/me)",
                      "200 com e-mail, CPF, RG e endereço cadastrados", resp,
                      resp.status == 200 and j.get("email") == a.email and j.get("documento") == a.documento
                      and j.get("rg") == a.rg and (j.get("endereco") or {}).get("municipio") == "Rio Pomba")
        data_antes = j.get("dataUltimaAlteracao")

        b = self.conta_pronta("b", exibirNoSitePublico=True)

        time.sleep(1)
        edicao = {"nome": "QA S8 a editado", "telefone": "32988887777", "email": a.email,
                  "endereco": self.endereco(municipio="Juiz de Fora"),
                  "documento": gerar_cpf(), "rg": "NAO-EDITAVEL"}
        resp = self.api.req("PUT", f"/api/proprietarios/{a.id}", edicao, token=a.token)
        j = resp.json if isinstance(resp.json, dict) else {}
        self.r.checar("CRUD-04", "crud", "Alteração dos próprios dados (PUT /api/proprietarios/{id})",
                      "200 com nome/endereço novos e dataUltimaAlteracao atualizada", resp,
                      resp.status == 200 and j.get("nome") == "QA S8 a editado"
                      and (j.get("endereco") or {}).get("municipio") == "Juiz de Fora"
                      and j.get("dataUltimaAlteracao") != data_antes)

        resp = self.api.req("GET", "/auth/me", token=a.token)
        j = resp.json if isinstance(resp.json, dict) else {}
        self.r.checar("CRUD-05", "crud", "CPF e RG não mudam pela edição de perfil",
                      f"documento={a.documento} rg={a.rg}", f"documento={j.get('documento')} rg={j.get('rg')}",
                      j.get("documento") == a.documento and j.get("rg") == a.rg)

        resp = self.api.req("PUT", f"/api/proprietarios/{b.id}", edicao | {"email": b.email}, token=a.token)
        self.r.checar("CRUD-06", "crud", "Proprietário não altera dados de outro", "403", resp, resp.status == 403)
        resp = self.api.req("DELETE", f"/api/pessoas/{b.id}", token=a.token)
        self.r.checar("CRUD-07", "crud", "Proprietário não exclui outra pessoa (DELETE /api/pessoas/{id})",
                      "403", resp, resp.status == 403)
        resp = self.api.req("DELETE", f"/api/pessoas/{b.id}/conta", token=a.token)
        self.r.checar("CRUD-08", "crud", "Proprietário não exclui a conta de outro (DELETE .../conta)",
                      "403", resp, resp.status == 403)

        self.troca_de_email()
        self.crud_admin()
        self.exclusao_pelo_titular()
        return a, b

    def troca_de_email(self):
        e = self.conta_pronta("e")
        tokens = self.api.req("POST", "/auth/login", {"email": e.email, "senha": e.senha})
        refresh = tokens.json.get("refreshToken") if isinstance(tokens.json, dict) else None
        novo_email = self.email("e-novo")
        resp = self.api.req("PUT", f"/api/proprietarios/{e.id}",
                            {"nome": "QA S8 e", "email": novo_email.upper(), "endereco": self.endereco()},
                            token=e.token)
        renovado = self.api.req("POST", "/auth/refresh", {"refreshToken": refresh})
        token_novo = renovado.json.get("accessToken") if isinstance(renovado.json, dict) else None
        me = self.api.req("GET", "/auth/me", token=token_novo)
        email_final = me.json.get("email") if isinstance(me.json, dict) else None
        if resp.status == 200:
            e.email = email_final or novo_email
            # O token antigo leva o e-mail velho e passa a dar 401. Sem trocar, a
            # limpeza (sem credenciais de admin) não excluía esta conta.
            e.token = token_novo
        self.r.checar("CRUD-17", "crud", "Troca do próprio e-mail: grava normalizado e a sessão segue pelo refresh",
                      f"PUT 200; refresh 200; /auth/me email={novo_email}",
                      f"PUT {resp.status}; refresh {renovado.status}; /auth/me {me.status} email={email_final}",
                      resp.status == 200 and renovado.status == 200 and email_final == novo_email)

    def crud_admin(self):
        ids = ("CRUD-09", "CRUD-10", "CRUD-11", "CRUD-12", "CRUD-13", "CRUD-14")
        if not self.admin_token:
            for i in ids:
                self.r.pular(i, "crud", "Operação administrativa", "sem credenciais de admin")
            return

        corpo = self.payload("c")
        resp = self.api.req("POST", "/api/proprietarios", corpo, token=self.admin_token)
        c = self.registrar_se_criou("c", corpo, resp)
        self.r.checar("CRUD-09", "crud", "Admin cadastra proprietário (POST /api/proprietarios)",
                      "201 com Location", resp, resp.status == 201 and bool(resp.headers.get("Location")))
        if not c:
            return

        resp = self.api.req("GET", f"/api/proprietarios/{c.id}", token=self.admin_token)
        self.r.checar("CRUD-10", "crud", "Admin consulta proprietário por id", "200 com RG", resp,
                      resp.status == 200 and isinstance(resp.json, dict) and resp.json.get("rg") == c.rg)

        resp = self.api.req("GET", "/api/pessoas", token=self.admin_token)
        ids_lista = {p.get("id") for p in resp.json} if isinstance(resp.json, list) else set()
        self.r.checar("CRUD-11", "crud", "Admin lista pessoas (GET /api/pessoas)", "200 contendo o cadastrado",
                      resp, resp.status == 200 and c.id in ids_lista)

        edicao = {"nome": "QA S8 c editado pelo admin", "telefone": "3233334444", "email": c.email,
                  "endereco": self.endereco()}
        resp = self.api.req("PUT", f"/api/proprietarios/{c.id}", edicao, token=self.admin_token)
        self.r.checar("CRUD-12", "crud", "Admin altera proprietário", "200", resp,
                      resp.status == 200 and isinstance(resp.json, dict)
                      and resp.json.get("nome") == "QA S8 c editado pelo admin")

        resp = self.api.req("DELETE", f"/api/proprietarios/{c.id}", token=self.admin_token)
        depois = self.api.req("GET", f"/api/proprietarios/{c.id}", token=self.admin_token)
        login, _ = self.login(c.email, c.senha)
        ok = resp.status == 204 and depois.status == 404 and login.status == 401
        if resp.status == 204:
            c.removida = True
        self.r.checar("CRUD-13", "crud", "Admin exclui proprietário sem vínculos",
                      "204; depois GET 404 e login 401",
                      f"DELETE {resp.status}; GET {depois.status}; login {login.status}", ok)

        resp = self.api.req("GET", f"/api/pessoas/{uuid.uuid4()}", token=self.admin_token)
        self.r.checar("CRUD-14", "crud", "Consulta de id inexistente", "404 com mensagem", resp,
                      resp.status == 404 and isinstance(resp.json, dict) and bool(resp.json.get("message")))

    def exclusao_pelo_titular(self):
        d = self.conta_pronta("d")
        resp = self.api.req("DELETE", f"/api/pessoas/{d.id}/conta", token=d.token)
        if resp.status == 204:
            d.removida = True
        login, _ = self.login(d.email, d.senha)
        me = self.api.req("GET", "/auth/me", token=d.token)
        self.r.checar("CRUD-15", "crud", "Titular exclui a própria conta (LGPD, DELETE /api/pessoas/{id}/conta)",
                      "204; depois login 401 e token antigo 401",
                      f"DELETE {resp.resumo()}; login {login.status}; /auth/me {me.status}",
                      resp.status == 204 and login.status == 401 and me.status == 401)

        # Direito ao esquecimento: e-mail, CPF e RG precisam ficar livres de novo.
        corpo = {"email": d.email, "documento": d.documento, "rg": d.rg}
        _, resp, novo = self.cadastrar("d", **corpo)
        self.r.checar("CRUD-16", "crud", "Após a exclusão, e-mail/CPF/RG ficam livres para novo cadastro",
                      "201", resp, resp.status == 201)
        if novo:
            _, novo.token = self.login(novo.email, novo.senha)

    def unicidade(self, a, b):
        print("\n== Unicidade de e-mail, CPF e RG")

        def duplicado(id_, descricao, campo, **sobrescrever):
            corpo, resp, _ = self.cadastrar(f"dup-{id_.lower()}", **sobrescrever)
            self.r.checar(id_, "unicidade", descricao, f"409 com fieldErrors '{campo}: ...'", resp,
                          resp.status == 409 and campo in resp.campos_com_erro())

        duplicado("UNI-01", "E-mail já cadastrado é rejeitado", "email", email=a.email)
        duplicado("UNI-02", "E-mail já cadastrado com outra caixa (MAIÚSCULAS) é rejeitado", "email",
                  email=a.email.upper())
        _, resp, _ = self.cadastrar("dup-uni-03", email=f"  {a.email}  ")
        self.r.checar("UNI-03", "unicidade", "E-mail já cadastrado com espaços nas pontas não gera conta nova",
                      "400 ou 409", resp, resp.status in (400, 409))
        duplicado("UNI-04", "CPF já cadastrado é rejeitado", "documento", documento=a.documento)
        duplicado("UNI-05", "RG já cadastrado é rejeitado", "rg", rg=a.rg)

        rg_formatado = f"{a.rg[:2].lower()}-{a.rg[2:]}"
        _, resp, _ = self.cadastrar("dup-uni-06", rg=rg_formatado)
        self.r.checar("UNI-06", "unicidade", "RG já cadastrado com outra formatação (minúsculas/hífen)",
                      "409 (mesmo RG)", resp, resp.status == 409, aviso=True)

        edicao = {"nome": "QA S8 a editado", "telefone": "32988887777", "endereco": self.endereco()}
        resp = self.api.req("PUT", f"/api/proprietarios/{a.id}", edicao | {"email": b.email}, token=a.token)
        self.r.checar("UNI-07", "unicidade", "Edição para e-mail de outra pessoa é rejeitada",
                      "409 com fieldErrors 'email: ...'", resp,
                      resp.status == 409 and "email" in resp.campos_com_erro())
        if resp.status == 200:
            self.restaurar_email(a, b.email)
        resp = self.api.req("PUT", f"/api/proprietarios/{a.id}", edicao | {"email": b.email.upper()}, token=a.token)
        self.r.checar("UNI-08", "unicidade", "Edição para e-mail de outra pessoa com outra caixa é rejeitada",
                      "409 com fieldErrors 'email: ...'", resp,
                      resp.status == 409 and "email" in resp.campos_com_erro())
        if resp.status == 200:
            self.restaurar_email(a, b.email.upper())

        resp, token = self.login(a.email.upper(), a.senha)
        logado = self.id_logado(token)
        self.r.checar("UNI-09", "unicidade",
                      "Login com o e-mail em outra caixa entra na conta certa (e-mail não diferencia maiúsculas)",
                      f"200 na conta {a.id}", f"{resp.status} conta={logado}", resp.status == 200 and logado == a.id)

        self.concorrencia("UNI-10", "Dois cadastros simultâneos com o mesmo e-mail", "email",
                          lambda i: {"email": self.email(f"corrida-email")})
        cpf = gerar_cpf()
        self.concorrencia("UNI-11", "Dois cadastros simultâneos com o mesmo CPF", "documento",
                          lambda i: {"documento": cpf, "email": self.email(f"corrida-cpf-{i}")})

    def concorrencia(self, id_, descricao, campo, variacao):
        corpos = [self.payload(f"corrida-{id_.lower()}-{i}", **variacao(i)) for i in range(2)]
        with ThreadPoolExecutor(max_workers=2) as pool:
            respostas = list(pool.map(lambda c: self.api.req("POST", "/auth/cadastrar", c), corpos))
        for corpo, resp in zip(corpos, respostas):
            self.registrar_se_criou(id_, corpo, resp)
        status = sorted(r.status for r in respostas)
        perdedora = next((r for r in respostas if r.status != 201), None)
        ok = status == [201, 409] and perdedora is not None and campo in perdedora.campos_com_erro()
        self.r.checar(id_, "unicidade", descricao, f"um 201 e um 409 com '{campo}: ...'",
                      f"{status} / {perdedora.resumo() if perdedora else '-'}", ok)

    def validacoes(self, a):
        print("\n== Validações e mensagens de erro")

        def invalido(id_, descricao, campo=None, mensagem=None, **sobrescrever):
            _, resp, _ = self.cadastrar(f"inv-{id_.lower()}", **sobrescrever)
            esperado = "400" + (f" com fieldErrors '{campo}: ...'" if campo else "") + \
                       (f" e mensagem '{mensagem}'" if mensagem else "")
            ok = resp.status == 400
            if campo:
                ok = ok and campo in resp.campos_com_erro()
            if mensagem:
                ok = ok and isinstance(resp.json, dict) and mensagem in str(resp.json.get("message"))
            self.r.checar(id_, "validacao", descricao, esperado, resp, ok)

        invalido("VAL-01", "CPF com dígito verificador errado", mensagem="dígito verificador",
                 documento=cpf_com_dv_errado())
        invalido("VAL-02", "CPF com máscara (pontos e hífen)", mensagem="apenas dígitos",
                 documento="529.982.247-25")
        invalido("VAL-03", "CPF com todos os dígitos iguais", mensagem="CPF inválido", documento="11111111111")
        invalido("VAL-04", "CNPJ informado como CPF", mensagem="11 dígitos", documento="11222333000181")
        invalido("VAL-05", "E-mail em formato inválido", campo="email", email="nao-e-um-email")
        invalido("VAL-06", "UF em minúsculas", campo="endereco.uf", endereco=self.endereco(uf="mg"))
        invalido("VAL-07", "Nome acima de 150 caracteres", campo="nome", nome="Q" * 151)
        invalido("VAL-08", "Telefone acima de 15 caracteres", campo="telefone", telefone="3" * 16)
        invalido("VAL-09", "Senha curta (3 caracteres)", campo="senha", senha="Ab1")
        invalido("VAL-10", "Senha acima de 72 caracteres (limite do BCrypt)", campo="senha", senha="A1" + "x" * 71)
        invalido("VAL-11", "Município acima de 100 caracteres", campo="endereco.municipio",
                 endereco=self.endereco(municipio="M" * 101))
        invalido("VAL-12", "Número do endereço acima de 10 caracteres", campo="endereco.numero",
                 endereco=self.endereco(numero="1" * 11))
        invalido("VAL-13", "CEP acima de 9 caracteres", campo="endereco.cep",
                 endereco=self.endereco(cep="3618000000"))
        invalido("VAL-14", "RG acima de 20 caracteres", campo="rg", rg="R" * 21)

        resp = self.api.req("POST", "/auth/cadastrar", {})
        faltando = {"nome", "tipoDocumento", "documento", "email", "senha", "endereco", "rg"}
        self.r.checar("VAL-15", "validacao", "Corpo vazio lista todos os campos obrigatórios",
                      f"400 com fieldErrors {sorted(faltando)}", resp,
                      resp.status == 400 and faltando <= set(resp.campos_com_erro()))

        corpo = self.payload("inv-val-16")
        corpo["tipoDocumento"] = "RG"
        resp = self.api.req("POST", "/auth/cadastrar", corpo)
        self.registrar_se_criou("inv-val-16", corpo, resp)
        self.r.checar("VAL-16", "validacao", "Tipo de documento inexistente", "400", resp, resp.status == 400)

        resp = self.api.req("POST", "/auth/cadastrar", bruto='{"nome": "QA", ')
        self.r.checar("VAL-17", "validacao", "JSON malformado", "400", resp, resp.status == 400)

        resp = self.api.req("PUT", f"/api/proprietarios/{a.id}",
                            {"nome": " ", "email": a.email, "endereco": self.endereco()}, token=a.token)
        self.r.checar("VAL-18", "validacao", "Edição com nome em branco", "400 com fieldErrors 'nome: ...'",
                      resp, resp.status == 400 and "nome" in resp.campos_com_erro())

    def dados_sensiveis(self, a, b):
        print("\n== Dados sensíveis e autorização")

        resp = self.api.req("GET", f"/produtores/{b.id}")
        chaves = set(resp.json) if isinstance(resp.json, dict) else set()
        vazados = [v for v in (b.documento, b.rg, b.email, "32999990000") if v and v in resp.texto]
        self.r.checar("SEN-01", "sensiveis", "Perfil público do produtor só traz id, nome, município, comunidades e sementes",
                      f"200 com chaves {sorted(CAMPOS_PERFIL_PUBLICO)} e sem CPF/RG/e-mail/telefone",
                      f"{resp.status} chaves={sorted(chaves)} vazados={vazados}",
                      resp.status == 200 and chaves == CAMPOS_PERFIL_PUBLICO and not vazados)

        privado = self.api.req("GET", f"/produtores/{a.id}")
        inexistente = self.api.req("GET", f"/produtores/{uuid.uuid4()}")

        def sem_id(r):
            return re.sub(r"[0-9a-f-]{36}", "<id>", str((r.json or {}).get("message"))) if isinstance(r.json, dict) else r.texto

        self.r.checar("SEN-02", "sensiveis", "Produtor privado é indistinguível de inexistente no site público",
                      "404 com a mesma mensagem nos dois casos",
                      f"privado {privado.status} '{sem_id(privado)}' / inexistente {inexistente.status} '{sem_id(inexistente)}'",
                      privado.status == 404 and inexistente.status == 404 and sem_id(privado) == sem_id(inexistente))

        resp = self.api.req("GET", "/api/pessoas")
        self.r.checar("SEN-03", "sensiveis", "Listagem de pessoas sem login", "401", resp, resp.status == 401)

        def bloqueado(id_, descricao, metodo, caminho, corpo=None):
            resp = self.api.req(metodo, caminho, corpo, token=a.token)
            expoe = b.documento in resp.texto or b.rg in resp.texto
            obtido = resp.resumo() if resp.status != 200 else \
                f"200 ({'expõe CPF/RG de outra pessoa' if expoe else 'sem dados de terceiros'})"
            self.r.checar(id_, "sensiveis", descricao, "403", obtido, resp.status == 403)
            if corpo is not None:
                self.registrar_se_criou(id_, corpo, resp)

        bloqueado("SEN-04", "Proprietário não lista todas as pessoas (GET /api/pessoas)", "GET", "/api/pessoas")
        bloqueado("SEN-05", "Proprietário não consulta outra pessoa (GET /api/pessoas/{id})", "GET", f"/api/pessoas/{b.id}")
        bloqueado("SEN-06", "Proprietário não lista proprietários (GET /api/proprietarios)", "GET", "/api/proprietarios")
        bloqueado("SEN-07", "Proprietário não consulta outro proprietário (GET /api/proprietarios/{id})", "GET",
                  f"/api/proprietarios/{b.id}")
        bloqueado("SEN-08", "Proprietário não lista contas de login (GET /api/usuarios)", "GET", "/api/usuarios")
        bloqueado("SEN-09", "Proprietário não consulta conta de outro (GET /api/usuarios/{id})", "GET",
                  f"/api/usuarios/{b.id}")
        bloqueado("SEN-10", "Proprietário não cria outras contas (POST /api/proprietarios)", "POST",
                  "/api/proprietarios", self.payload("sen-10"))

        resp = self.api.req("GET", f"/api/pessoas/{a.id}", token=a.token)
        self.r.checar("SEN-11", "sensiveis", "Titular consulta os próprios dados (GET /api/pessoas/{id})", "200",
                      resp, resp.status == 200)

        token_forjado = jwt_hs256({"sub": b.email, "id": b.id, "nome": "forjado", "roles": "ROLE_PROPRIETARIO",
                                   "iat": int(time.time()), "exp": int(time.time()) + 600}, SEGREDO_JWT_PADRAO)
        resp = self.api.req("GET", "/auth/me", token=token_forjado)
        self.r.checar("SEN-12", "sensiveis", "Token assinado com o jwt.secret padrão do código é recusado",
                      "401 (produção com segredo próprio)", resp, resp.status == 401)

        resp = self.api.req("GET", "/v3/api-docs")
        self.r.checar("SEN-13", "sensiveis", "Documentação OpenAPI fechada em produção", "401/403/404", resp,
                      resp.status in (401, 403, 404), aviso=True)

    def fluxo_front(self):
        print("\n== Fluxo de cadastro do front-app")
        origem = {"Origin": self.origem_front}

        resp = self.api.req("OPTIONS", "/auth/cadastrar", headers=origem | {
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "content-type"})
        permitido = resp.headers.get("Access-Control-Allow-Origin") if resp.headers else None
        self.r.checar("FRONT-01", "front", f"CORS libera o front-app ({self.origem_front})",
                      f"preflight 200 com Access-Control-Allow-Origin={self.origem_front}",
                      f"{resp.status} Allow-Origin={permitido}",
                      resp.status == 200 and permitido == self.origem_front)

        # Mesmo formato que front-app/src/lib/validators.ts (paraPessoaRequest): opcionais ausentes,
        # telefone e CEP só com dígitos, CPF sem máscara, RG com trim.
        corpo = {
            "nome": "QA S8 front", "telefone": "32999990000", "email": self.email("front"),
            "endereco": {"logradouro": "Rua de Teste QA", "municipio": "Rio Pomba", "uf": "MG", "cep": "36180000"},
            "tipoDocumento": "CPF", "documento": gerar_cpf(), "rg": self.proximo_rg(), "senha": SENHA_QA,
        }
        resp = self.api.req("POST", "/auth/cadastrar", corpo, headers=origem)
        conta = self.registrar_se_criou("front", corpo, resp)
        self.r.checar("FRONT-02", "front", "Cadastro com o payload do front-app (opcionais ausentes)", "201",
                      resp, resp.status == 201)

        if conta:
            login, conta.token = self.login(conta.email, conta.senha, headers=origem)
            me = self.api.req("GET", "/auth/me", token=conta.token, headers=origem)
            self.r.checar("FRONT-03", "front", "Login + GET /auth/me como o AuthContext faz", "200 e 200",
                          f"login {login.status}; /auth/me {me.status}", login.status == 200 and me.status == 200)

            corpo_dup = corpo | {"email": self.email("front-dup"), "rg": self.proximo_rg()}
            resp = self.api.req("POST", "/auth/cadastrar", corpo_dup, headers=origem)
            self.registrar_se_criou("front-dup", corpo_dup, resp)
            formato_ok = resp.status == 409 and all(
                re.match(r"^[A-Za-z.]+: .+", e) for e in (resp.json or {}).get("fieldErrors") or ["x"])
            self.r.checar("FRONT-04", "front",
                          "Erro de CPF duplicado vem como 'documento: ...' (o front mapeia para o campo CPF)",
                          "409 com fieldErrors ['documento: ...']", resp,
                          formato_ok and resp.campos_com_erro() == ["documento"])

    def exclusao_com_vinculos(self):
        print("\n== Exclusão de conta com estoque, pedido e notificação")
        try:
            vendedor = self.conta_pronta("vendedor")
            comprador = self.conta_pronta("comprador")
        except RuntimeError as e:
            for i in ("EXC-01", "EXC-02", "EXC-03"):
                self.r.pular(i, "exclusao", "Exclusão com vínculos", str(e))
            return

        produtos = self.api.req("GET", "/produtos")
        lista = produtos.json.get("content", []) if isinstance(produtos.json, dict) else (produtos.json or [])
        if not lista:
            for i in ("EXC-01", "EXC-02", "EXC-03"):
                self.r.pular(i, "exclusao", "Exclusão com vínculos", "nenhum produto no catálogo")
            return
        produto_id = lista[0]["id"]

        estoque = self.api.req("POST", "/estoques", {
            "proprietarioId": vendedor.id, "produtoId": produto_id, "descricao": "Estoque QA S8",
            "preco": 10.0, "quantidade": 50.0, "tipoPesagem": "KG", "disponibilidade": "PARA_VENDA",
            "tipoMovimentacao": "ENTRADA"}, token=vendedor.token)
        pedido = self.api.req("POST", "/pedidos", {
            "tipoPedido": "VENDA", "mensagemOpcional": "Pedido QA S8", "proprietarioRecebedorId": vendedor.id,
            "itens": [{"produtoId": produto_id, "quantidade": 1.0, "precoUnitario": 10.0}],
            "comprador": {"nome": "Comprador QA S8", "telefone": "32977776666"}}, token=comprador.token)
        if estoque.status not in (200, 201) or pedido.status not in (200, 201):
            motivo = f"preparação falhou: estoque {estoque.resumo()} / pedido {pedido.resumo()}"
            for i in ("EXC-01", "EXC-02", "EXC-03"):
                self.r.pular(i, "exclusao", "Exclusão com vínculos", motivo)
            return
        pedido_id = pedido.json.get("id")

        if self.admin_token:
            resp = self.api.req("DELETE", f"/api/pessoas/{vendedor.id}", token=self.admin_token)
            login, _ = self.login(vendedor.email, vendedor.senha)
            self.r.checar("EXC-01", "exclusao",
                          "Admin não apaga pessoa com estoque/pedidos pelo DELETE simples e a conta continua íntegra",
                          "409 com mensagem que explique o vínculo; login continua 200",
                          f"DELETE {resp.resumo()}; login {login.status}",
                          resp.status == 409 and login.status == 200
                          and "integridade" not in str((resp.json or {}).get("message", "")).lower())
        else:
            self.r.pular("EXC-01", "exclusao", "Admin tenta excluir pessoa com vínculos", "sem credenciais de admin")

        resp = self.api.req("DELETE", f"/api/pessoas/{vendedor.id}/conta", token=vendedor.token)
        if resp.status == 204:
            vendedor.removida = True
        login, _ = self.login(vendedor.email, vendedor.senha)
        self.r.checar("EXC-02", "exclusao",
                      "Titular com estoque, pedido recebido e notificação consegue excluir a conta (LGPD)",
                      "204 e login 401", f"DELETE {resp.resumo()}; login {login.status}",
                      resp.status == 204 and login.status == 401)

        resp = self.api.req("GET", f"/pedidos/{pedido_id}", token=comprador.token)
        self.r.checar("EXC-03", "exclusao", "Pedido que envolvia a conta excluída deixa de existir", "404",
                      resp, resp.status == 404)

    def limpar(self):
        print("\n== Limpeza dos dados de teste")
        restantes = []
        for conta in self.contas:
            if conta.removida:
                continue
            if not conta.token:
                _, conta.token = self.login(conta.email, conta.senha)
            resp = self.api.req("DELETE", f"/api/pessoas/{conta.id}/conta", token=conta.token) if conta.token else None
            if (resp is None or resp.status != 204) and self.admin_token:
                resp = self.api.req("DELETE", f"/api/pessoas/{conta.id}/conta", token=self.admin_token)
            if resp is not None and resp.status == 204:
                conta.removida = True
            else:
                restantes.append(f"{conta.email} ({resp.resumo() if resp else 'sem token'})")
        self.r.checar("LIMP-01", "limpeza", f"Todas as {len(self.contas)} contas de teste foram removidas",
                      "nenhuma conta qa-s8 restante", "; ".join(restantes) or "nenhuma restante", not restantes)

    def checagens_globais(self):
        print("\n== Varredura de todas as respostas")
        sensiveis, vazamentos, erros_500 = [], [], []

        def chaves(obj):
            if isinstance(obj, dict):
                for k, v in obj.items():
                    yield k
                    yield from chaves(v)
            elif isinstance(obj, list):
                for v in obj:
                    yield from chaves(v)

        for metodo, caminho, resp in self.api.historico:
            if caminho.startswith("/v3/api-docs"):
                continue
            if 200 <= resp.status < 300:
                achadas = {k for k in chaves(resp.json) if str(k).lower() in CHAVES_SENSIVEIS}
                if achadas or PADRAO_BCRYPT.search(resp.texto):
                    sensiveis.append(f"{metodo} {caminho}: {sorted(achadas) or 'hash bcrypt'}")
            elif PADRAO_VAZAMENTO.search(resp.texto):
                vazamentos.append(f"{metodo} {caminho} ({resp.status})")
            if resp.status >= 500:
                erros_500.append(f"{metodo} {caminho}: {resp.resumo()}")

        self.r.checar("SEN-14", "sensiveis", "Nenhuma resposta de sucesso traz senha ou hash",
                      "nenhuma", "; ".join(sensiveis) or "nenhuma", not sensiveis)
        self.r.checar("SEN-15", "sensiveis", "Respostas de erro não vazam stack trace, SQL ou classes internas",
                      "nenhuma", "; ".join(vazamentos) or "nenhuma", not vazamentos)
        self.r.checar("AMB-04", "ambiente", f"Nenhum 5xx nas {len(self.api.historico)} requisições",
                      "nenhum", "; ".join(erros_500[:5]) or "nenhum", not erros_500)


def main():
    parser = argparse.ArgumentParser(description="Validação do domínio de pessoas (#133)")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--admin-email", default=os.environ.get("SEMENTE_ADMIN_EMAIL"))
    parser.add_argument("--admin-senha", default=os.environ.get("SEMENTE_ADMIN_SENHA"))
    parser.add_argument("--origem-front", default="http://localhost:3000",
                        help="Origin do front-app, para conferir o CORS")
    parser.add_argument("--saida", help="Grava o resultado em JSON neste arquivo")
    args = parser.parse_args()

    api = Api(args.base_url)
    relatorio = Relatorio()
    validacao = Validacao(api, relatorio, args.admin_email, args.admin_senha, args.origem_front)
    print(f"Validação do domínio de pessoas em {args.base_url} (execução {validacao.execucao})")

    try:
        validacao.executar()
    except Exception as e:  # noqa: BLE001 -- qualquer erro interrompe, mas a limpeza ainda roda
        print(f"\nExecução interrompida: {e!r}")
        relatorio.checar("AMB-99", "ambiente", "Execução completa do roteiro", "sem interrupção", repr(e), False)
        validacao.limpar()
    validacao.checagens_globais()

    c = relatorio.contagem()
    print(f"\nResumo: {c['OK']} ok, {c['FALHA']} falhas, {c['AVISO']} avisos, {c['PULADO']} pulados")

    if args.saida:
        with open(args.saida, "w", encoding="utf-8") as f:
            json.dump({"execucao": validacao.execucao, "base_url": args.base_url, "resumo": c,
                       "resultados": [r.__dict__ for r in relatorio.itens]}, f, ensure_ascii=False, indent=2)

    sys.exit(1 if c["FALHA"] else 0)


if __name__ == "__main__":
    main()
