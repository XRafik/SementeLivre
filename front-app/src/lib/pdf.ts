'use client';

import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { DisponibilidadeLabels, type Estoque } from '@/types/stock';
import { EspecieGeralLabels } from '@/types/seed';
import { TipoPedidoLabels, StatusPedidoLabels, type Pedido } from '@/types/order';
import { LOGOS_IF } from '@/lib/institutionalLogo';

export type PdfTipo = 'ESTOQUE' | 'PEDIDOS';

interface PdfContexto {
  tipo: PdfTipo;
  filtroDisponibilidade?: string;
  filtroEspecie?: string;
  filtroStatus?: string;
  filtroTipo?: string;
  nomeSemente?: string;
  dataInicio?: string;
  dataFim?: string;
  seeds: Estoque[];
  orders: Pedido[];
}

function formatarDataBr(iso: string): string {
  if (!iso) return '—';
  const [ano, mes, dia] = iso.split('-');
  if (!ano || !mes || !dia) return iso;
  return `${dia}/${mes}/${ano}`;
}

function dataDeBr(d: Date): string {
  return d.toLocaleDateString('pt-BR');
}

/** Bytes do PNG da logo do IF; sem ela (ex.: offline) o relatório sai mesmo assim. */
async function carregarLogoIF(): Promise<Uint8Array | null> {
  try {
    const res = await fetch(LOGOS_IF.divisoria.src);
    if (!res.ok) return null;
    return new Uint8Array(await res.arrayBuffer());
  } catch {
    return null;
  }
}

/**
 * Monta o PDF no navegador e dispara o download.
 * Não há endpoint de PDF no backend: o arquivo é gerado no cliente a partir
 * dos mesmos dados que a prévia exibe, e a emissão já foi registrada em /relatorios.
 */
export async function baixarRelatorioPdf({ tipo, filtroDisponibilidade, filtroEspecie, filtroStatus, filtroTipo, nomeSemente, dataInicio, dataFim, seeds, orders }: PdfContexto): Promise<void> {
  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });
  const ehEstoque = tipo === 'ESTOQUE';

  // ── Cabeçalho ────────────────────────────────────────────────────────────
  // Logo do IF no canto direito, na proporção do arquivo. Com 15mm de altura o símbolo
  // fica com ~9mm de largura (~34px), acima do mínimo de 30px do manual da marca.
  // 'SLOW' é compressão sem perda: sem ela o PNG entra cru e o PDF passa de 4 MB.
  const logoIF = await carregarLogoIF();
  if (logoIF) {
    const { largura, altura } = LOGOS_IF.divisoria;
    const alturaMm = 15;
    const larguraMm = (alturaMm * largura) / altura;
    doc.addImage(logoIF, 'PNG', doc.internal.pageSize.getWidth() - 14 - larguraMm, 8, larguraMm, alturaMm, undefined, 'SLOW');
  }

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(18);
  doc.setTextColor(47, 158, 65);
  doc.text('Semente Livre', 14, 18);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(13);
  doc.setTextColor(30, 30, 30);
  doc.text(ehEstoque ? 'Relatório de Estoque de Produtos' : 'Relatório de Pedidos Realizados', 14, 26);

  doc.setFontSize(9);
  doc.setTextColor(120, 120, 120);
  doc.text(`Emitido em ${new Date().toLocaleString('pt-BR')}`, 14, 32);

  // ── Filtros aplicados ────────────────────────────────────────────────────
  const filtros: string[] = [];
  if (ehEstoque) {
    filtros.push(`Disponibilidade: ${filtroDisponibilidade && filtroDisponibilidade !== 'TODOS' ? DisponibilidadeLabels[filtroDisponibilidade as keyof typeof DisponibilidadeLabels] ?? filtroDisponibilidade : 'Todas'}`);
    if (filtroEspecie && filtroEspecie !== 'TODOS') {
      filtros.push(`Espécie: ${EspecieGeralLabels[filtroEspecie as keyof typeof EspecieGeralLabels]}`);
    }
  } else {
    filtros.push(`Status: ${filtroStatus && filtroStatus !== 'TODOS' ? StatusPedidoLabels[filtroStatus as keyof typeof StatusPedidoLabels] ?? filtroStatus : 'Todos'}`);
    if (filtroTipo && filtroTipo !== 'TODOS') {
      filtros.push(`Tipo: ${TipoPedidoLabels[filtroTipo as keyof typeof TipoPedidoLabels]}`);
    }
    if (nomeSemente) filtros.push(`Produto: ${nomeSemente}`);
    if (dataInicio) filtros.push(`De: ${formatarDataBr(dataInicio)}`);
    if (dataFim) filtros.push(`Até: ${formatarDataBr(dataFim)}`);
  }
  filtros.push(`Total de registros: ${ehEstoque ? seeds.length : orders.length}`);

  let cursorY = 38;
  filtros.forEach((f) => {
    doc.text(f, 14, cursorY);
    cursorY += 4.5;
  });

  // ── Tabela ───────────────────────────────────────────────────────────────
  const head = ehEstoque
    ? ['Produto', 'Quantidade', 'Unidade', 'Disponibilidade', 'Preço']
    : ['ID', 'Data', 'Recebedor', 'Tipo', 'Status', 'Total'];
  const body = ehEstoque
    ? seeds.map((s) => [
        s.nomePopular,
        String(s.quantidade ?? ''),
        s.tipoPesagem ?? '',
        DisponibilidadeLabels[s.disponibilidade] ?? s.disponibilidade,
        s.preco != null ? s.preco.toFixed(2) : '—',
      ])
    : orders.map((o) => [
        `#${o.idPedido.slice(-6).toUpperCase()}`,
        dataDeBr(o.dataPedido),
        o.nomeRecebedor,
        TipoPedidoLabels[o.tipoPedido] ?? o.tipoPedido,
        StatusPedidoLabels[o.status] ?? o.status,
        o.totalValor != null ? o.totalValor.toFixed(2) : '—',
      ]);

  autoTable(doc, {
    startY: cursorY + 2,
    head: [head],
    body: body.length ? body : [['—']],
    styles: { fontSize: 9, cellPadding: 2, overflow: 'linebreak' },
    headStyles: { fillColor: [47, 158, 65], textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: [245, 250, 245] },
    margin: { left: 14, right: 14 },
    didDrawPage: () => {
      const altura = doc.internal.pageSize.getHeight();
      const largura = doc.internal.pageSize.getWidth();
      doc.setFontSize(8);
      doc.setTextColor(140, 140, 140);
      doc.text('Semente Livre — IF Sudeste MG, Campus Rio Pomba', 14, altura - 8);
      doc.text(`Página ${doc.getNumberOfPages()}`, largura - 14, altura - 8, { align: 'right' });
    },
  });

  // Só há sentido em "de N" quando o relatório quebrou de página.
  const total = doc.getNumberOfPages();
  if (total > 1) {
    for (let p = 1; p <= total; p++) {
      doc.setPage(p);
      doc.setFontSize(8);
      doc.setTextColor(140, 140, 140);
      doc.text(
        `Página ${p} de ${total}`,
        doc.internal.pageSize.getWidth() - 14,
        doc.internal.pageSize.getHeight() - 8,
        { align: 'right' }
      );
    }
  }

  doc.save(`relatorio-${tipo.toLowerCase()}-${Date.now()}.pdf`);
}
