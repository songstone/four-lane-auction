import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { BidPolicyCreateRequest, BidPolicyResponse } from '../api/generated/models';
import { createBidPolicy } from '../api/generated/sessions';
import { adminRequest } from '../api/adminItemQueries';
import { useBidPolicies } from '../api/bidPolicyQueries';
import { ApiRequestError, apiErrorMessage, policyFieldErrors, responseError } from '../api/errorMessages';
import type { FieldError } from '../api/errorMessages';
import { tierDraft, validatePolicyForm } from '../features/auction/policy/validation';
import type { TierDraft } from '../features/auction/policy/validation';
import { useAuth } from '../features/auth/useAuth';
import { requestConnectionAuth } from '../features/auth/authStore';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Spinner } from '../components/ui/Spinner';
import { EmptyState } from '../components/ui/EmptyState';

const money = (value: number) => `${value.toLocaleString('ko-KR')}원`;
const drafts = (policy: BidPolicyResponse | undefined) => policy ? policy.tiers.map(tier => tierDraft(String(tier.minPrice), String(tier.increment))) : [tierDraft('0')];
const inputClass = 'mt-2 w-full min-w-0 rounded-lg border border-line bg-surface px-3 py-2 text-sm focus-visible:outline-brand';

function PolicyEditor({ policies, memberId }: { policies: BidPolicyResponse[]; memberId: number }) {
  const cache = useQueryClient();
  const [rows, setRows] = useState<TierDraft[]>(() => drafts(policies[0]));
  const [validation, setValidation] = useState<FieldError[]>([]);
  const submitting = useRef(false);
  const mutation = useMutation({
    retry: false,
    mutationFn: async ({ input, identity }: { input: BidPolicyCreateRequest; identity: string }) => {
      const response = await adminRequest(options => createBidPolicy(input, options), undefined, identity);
      if (response.status !== 201) throw responseError(response.data);
      return response.data;
    },
    onSettled: async (_data, _error, variables) => {
      try {
        if (variables.identity === requestConnectionAuth().identity)
          await cache.invalidateQueries({ queryKey: ['adminBidPolicies', memberId] });
      } finally { submitting.current = false; }
    },
  });
  const fields = policyFieldErrors(validation.length > 0 ? validation : mutation.error instanceof ApiRequestError ? mutation.error.fields : []);
  function edit(next: TierDraft[]) { setRows(next); setValidation([]); mutation.reset(); }
  function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting.current || mutation.isPending) return;
    const result = validatePolicyForm(rows);
    setValidation(result.fields);
    if (!result.input) return;
    submitting.current = true;
    mutation.mutate({ input: result.input, identity: requestConnectionAuth().identity });
  }
  return <Card>
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-sm font-semibold text-brand">새 버전</p>
      <h2 className="mt-2 text-xl font-bold">호가 구간 등록</h2></div>
      <Button variant="secondary" disabled={mutation.isPending || policies.length === 0} onClick={() => { edit(drafts(policies[0])); }}>최신 정책 불러오기</Button></div>
    <p className="mt-3 text-sm leading-relaxed text-muted">기존 버전은 보존됩니다. 구간을 바꾸면 새 버전으로 등록하세요.</p>
    <form className="mt-6 space-y-4" noValidate onSubmit={submit}>
      <fieldset disabled={mutation.isPending} className="min-w-0 space-y-4">
        <legend className="sr-only">새 정책의 호가 구간</legend>
        {rows.map((row, index) => <div key={row.key} className="rounded-lg border border-line p-4">
          <div className="flex items-center justify-between gap-3"><h3 className="text-sm font-semibold">구간 {index + 1}</h3>
            <Button variant="secondary" disabled={rows.length === 1 || mutation.isPending} onClick={() => { edit(rows.filter(tier => tier.key !== row.key)); }} aria-label={`구간 ${index + 1} 삭제`}>삭제</Button></div>
          <div className="mt-3 grid min-w-0 gap-4 sm:grid-cols-2">{(['minPrice', 'increment'] as const).map(name => {
            const field = `tiers[${index}].${name}`;
            const id = `policy-${row.key}-${name}`;
            return <div key={name} className="min-w-0"><label className="text-sm" htmlFor={id}>{name === 'minPrice' ? '구간 하한 (원)' : '호가 단위 (원)'}</label>
              <input id={id} inputMode="numeric" value={row[name]} className={inputClass} aria-invalid={!!fields[field]}
                aria-describedby={fields[field] ? `${id}-error` : 'policy-rules'}
                onChange={event => { edit(rows.map(tier => tier.key === row.key ? { ...tier, [name]: event.target.value } : tier)); }} />
              {fields[field] && <p id={`${id}-error`} role="alert" className="mt-2 text-sm text-danger">{fields[field]}</p>}
            </div>;
          })}</div>
        </div>)}
        <Button variant="secondary" onClick={() => { edit([...rows, tierDraft()]); }}>구간 추가</Button>
      </fieldset>
      <div id="policy-rules" className="rounded-lg bg-canvas p-4 text-xs leading-relaxed text-muted">
        <p>첫 구간 하한은 0원이며, 다음 구간의 하한은 앞 구간보다 커야 합니다.</p>
        <p className="mt-1">호가 단위는 1,000원의 양수 배수입니다. 두 값은 각각 최대 9,007,199,254,740,991원입니다.</p>
      </div>
      {fields.tiers && <p role="alert" className="text-sm text-danger">{fields.tiers}</p>}
      {mutation.isError && <div role="alert" className="rounded-lg bg-danger-soft p-4 text-sm text-danger">
        <p>{apiErrorMessage(mutation.error)}</p><p className="mt-2">등록 결과가 불확실하면 다시 제출하기 전에 목록 새로고침으로 확인해 주세요.</p>
      </div>}
      {mutation.isSuccess && <p role="status" className="rounded-lg bg-success-soft p-4 text-sm text-success">v{mutation.data.version}을 등록했습니다.</p>}
      <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? '등록 중…' : '새 버전 등록'}</Button>
    </form>
  </Card>;
}

export function AdminBidPolicyPage() {
  const auth = useAuth();
  const query = useBidPolicies(auth.member?.memberId);
  return <main className="mx-auto max-w-6xl px-6 py-10">
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-semibold text-brand">운영자</p>
      <h1 className="mt-2 text-3xl font-bold">호가 정책 관리</h1>
      <p className="mt-3 max-w-2xl text-sm leading-relaxed text-muted">새 버전은 이후 시작하는 회차부터 적용됩니다. 진행 중인 회차는 시작할 때 고정한 버전을 유지합니다.</p></div>
      <Button variant="secondary" disabled={query.isFetching} onClick={() => { void query.refetch(); }}>목록 새로고침</Button></div>
    {query.isPending ? <Card><Spinner label="호가 정책을 불러오는 중" /></Card> : query.isError ? <Card>
      <p role="alert" className="text-danger">{apiErrorMessage(query.error)}</p>
      <Button variant="secondary" className="mt-4" onClick={() => { void query.refetch(); }}>목록 다시 조회</Button>
    </Card> : <div className="grid items-start gap-6 lg:grid-cols-2">
      <section aria-label="정책 버전 목록" className="min-w-0 space-y-4">
        {query.data.length === 0 ? <Card><EmptyState title="등록된 정책이 없습니다" description="첫 구간이 0원인 정책을 등록해 주세요." /></Card> : query.data.map(policy => <Card key={policy.policyId}>
          <div className="flex flex-wrap items-center gap-3"><h2 className="text-xl font-bold">v{policy.version}</h2>{policy.isLatest && <Badge tone="success">다음 회차 적용</Badge>}</div>
          <p className="mt-3 break-words text-xs text-muted"><time dateTime={policy.createdAt}>{new Date(policy.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}</time> · {policy.createdByNickname ?? '기본 정책'}</p>
          <dl className="mt-4 divide-y divide-line">{policy.tiers.map((tier, index) => <div key={tier.minPrice} className="grid min-w-0 grid-cols-2 gap-3 py-3 text-sm">
            <dt className="min-w-0 break-words">{money(tier.minPrice)} 이상{policy.tiers[index + 1] ? <span className="mt-1 block text-xs text-muted">{money(policy.tiers[index + 1]!.minPrice)} 미만</span> : null}</dt>
            <dd className="min-w-0 break-words text-right font-semibold">+{money(tier.increment)}</dd>
          </div>)}</dl>
        </Card>)}
      </section>
      <section aria-label="새 정책 등록" className="min-w-0">{auth.member && <PolicyEditor key={auth.member.memberId} policies={query.data} memberId={auth.member.memberId} />}</section>
    </div>}
  </main>;
}
