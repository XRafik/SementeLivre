package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.service.RelatorioEstoqueService;
import com.sementelivre.backend.service.RelatorioPedidoService;
import com.sementelivre.backend.util.RelatorioCsv;
import com.sementelivre.backend.util.RelatorioPdf;

/**
 * Download dos relatorios em PDF e CSV (RF-07, issue #95).
 *
 * <p>Fica separado do RelatorioController, que cuida do CRUD do historico de
 * relatorios, para cada arquivo tratar de um assunto so.</p>
 *
 * <p>Cada proprietario so baixa o proprio relatorio; o admin baixa de qualquer
 * um. E a mesma regra ja usada em PessoaController e ProprietarioController.</p>
 */
@RestController
@RequestMapping("/relatorios")
@Tag(name = "Relatórios - exportação", description = "Relatórios de estoque e pedidos em PDF e CSV.")
public class RelatorioExportacaoController {

    private final RelatorioEstoqueService relatorioEstoqueService;
    private final RelatorioPedidoService relatorioPedidoService;

    public RelatorioExportacaoController(
            RelatorioEstoqueService relatorioEstoqueService,
            RelatorioPedidoService relatorioPedidoService) {

        this.relatorioEstoqueService = relatorioEstoqueService;
        this.relatorioPedidoService = relatorioPedidoService;
    }

    // ---- Relatorio de estoque ----

    @GetMapping("/estoque/pdf")
    @PreAuthorize("hasRole('ADMIN') or #proprietarioId == principal.id")
    public ResponseEntity<byte[]> estoqueEmPdf(
            @RequestParam UUID proprietarioId,
            @RequestParam(required = false) EspecieGeral especie,
            @RequestParam(required = false) Disponibilidade disponibilidade) {

        List<String[]> linhas = relatorioEstoqueService.montarLinhas(proprietarioId, especie, disponibilidade);

        byte[] arquivo = RelatorioPdf.gerar(
                RelatorioEstoqueService.TITULO, RelatorioEstoqueService.CABECALHOS, linhas);

        return download(arquivo, "relatorio-estoque.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/estoque/csv")
    @PreAuthorize("hasRole('ADMIN') or #proprietarioId == principal.id")
    public ResponseEntity<byte[]> estoqueEmCsv(
            @RequestParam UUID proprietarioId,
            @RequestParam(required = false) EspecieGeral especie,
            @RequestParam(required = false) Disponibilidade disponibilidade) {

        List<String[]> linhas = relatorioEstoqueService.montarLinhas(proprietarioId, especie, disponibilidade);

        byte[] arquivo = RelatorioCsv.gerar(RelatorioEstoqueService.CABECALHOS, linhas);

        return download(arquivo, "relatorio-estoque.csv", MediaType.valueOf("text/csv"));
    }

    // ---- Relatorio de pedidos ----

    @GetMapping("/pedidos/pdf")
    @PreAuthorize("hasRole('ADMIN') or #proprietarioId == principal.id")
    public ResponseEntity<byte[]> pedidosEmPdf(
            @RequestParam UUID proprietarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoPedido tipoPedido,
            @RequestParam(required = false) StatusPedido status) {

        List<String[]> linhas = relatorioPedidoService.montarLinhas(
                proprietarioId, dataInicio, dataFim, tipoPedido, status);

        byte[] arquivo = RelatorioPdf.gerar(
                RelatorioPedidoService.TITULO, RelatorioPedidoService.CABECALHOS, linhas);

        return download(arquivo, "relatorio-pedidos.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/pedidos/csv")
    @PreAuthorize("hasRole('ADMIN') or #proprietarioId == principal.id")
    public ResponseEntity<byte[]> pedidosEmCsv(
            @RequestParam UUID proprietarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoPedido tipoPedido,
            @RequestParam(required = false) StatusPedido status) {

        List<String[]> linhas = relatorioPedidoService.montarLinhas(
                proprietarioId, dataInicio, dataFim, tipoPedido, status);

        byte[] arquivo = RelatorioCsv.gerar(RelatorioPedidoService.CABECALHOS, linhas);

        return download(arquivo, "relatorio-pedidos.csv", MediaType.valueOf("text/csv"));
    }

    // Content-Disposition: attachment faz o navegador baixar em vez de abrir
    private ResponseEntity<byte[]> download(byte[] arquivo, String nomeArquivo, MediaType tipo) {

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(tipo)
                .body(arquivo);
    }
}
