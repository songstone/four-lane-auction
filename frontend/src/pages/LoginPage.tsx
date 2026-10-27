import { useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { apiErrorMessage, authFieldErrors, validateAuthForm } from '../api/errorMessages';
import { useAuth } from '../features/auth/useAuth';
import { signIn } from '../features/auth/authStore';
import { navigate, safeReturnTo } from '../features/auth/navigation';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';

const inputClass = 'mt-2 w-full rounded-lg border border-line bg-surface px-3 py-2 text-ink focus-visible:outline-brand';

export function LoginPage({ returnTo }: { returnTo: string | null }) {
  const auth = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [validation, setValidation] = useState<Record<string, string>>({});
  const mutation = useMutation({ mutationFn: signIn, onSuccess: () => { navigate(safeReturnTo(returnTo)); } });
  const fields = { ...authFieldErrors(mutation.error), ...validation };
  const busy = mutation.isPending || auth.status === 'loading';
  function submit(event: FormEvent) {
    event.preventDefault();
    const errors = validateAuthForm(password);
    setValidation(errors);
    if (Object.keys(errors).length === 0) mutation.mutate({ email, password });
  }
  return <main className="mx-auto max-w-md px-6 py-12"><Card>
    <h1 className="text-2xl font-bold">로그인</h1>
    <p className="mt-3 text-sm text-muted">로그인하면 원래 보던 화면으로 돌아갑니다.</p>
    <form onSubmit={submit} noValidate className="mt-6 space-y-4">
      <div><label htmlFor="email">이메일</label><input id="email" name="email" type="email" autoComplete="username"
        value={email} onChange={(e) => { setEmail(e.target.value); }} className={inputClass}
        aria-invalid={!!fields.email} aria-describedby={fields.email ? 'email-error' : undefined} />
        {fields.email && <p id="email-error" role="alert" className="mt-1 text-sm text-danger">{fields.email}</p>}</div>
      <div><label htmlFor="password">비밀번호</label><input id="password" name="password" type="password" autoComplete="current-password"
        value={password} onChange={(e) => { setPassword(e.target.value); }} className={inputClass}
        aria-invalid={!!fields.password} aria-describedby={fields.password ? 'password-error' : undefined} />
        {fields.password && <p id="password-error" role="alert" className="mt-1 text-sm text-danger">{fields.password}</p>}</div>
      {mutation.isError && <p role="alert" className="text-sm text-danger">{apiErrorMessage(mutation.error)}</p>}
      <Button type="submit" className="w-full" disabled={busy}>{busy ? '확인 중' : '로그인'}</Button>
    </form>
    <a className="mt-6 block text-sm text-brand" href={`#/signup?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`}>처음이신가요? 회원가입</a>
  </Card></main>;
}
