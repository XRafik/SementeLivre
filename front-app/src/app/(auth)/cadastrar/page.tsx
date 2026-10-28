'use client';

/**
 * cadastrar/page.tsx — Cadastro de proprietário integrado ao backend REST.
 *
 *   - POST /auth/cadastrar com o payload completo (tipoDocumento, documento, rg, endereco, senha).
 *   - Validação no front com o mesmo schema do perfil (lib/validators.ts), espelhando o backend.
 *   - Erros de validação/duplicidade da API (fieldErrors) vão para o campo com setError.
 *   - Com sucesso, mostra um toast e redireciona para /entrar (sem auto-login).
 */

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { cadastrarApi } from '@/lib/authApi';
import { verificarSessao } from '@/lib/api';
import { aplicarErrosDaApi, comMascara } from '@/lib/forms';
import { useToast } from '@/components/feedback/Toast';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { AnimatedPlantSVG } from '@/components/icons/AnimatedPlantSVG';
import { InstitutionalLogo } from '@/components/shared/InstitutionalLogo';
import {
  CadastroProprietarioForm, cadastroProprietarioSchema, fetchCEP, formatCEP, formatCPF, formatTelefone,
  paraPessoaRequest,
} from '@/lib/validators';
import styles from '../entrar/entrar.module.css';
import authStyles from '../auth.module.css';
import formStyles from './cadastrar.module.css';

const valoresIniciais: CadastroProprietarioForm = {
  nome: '', cpf: '', rg: '', telefone: '', email: '', senha: '', confirmarSenha: '',
  cep: '', logradouro: '', numero: '', complemento: '', bairro: '', municipio: '', uf: '',
};

export default function CadastrarPage() {
  const router = useRouter();
  const { showToast } = useToast();
  const [error, setErrorGeral] = useState('');
  const [loadingCEP, setLoadingCEP] = useState(false);

  const {
    register, handleSubmit, setValue, setError, getValues,
    formState: { errors, isSubmitting },
  } = useForm<CadastroProprietarioForm>({
    resolver: zodResolver(cadastroProprietarioSchema),
    defaultValues: valoresIniciais,
  });

  useEffect(() => {
    let cancelado = false;
    void verificarSessao().then((sessao) => {
      if (!cancelado && sessao === 'ok') router.replace('/dashboard');
    });
    return () => {
      cancelado = true;
    };
  }, [router]);

  const cep = comMascara(register('cep'), formatCEP);
  const handleCEP = async (e: React.ChangeEvent<HTMLInputElement>) => {
    await cep.onChange(e);
    const cleaned = e.target.value.replace(/\D/g, '');
    if (cleaned.length !== 8) return;
    setLoadingCEP(true);
    const data = await fetchCEP(cleaned);
    if (data) {
      const preenchidos: [keyof CadastroProprietarioForm, string][] = [
        ['logradouro', data.logradouro], ['bairro', data.bairro], ['municipio', data.localidade],
        ['uf', data.uf], ['complemento', data.complemento],
      ];
      preenchidos.filter(([, v]) => v).forEach(([campo, v]) => setValue(campo, v, { shouldValidate: true }));
    }
    setLoadingCEP(false);
  };

  const onSubmit = async (form: CadastroProprietarioForm) => {
    setErrorGeral('');
    try {
      await cadastrarApi({
        ...paraPessoaRequest(form),
        tipoDocumento: 'CPF',
        documento: form.cpf.replace(/\D/g, ''),
        rg: form.rg.trim(),
        senha: form.senha,
      });
      showToast('Conta criada com sucesso! Entre com seu e-mail e senha.', 'success');
      router.push('/entrar');
    } catch (err: unknown) {
      const geral = aplicarErrosDaApi(err, Object.keys(getValues()), setError, { documento: 'cpf' });
      if (geral) setErrorGeral(geral);
    }
  };

  return (
    <div className={authStyles.container}>
      <div className={authStyles.hero}>
        <div className={`${authStyles.blob} ${authStyles.blob1}`}></div>
        <div className={`${authStyles.blob} ${authStyles.blob2}`}></div>

        <div className={authStyles.heroHeader}>
          <h1 className={authStyles.heroTitle}>Semente Livre</h1>
          <p className={authStyles.heroSub}>IF Sudeste MG</p>
        </div>

        <div className={authStyles.svgWrapper}>
          <AnimatedPlantSVG />
        </div>
      </div>

      <div className={authStyles.formContainer}>
        <form onSubmit={handleSubmit(onSubmit)} className={formStyles.form} noValidate>
          <h2 className={styles.title}>Criar Conta</h2>
          {error && <div className={styles.errorBox} role="alert">⚠ {error}</div>}

          <fieldset className={formStyles.fieldset}>
            <legend className={formStyles.legend}>Dados Pessoais</legend>
            <Input label="Nome completo" {...register('nome')} error={errors.nome?.message} autoComplete="name" required />
            <div className={formStyles.row}>
              <Input label="CPF" {...comMascara(register('cpf'), formatCPF)} error={errors.cpf?.message} inputMode="numeric" required placeholder="000.000.000-00" />
              <Input label="RG" {...register('rg')} error={errors.rg?.message} required maxLength={20} />
            </div>
            <Input label="Telefone" {...comMascara(register('telefone'), formatTelefone)} error={errors.telefone?.message} inputMode="tel" autoComplete="tel" placeholder="(00) 00000-0000" />
          </fieldset>

          <fieldset className={formStyles.fieldset}>
            <legend className={formStyles.legend}>Acesso</legend>
            <Input label="E-mail" type="email" {...register('email')} error={errors.email?.message} autoComplete="email" required />
            <Input label="Senha" type="password" {...register('senha')} error={errors.senha?.message} hint="Mínimo 8 caracteres, com uma letra maiúscula e um número" autoComplete="new-password" required />
            <Input label="Confirmar Senha" type="password" {...register('confirmarSenha')} error={errors.confirmarSenha?.message} autoComplete="new-password" required />
          </fieldset>

          <fieldset className={formStyles.fieldset}>
            <legend className={formStyles.legend}>Endereço</legend>
            <Input label="CEP" {...cep} onChange={handleCEP} error={errors.cep?.message} inputMode="numeric" autoComplete="postal-code" placeholder="00000-000" hint={loadingCEP ? 'Buscando CEP...' : undefined} />
            <Input label="Logradouro" {...register('logradouro')} error={errors.logradouro?.message} required />
            <div className={formStyles.row}>
              <Input label="Número" {...register('numero')} error={errors.numero?.message} maxLength={10} />
              <Input label="Complemento" {...register('complemento')} error={errors.complemento?.message} placeholder="Opcional" />
            </div>
            <Input label="Bairro" {...register('bairro')} error={errors.bairro?.message} />
            <div className={formStyles.row}>
              <div style={{ flex: 2 }}>
                <Input label="Município" {...register('municipio')} error={errors.municipio?.message} required />
              </div>
              <div style={{ flex: 1 }}>
                <Input label="UF" {...comMascara(register('uf'), (v) => v.toUpperCase().slice(0, 2))} error={errors.uf?.message} required maxLength={2} />
              </div>
            </div>
          </fieldset>

          <Button type="submit" fullWidth loading={isSubmitting} size="lg">
            Criar Conta
          </Button>
        </form>

        <p className={styles.registerLink}>
          Já tem conta? <Link href="/entrar">Entrar</Link>
        </p>

        <InstitutionalLogo />
      </div>
    </div>
  );
}
