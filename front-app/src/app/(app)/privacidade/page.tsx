'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, ShieldCheck } from 'lucide-react';
import styles from '../legal.module.css';

export default function PrivacidadePage() {
  const router = useRouter();

  return (
    <div className={styles.page}>
      <button type="button" className={styles.back} onClick={() => router.back()}>
        <ArrowLeft size={16} strokeWidth={2.5} />
        <span>Voltar</span>
      </button>

      <div className={styles.intro}>
        <div className={styles.introTitle}>
          <ShieldCheck size={20} strokeWidth={2.5} style={{ verticalAlign: '-3px', marginRight: 8 }} />
          Política de Privacidade
        </div>
        <p className={styles.introText}>
          O Semente Livre trata dados pessoais conforme a Lei Geral de Proteção de Dados
          (Lei nº 13.709/2018). Esta página explica quais dados coletamos, por que coletamos
          e como você exerce seus direitos.
        </p>
        <p className={styles.updated}>Última atualização: 28 de setembro de 2026</p>
      </div>

      <div className={styles.card}>
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>1. Quem é o controlador</h2>
          <p className={styles.text}>
            O Semente Livre é um sistema de gestão de bancos de sementes crioulas desenvolvido
            pelo IF Sudeste MG — Campus Rio Pomba, com o objetivo de apoiar produtores rurais
            familiares. O campus é o controlador dos dados pessoais coletados.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>2. Quais dados coletamos</h2>
          <ul className={styles.list}>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span><span className={styles.strong}>Identificação:</span> nome completo, CPF ou CNPJ, RG e data de cadastro.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span><span className={styles.strong}>Contato:</span> e-mail e telefone.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span><span className={styles.strong}>Endereço:</span> logradouro, número, complemento, bairro, município, UF e CEP.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span><span className={styles.strong}>Atividade no sistema:</span> produtos cadastrados, estoque, pedidos e relatórios gerados.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span><span className={styles.strong}>Segurança:</span> senha guardada apenas em formato criptografado (BCrypt).</span>
            </li>
          </ul>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>3. Para que usamos seus dados</h2>
          <p className={styles.text}>
            Usamos seus dados exclusivamente para operar o banco de sementes: manter seu
            cadastro, permitir o controle de estoque, registrar e acompanhar pedidos entre
            produtores, gerar relatórios e proteger o acesso à sua conta.
          </p>
          <p className={styles.text}>
            Não vendemos, alugamos nem cedemos seus dados a terceiros para fins publicitários.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>4. Base legal</h2>
          <p className={styles.text}>
            O tratamento se apoia no <span className={styles.strong}>consentimento</span> do titular
            nasignup e no <span className={styles.strong}>cumprimento de obrigação legal</span> e no
            <span className={styles.strong}> legítimo interesse</span> do campus na operação do programa
            de manejo de sementes.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>5. Com quem compartilhamos</h2>
          <p className={styles.text}>
            Os dados ficam restritos às pessoas autorizadas a operar o sistema. Podem ser
            compartilhados quando houver obrigação legal, ordem judicial ou,
            para dados de contato, quando você escolher exibir seu perfil no site público
            do Semente Livre — essa opção é desligada por padrão.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>6. Por quanto tempo guardamos</h2>
          <p className={styles.text}>
            Mantemos os dados enquanto sua conta estiver ativa. Após a solicitação de exclusão,
            o cadastro e os dados operacionais vinculados são removidos. Registros de acesso
            ao sistema podem ser mantidos por até 6 (seis) meses para fins de segurança e
            auditoria, conforme a política de registros de logs.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>7. Seus direitos (LGPD)</h2>
          <p className={styles.text}>Como titular, você pode a qualquer momento:</p>
          <ul className={styles.list}>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span><span className={styles.strong}>Confirmar</span> a existência de tratamento dos seus dados.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span><span className={styles.strong}>Acessar</span> e corriger dados incompletos ou desatualizados.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span>Solicitar a <span className={styles.strong}>anonimização, bloqueio ou eliminação</span> de dados desnecessários.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span><span className={styles.strong}>Revogar</span> o consentimento e solicitar a portabilidade.</span>
            </li>
            <li className={styles.item}>
              <span className={styles.bullet}>•</span>
              <span>Receber informação sobre <span className={styles.strong}>compartilhamentos</span> com terceiros.</span>
            </li>
          </ul>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>8. Como exercer seus direitos</h2>
          <p className={styles.text}>
            Corrija seus dados diretamente na tela <span className={styles.strong}>Meu Perfil</span> e, em
            <span className={styles.strong}> Editar Dados</span>. Para revogar o consentimento ou pedir a
            eliminação da conta, use a opção <span className={styles.strong}>Excluir conta</span> na mesma tela.
            Para qualquer outra solicitação, fale com o campus pelos canais de atendimento do
            projeto.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>9. Cookies e armazenamento local</h2>
          <p className={styles.text}>
            O aplicativo guarda localmente o token de sessão e a sua preferência de tamanho de
            texto. Esses dados não são compartilhados com terceiros e podem ser apagados
            limpando o armazenamento do navegador ou excluindo a conta.
          </p>
        </section>
      </div>
    </div>
  );
}
