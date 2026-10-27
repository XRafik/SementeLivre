'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, ScrollText } from 'lucide-react';
import styles from '../legal.module.css';

export default function TermosPage() {
  const router = useRouter();

  return (
    <div className={styles.page}>
      <button type="button" className={styles.back} onClick={() => router.back()}>
        <ArrowLeft size={16} strokeWidth={2.5} />
        <span>Voltar</span>
      </button>

      <div className={styles.intro}>
        <div className={styles.introTitle}>
          <ScrollText size={20} strokeWidth={2.5} style={{ verticalAlign: '-3px', marginRight: 8 }} />
          Termos de Uso
        </div>
        <p className={styles.introText}>
          As condições para utilizar o Semente Livre e as responsabilidades de quem usa o
          sistema para gestão de bancos de sementes crioulas.
        </p>
        <p className={styles.updated}>Última atualização: 28 de setembro de 2026</p>
      </div>

      <div className={styles.card}>
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>1. Sobre o sistema</h2>
          <p className={styles.text}>
            O Semente Livre é uma plataforma de gestão de bancos de sementes crioulas mantida
            pelo IF Sudeste MG — Campus Rio Pomba. O sistema organiza o cadastro de produtores,
            o controle de estoque de sementes, a realização de trocas, vendas e doações, e a
            emissão de relatórios.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>2. Cadastro e acesso</h2>
          <ul className={styles.list}>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span>Use dados verdadeiros e completos. O cadastro é validado por CPF/CNPJ.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span>Mantenha sua senha em sigilo. Ela é armazenada criptografada e nunca é exibida.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span>Uma conta por pessoa. É vedado compartilhar credenciais de acesso.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span>Comunique ao campus qualquer uso indevido da sua conta.</span>
            </li>
          </ul>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>3. Uso adequado</h2>
          <p className={styles.text}>
            Ao usar o sistema você concorda em: registrar apenas produtos e quantidades reais;
            não usar o sistema para fins ilícitos; respeitar os dados de terceiros; e não tentar
            acessar contas ou rotinas que não lhe pertencem. Tentativas de acesso indevido
            podem ser registradas e a conta cancelada.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>4. Informações do cadastro de produtos</h2>
          <p className={styles.text}>
            O nome popular, a espécie, o formato, a origem e a história do produto são
            informações de livre acesso. Ao cadastrar um produto, você declara que os dados são
            verdadeiros e que tem autorização para expor a imagem enviada.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>5. Pedidos, trocas e doações</h2>
          <p className={styles.text}>
            O registro de um pedido cria um compromisso entre as partes envolvidas. A confirmação
            do pedido reserva o estoque informado. Cancelamentos devolvem a quantidade ao estoque
            do produto. Situações de não cumprimento devem ser comunicadas ao campus para
            avaliação.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>6. Propriedade intelectual</h2>
          <p className={styles.text}>
            Marcas, textos, layout e código do Semente Livre pertencem ao IF Sudeste MG e aos
            autores do projeto. As descrições e imagens dos produtos cadastrados continuam
            pertencendo a quem os registering.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>7. Limitação de responsabilidade</h2>
          <p className={styles.text}>
            O sistema é adequado para uso administrativo e técnico na gestão de bancos de
            sementes. O campus não se responsabiliza por decisões de compra, venda ou
            plantio tomadas exclusivamente com base nas informações cadastradas, nem por
            indisponibilidades temporárias de conectividade em campo.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>8. Alterações destes termos</h2>
          <p className={styles.text}>
            Estes termos podem ser atualizados para refletir mudanças no sistema ou na legislação.
            A data da última atualização é informada no topo desta página. O uso contínuo do
            sistema após a publicação de alterações implica aceitação das novas condições.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>9. Contato</h2>
          <p className={styles.text}>
            Dúvidas sobre estes termos ou sobre o sistema podem ser encaminhadas ao
            IF Sudeste MG — Campus Rio Pomba, pelos canais oficiais de atendimento do projeto.
          </p>
        </section>
      </div>
    </div>
  );
}
