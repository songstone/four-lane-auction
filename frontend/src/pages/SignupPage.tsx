import { useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { signup } from '../api/generated/sessions';
import type { SignupRequest } from '../api/generated/models';
import { apiErrorMessage, authFieldErrors, responseError, validateAuthForm } from '../api/errorMessages';
import { signIn } from '../features/auth/authStore';
import { loginHref, navigate, safeReturnTo } from '../features/auth/navigation';
import { useAuth } from '../features/auth/useAuth';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';

const inputClass = 'mt-2 w-full rounded-lg border border-line bg-surface px-3 py-2 text-ink focus-visible:outline-brand';

export function SignupPage({ returnTo }: { returnTo: string | null }) {
  const auth = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [registered, setRegistered] = useState(false);
  const [validation, setValidation] = useState<Record<string, string>>({});
  const registration = useMutation({ mutationFn: async (data: SignupRequest) => {
    const response = await signup(data, { credentials: 'same-origin' });
    if (response.status !== 201) throw responseError(response.data);
    return response.data;
  } });
  const login = useMutation({ mutationFn: signIn });
  const error = registered ? login.error : registration.error;
  const fields = { ...authFieldErrors(error), ...validation };
  const busy = registration.isPending || login.isPending || auth.status === 'loading';
  async function submit(event: FormEvent) {
    event.preventDefault();
    const errors = validateAuthForm(password, nickname);
    setValidation(errors);
    if (Object.keys(errors).length > 0) return;
    try {
      if (!registered) { await registration.mutateAsync({ email, password, nickname }); setRegistered(true); }
      await login.mutateAsync({ email, password });
      navigate(safeReturnTo(returnTo));
    } catch { /* Mutation owns the displayed error; a successful signup is not sent twice. */ }
  }
  return <main className="mx-auto max-w-md px-6 py-12"><Card>
    <h1 className="text-2xl font-bold">회원가입</h1>
    <p className="mt-3 text-sm text-muted">일반 회원으로 가입합니다. 닉네임은 중복 사용할 수 있습니다.</p>
    <form noValidate onSubmit={(event) => { void submit(event); }} className="mt-6 space-y-4">
      <div><label htmlFor="email">이메일</label><input id="email" name="email" type="email" autoComplete="username"
        value={email} disabled={registered} onChange={(e) => { setEmail(e.target.value); }} className={inputClass}
        aria-invalid={!!fields.email} aria-describedby={fields.email ? 'email-error' : undefined} />
        {fields.email && <p id="email-error" role="alert" className="mt-1 text-sm text-danger">{fields.email}</p>}</div>
      <div><label htmlFor="nickname">닉네임</label><input id="nickname" name="nickname" autoComplete="nickname"
        value={nickname} disabled={registered} onChange={(e) => { setNickname(e.target.value); }} className={inputClass}
        aria-invalid={!!fields.nickname} aria-describedby="nickname-help" />
        <p id="nickname-help" className={`mt-1 text-sm ${fields.nickname ? 'text-danger' : 'text-muted'}`}>{fields.nickname ?? '2~20자, 중복 허용'}</p></div>
      <div><label htmlFor="password">비밀번호</label><input id="password" name="password" type="password" autoComplete="new-password"
        value={password} onChange={(e) => { setPassword(e.target.value); }} className={inputClass}
        aria-invalid={!!fields.password} aria-describedby="password-help" />
        <p id="password-help" className={`mt-1 text-sm ${fields.password ? 'text-danger' : 'text-muted'}`}>{fields.password ?? '8~64자, UTF-8 최대 72바이트'}</p></div>
      {error != null && <p role="alert" className="text-sm text-danger">{apiErrorMessage(error)}</p>}
      {registered && <p role="status" className="text-sm text-success">가입이 완료됐습니다. 로그인을 이어서 진행합니다.</p>}
      <Button type="submit" className="w-full" disabled={busy}>{busy ? '처리 중' : registered ? '로그인 계속하기' : '가입하고 로그인'}</Button>
    </form>
    <a href={loginHref(safeReturnTo(returnTo))} className="mt-6 block text-sm text-brand">이미 계정이 있나요? 로그인</a>
  </Card></main>;
}
