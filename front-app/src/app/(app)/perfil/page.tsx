'use client';

/**
 * perfil/page.tsx — Perfil do proprietário integrado ao backend REST.
 *
 *   - Exibe o perfil completo de GET /auth/me (vem do AuthContext).
 *   - Editar: PUT /api/proprietarios/{id} e recarrega o /auth/me. CPF e RG não são editáveis.
 *     Trocar o e-mail invalida o token (o JWT usa o e-mail como subject): renova via refresh;
 *     se não der, sai e manda para o login com aviso.
 *   - Alterar senha: POST /auth/alterar-senha; senha atual errada aparece no campo.
 */

import React, { useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/feedback/Toast';
import { useRouter } from 'next/navigation';
import { Lock, LogOut, Phone, MapPin, Hash, ChevronRight, User, Shield, Mail, Pencil, Accessibility, Trash2 } from 'lucide-react';
import { alterarSenhaApi, atualizarProprietarioApi, excluirContaApi } from '@/lib/authApi';
import { isApiError, renovarToken } from '@/lib/api';
import { aplicarErrosDaApi, comMascara } from '@/lib/forms';
import {
  AlterarSenhaForm, alterarSenhaSchema, fetchCEP, formatCEP, formatCPF, formatTelefone,
  paraPessoaRequest, PerfilForm, perfilSchema,
} from '@/lib/validators';
import { PerfilUsuario } from '@/types/user';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Dialog } from '@/components/ui/dialog';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { ZoomControl } from '@/components/shared/ZoomControl';
import { InstitutionalLogo } from '@/components/shared/InstitutionalLogo';
import styles from './perfil.module.css';

const formStack: React.CSSProperties = { display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' };
const formRow: React.CSSProperties = { display: 'flex', gap: 'var(--space-3)' };
const alertBox: React.CSSProperties = { background: 'var(--color-danger-light)', color: '#fca5a5', padding: 'var(--space-3)', borderRadius: 'var(--radius-lg)', fontSize: 'var(--font-size-sm)' };

const senhaVazia: AlterarSenhaForm = { atual: '', nova: '', confirmar: '' };

function perfilParaForm(p: PerfilUsuario): PerfilForm {
  const e = p.endereco;
  return {
    nome: p.nome,
    telefone: formatTelefone(p.telefone ?? ''),
    email: p.email,
    cep: formatCEP(e?.cep ?? ''),
    logradouro: e?.logradouro ?? '',
    numero: e?.numero ?? '',
    complemento: e?.complemento ?? '',
    bairro: e?.bairro ?? '',
    municipio: e?.municipio ?? '',
    uf: e?.uf ?? '',
  };
}

export default function PerfilPage() {
  const { perfil, recarregarPerfil, logout } = useAuth();
  const { showToast } = useToast();
  const router = useRouter();
  const [showLogout, setShowLogout] = useState(false);
  const [showChangePwd, setShowChangePwd] = useState(false);
  const [pwdError, setPwdError] = useState('');
  const [editando, setEditando] = useState(false);
  const [formError, setFormError] = useState('');
  const [showExcluir, setShowExcluir] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  const [confirmacao, setConfirmacao] = useState('');

  const edicao = useForm<PerfilForm>({ resolver: zodResolver(perfilSchema) });
  const senha = useForm<AlterarSenhaForm>({ resolver: zodResolver(alterarSenhaSchema), defaultValues: senhaVazia });
  const errosEdicao = edicao.formState.errors;
  const errosSenha = senha.formState.errors;

  const handleLogout = async () => {
    await logout();
    router.push('/entrar');
  };

  // Exclusão de conta: pede a palavra "EXCLUIR" para não ser disparada por engano.
  const EXCLUIR_SENHA = 'EXCLUIR';
  const podeExcluir = confirmacao.trim().toUpperCase() === EXCLUIR_SENHA;

  const fecharExclusao = () => {
    setShowExcluir(false);
    setConfirmacao('');
  };

  const handleExcluirConta = async () => {
    if (!perfil || !podeExcluir) return;
    setExcluindo(true);
    try {
      await excluirContaApi(perfil.id);
      // A conta não existe mais: limpa a sessão local antes de redirecionar.
      await logout();
      showToast('Sua conta foi excluída.', 'success');
      router.push('/entrar');
    } catch (err) {
      setExcluindo(false);
      showToast(
        isApiError(err) ? err.message : 'Não foi possível excluir a conta. Tente novamente.',
        'error'
      );
    }
  };

  const iniciarEdicao = () => {
    if (!perfil) return;
    edicao.reset(perfilParaForm(perfil));
    setFormError('');
    setEditando(true);
  };

  const cep = comMascara(edicao.register('cep'), formatCEP);
  const handleCEP = async (e: React.ChangeEvent<HTMLInputElement>) => {
    await cep.onChange(e);
    const cleaned = e.target.value.replace(/\D/g, '');
    if (cleaned.length !== 8) return;
    const data = await fetchCEP(cleaned);
    if (data) {
      const preenchidos: [keyof PerfilForm, string][] = [
        ['logradouro', data.logradouro], ['bairro', data.bairro], ['municipio', data.localidade],
        ['uf', data.uf], ['complemento', data.complemento],
      ];
      preenchidos.filter(([, v]) => v).forEach(([campo, v]) => edicao.setValue(campo, v, { shouldValidate: true }));
    }
  };

  const salvarPerfil = async (form: PerfilForm) => {
    if (!perfil) return;
    setFormError('');
    const emailMudou = form.email.trim() !== perfil.email;
    try {
      await atualizarProprietarioApi(perfil.id, paraPessoaRequest(form));
    } catch (err) {
      const geral = aplicarErrosDaApi(err, Object.keys(edicao.getValues()), edicao.setError);
      if (geral) setFormError(geral);
      return;
    }

    // O token antigo tem o e-mail anterior como subject e deixa de valer: renova pelo refresh token.
    if (emailMudou && !(await renovarToken())) {
      await logout();
      showToast('Seu e-mail foi alterado. Entre novamente com o novo e-mail.', 'warning');
      router.replace('/entrar');
      return;
    }

    try {
      await recarregarPerfil();
    } catch {
      // falha ao recarregar já foi avisada por toast (sem conexão / sessão expirada)
    }
    setEditando(false);
    showToast('Perfil atualizado com sucesso!', 'success');
  };

  const alterarSenha = async (form: AlterarSenhaForm) => {
    setPwdError('');
    try {
      await alterarSenhaApi(form.atual, form.nova);
      fecharSenha();
      showToast('Senha alterada com sucesso!', 'success');
    } catch (err) {
      const geral = aplicarErrosDaApi(err, Object.keys(senhaVazia), senha.setError, {
        senhaAtual: 'atual',
        novaSenha: 'nova',
      });
      if (geral) setPwdError(geral);
      if (isApiError(err) && err.fieldErrors.senhaAtual) showToast(err.fieldErrors.senhaAtual, 'error');
    }
  };

  const fecharSenha = () => {
    setShowChangePwd(false);
    setPwdError('');
    senha.reset(senhaVazia);
  };

  if (!perfil) return null;

  const endereco = perfil.endereco;
  const initial = (perfil.nome || perfil.email).charAt(0).toUpperCase();

  return (
    <div className={styles.page}>
      {/* Avatar section */}
      <div className={styles.avatarSection}>
        <div className={styles.avatarRing} aria-label={`Foto de perfil — inicial ${initial}`}>
          <div className={styles.avatar}>
            <span>{initial}</span>
          </div>
        </div>
        <div className={styles.avatarInfo}>
          <h2 className={styles.name}>{perfil.nome}</h2>
          <p className={styles.email}>{perfil.email}</p>
        </div>
      </div>

      {/* Dados pessoais */}
      {!editando && (
        <>
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <User size={14} strokeWidth={2.5} className={styles.cardHeaderIcon} />
              <p className={styles.cardTitle}>Dados Pessoais</p>
              <Button variant="text" size="sm" onClick={iniciarEdicao} style={{ marginLeft: 'auto' }}>
                <Pencil size={13} strokeWidth={2} /> Editar
              </Button>
            </div>
            <div className={styles.dataList}>
              <div className={styles.dataRow}>
                <div className={styles.dataLabelWrap}>
                  <Hash size={13} strokeWidth={2} className={styles.dataIcon} />
                  <span className={styles.dataLabel}>CPF</span>
                </div>
                <span className={styles.dataValue}>{formatCPF(perfil.documento)}</span>
              </div>
              <div className={styles.dataRow}>
                <div className={styles.dataLabelWrap}>
                  <Hash size={13} strokeWidth={2} className={styles.dataIcon} />
                  <span className={styles.dataLabel}>RG</span>
                </div>
                <span className={styles.dataValue}>{perfil.rg || '—'}</span>
              </div>
              <div className={styles.dataRow}>
                <div className={styles.dataLabelWrap}>
                  <Phone size={13} strokeWidth={2} className={styles.dataIcon} />
                  <span className={styles.dataLabel}>Telefone</span>
                </div>
                <span className={styles.dataValue}>{perfil.telefone ? formatTelefone(perfil.telefone) : '—'}</span>
              </div>
              <div className={styles.dataRow}>
                <div className={styles.dataLabelWrap}>
                  <Mail size={13} strokeWidth={2} className={styles.dataIcon} />
                  <span className={styles.dataLabel}>E-mail</span>
                </div>
                <span className={styles.dataValue}>{perfil.email}</span>
              </div>
            </div>
          </div>

          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <MapPin size={14} strokeWidth={2.5} className={styles.cardHeaderIcon} />
              <p className={styles.cardTitle}>Endereço</p>
            </div>
            <div className={styles.dataList}>
              <div className={styles.dataRow}>
                <div className={styles.dataLabelWrap}>
                  <MapPin size={13} strokeWidth={2} className={styles.dataIcon} />
                  <span className={styles.dataLabel}>Logradouro</span>
                </div>
                <span className={styles.dataValue}>
                  {endereco ? [endereco.logradouro, endereco.numero, endereco.complemento].filter(Boolean).join(', ') : '—'}
                </span>
              </div>
              <div className={styles.dataRow}>
                <div className={styles.dataLabelWrap}>
                  <MapPin size={13} strokeWidth={2} className={styles.dataIcon} />
                  <span className={styles.dataLabel}>Bairro</span>
                </div>
                <span className={styles.dataValue}>{endereco?.bairro || '—'}</span>
              </div>
              <div className={styles.dataRow}>
                <div className={styles.dataLabelWrap}>
                  <MapPin size={13} strokeWidth={2} className={styles.dataIcon} />
                  <span className={styles.dataLabel}>Município</span>
                </div>
                <span className={styles.dataValue}>{endereco?.municipio || '—'}/{endereco?.uf || '—'}</span>
              </div>
              <div className={styles.dataRow}>
                <div className={styles.dataLabelWrap}>
                  <Hash size={13} strokeWidth={2} className={styles.dataIcon} />
                  <span className={styles.dataLabel}>CEP</span>
                </div>
                <span className={styles.dataValue}>{endereco?.cep ? formatCEP(endereco.cep) : '—'}</span>
              </div>
            </div>
          </div>
        </>
      )}

      {/* Editar dados pessoais e endereço */}
      {editando && (
        <form className={styles.card} onSubmit={edicao.handleSubmit(salvarPerfil)} noValidate>
          <div className={styles.cardHeader}>
            <Pencil size={14} strokeWidth={2.5} className={styles.cardHeaderIcon} />
            <p className={styles.cardTitle}>Editar Dados</p>
          </div>
          <div style={formStack}>
            {formError && <div style={alertBox} role="alert">{formError}</div>}
            <Input label="Nome completo" {...edicao.register('nome')} error={errosEdicao.nome?.message} autoComplete="name" required />
            <Input label="Telefone" {...comMascara(edicao.register('telefone'), formatTelefone)} error={errosEdicao.telefone?.message} inputMode="tel" autoComplete="tel" placeholder="(00) 00000-0000" />
            <Input label="E-mail" type="email" {...edicao.register('email')} error={errosEdicao.email?.message} autoComplete="email" required />
            <Input label="CEP" {...cep} onChange={handleCEP} error={errosEdicao.cep?.message} inputMode="numeric" autoComplete="postal-code" placeholder="00000-000" />
            <Input label="Logradouro" {...edicao.register('logradouro')} error={errosEdicao.logradouro?.message} required />
            <div style={formRow}>
              <Input label="Número" {...edicao.register('numero')} error={errosEdicao.numero?.message} maxLength={10} />
              <Input label="Complemento" {...edicao.register('complemento')} error={errosEdicao.complemento?.message} placeholder="Opcional" />
            </div>
            <Input label="Bairro" {...edicao.register('bairro')} error={errosEdicao.bairro?.message} />
            <div style={formRow}>
              <div style={{ flex: 2 }}>
                <Input label="Município" {...edicao.register('municipio')} error={errosEdicao.municipio?.message} required />
              </div>
              <div style={{ flex: 1 }}>
                <Input label="UF" {...comMascara(edicao.register('uf'), (v) => v.toUpperCase().slice(0, 2))} error={errosEdicao.uf?.message} required maxLength={2} />
              </div>
            </div>
            <div style={formRow}>
              <Button type="button" variant="ghost" onClick={() => setEditando(false)} fullWidth disabled={edicao.formState.isSubmitting}>Cancelar</Button>
              <Button type="submit" variant="primary" loading={edicao.formState.isSubmitting} fullWidth>Salvar</Button>
            </div>
          </div>
        </form>
      )}

      {/* Segurança */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <Shield size={14} strokeWidth={2.5} className={styles.cardHeaderIcon} />
          <p className={styles.cardTitle}>Segurança</p>
        </div>
        <button className={styles.actionRow} onClick={() => setShowChangePwd(true)}>
          <div className={styles.actionIcon}>
            <Lock size={15} strokeWidth={2} />
          </div>
          <span className={styles.actionLabel}>Alterar Senha</span>
          <ChevronRight size={15} className={styles.actionChevron} />
        </button>
      </div>

      {/* Acessibilidade */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <Accessibility size={14} strokeWidth={2.5} className={styles.cardHeaderIcon} />
          <p className={styles.cardTitle}>Acessibilidade</p>
        </div>
        <ZoomControl />
      </div>

      {/* Sobre e políticas */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <Shield size={14} strokeWidth={2.5} className={styles.cardHeaderIcon} />
          <p className={styles.cardTitle}>Sobre e Privacidade</p>
        </div>
        <InstitutionalLogo align="start" credit />
        <div className={styles.about}>Semente Livre v1.0.0</div>
        <div className={styles.about}>IF Sudeste MG — Campus Rio Pomba</div>
        <div className={styles.about}>Gestão de bancos de produtos crioulos para produtores rurais familiares.</div>
        <div className={styles.legalLinks}>
          <Link href="/privacidade" className={styles.legalLink}>
            Política de Privacidade
            <ChevronRight size={15} strokeWidth={2.5} />
          </Link>
          <Link href="/termos" className={styles.legalLink}>
            Termos de Uso
            <ChevronRight size={15} strokeWidth={2.5} />
          </Link>
        </div>
      </div>

      {/* Logout */}
      <button className={styles.logoutBtn} onClick={() => setShowLogout(true)}>
        <LogOut size={17} strokeWidth={2} />
        Sair do Aplicativo
      </button>

      {/* Exclusão de conta (LGPD) */}
      <div className={styles.dangerZone}>
        <p className={styles.dangerTitle}>Excluir conta</p>
        <p className={styles.dangerText}>
          Remove seu cadastro, seus pedidos e seu estoque. Os produtos que você cadastrou
          continuam visíveis no catálogo, sem os seus dados pessoais. Esta ação não pode ser
          desfeita.
        </p>
        <button className={styles.dangerBtn} onClick={() => setShowExcluir(true)}>
          <Trash2 size={16} strokeWidth={2} />
          Excluir minha conta
        </button>
      </div>

      {/* Logout confirm */}
      <ConfirmDialog
        isOpen={showLogout}
        title="Sair do aplicativo"
        description="Tem certeza que deseja sair?"
        confirmLabel="Sair"
        confirmVariant="danger"
        onConfirm={handleLogout}
        onCancel={() => setShowLogout(false)}
      />

      {/* Change password dialog */}
      <Dialog
        isOpen={showChangePwd}
        onClose={fecharSenha}
        title="Alterar Senha"
      >
        <form style={formStack} onSubmit={senha.handleSubmit(alterarSenha)} noValidate>
          {pwdError && (
            <div style={alertBox} role="alert">
              {pwdError}
            </div>
          )}
          <Input label="Senha atual" type="password" {...senha.register('atual')} error={errosSenha.atual?.message} autoComplete="current-password" required />
          <Input label="Nova senha" type="password" {...senha.register('nova')} error={errosSenha.nova?.message} hint="Mínimo 8 caracteres, com uma letra maiúscula e um número" autoComplete="new-password" required />
          <Input label="Confirmar nova senha" type="password" {...senha.register('confirmar')} error={errosSenha.confirmar?.message} autoComplete="new-password" required />
          <div style={formRow}>
            <Button type="button" variant="ghost" onClick={fecharSenha} fullWidth>Cancelar</Button>
            <Button type="submit" variant="primary" loading={senha.formState.isSubmitting} fullWidth>Salvar</Button>
          </div>
        </form>
      </Dialog>

      {/* Exclusão de conta: exige digitar EXCLUIR */}
      <Dialog isOpen={showExcluir} onClose={fecharExclusao} title="Excluir conta">
        <form style={formStack} onSubmit={(e) => { e.preventDefault(); handleExcluirConta(); }} noValidate>
          <div style={alertBox} role="alert">
            Esta ação é permanente e remove seu acesso ao aplicativo. Para confirmar, digite{' '}
            <strong>EXCLUIR</strong> no campo abaixo.
          </div>
          <Input
            label="Digite EXCLUIR para confirmar"
            value={confirmacao}
            onChange={(e) => setConfirmacao(e.target.value)}
            placeholder={EXCLUIR_SENHA}
            autoComplete="off"
            required
          />
          <div style={formRow}>
            <Button type="button" variant="ghost" onClick={fecharExclusao} fullWidth>Cancelar</Button>
            <Button
              type="submit"
              variant="danger"
              loading={excluindo}
              disabled={!podeExcluir}
              fullWidth
            >
              Excluir conta
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}
