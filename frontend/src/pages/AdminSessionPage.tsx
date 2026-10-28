import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createSession, patchSession } from '../api/generated/sessions';
import { SessionStatus } from '../api/generated/models';
import type { AdminSessionResponse, SessionCreateRequest, SessionPatchRequest, SessionTimingRequest } from '../api/generated/models';
import { adminRequest } from '../api/adminItemQueries';
import { useAdminSession } from '../api/adminSessionQueries';
import { useSessionList } from '../api/sessionQueries';
import { ApiRequestError, apiErrorMessage, responseError, sessionFieldErrors } from '../api/errorMessages';
import type { FieldError } from '../api/errorMessages';
import { useAuth } from '../features/auth/useAuth';
import { requestConnectionAuth } from '../features/auth/authStore';
import { sessionDraft, timingNames, timingLabels, validateSessionDraft } from '../features/auction/session/validation';
import type { SessionDraft } from '../features/auction/session/validation';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Spinner } from '../components/ui/Spinner';
import { EmptyState } from '../components/ui/EmptyState';

const inputClass = 'mt-2 w-full min-w-0 rounded-lg border border-line bg-surface px-3 py-2 text-sm focus-visible:outline-brand';
const dateLabel = (value: string) => new Date(value).toLocaleString('ko-KR', { hour12: false });
const stateLabels: Record<SessionStatus, string> = { SCHEDULED: '예정', RUNNING: '진행 중', FINISHED: '종료' };

function Fields({ prefix, draft, change, fields, creating }: {
  prefix: string; draft: SessionDraft; change: (draft: SessionDraft) => void; fields: Record<string, string>; creating: boolean;
}) {
  return <div className="space-y-4">
    {creating && <div><label htmlFor={`${prefix}-lanes`} className="text-sm">레인 수</label>
      <select id={`${prefix}-lanes`} className={inputClass} value={draft.laneCount} onChange={event => { change({ ...draft, laneCount: event.target.value }); }}>
        {[1, 2, 3, 4].map(count => <option key={count} value={count}>{count}개</option>)}
      </select>{fields.laneCount && <p role="alert" className="mt-2 text-sm text-danger">{fields.laneCount}</p>}
    </div>}
    <div><label htmlFor={`${prefix}-schedule`} className="text-sm">예정 시작 시각 (현지)</label>
      <input id={`${prefix}-schedule`} type="datetime-local" step="0.001" className={inputClass} value={draft.scheduledStartAt}
        aria-invalid={!!fields.scheduledStartAt} aria-describedby={fields.scheduledStartAt ? `${prefix}-schedule-error` : undefined}
        onChange={event => { change({ ...draft, scheduledStartAt: event.target.value }); }} />
      {fields.scheduledStartAt && <p id={`${prefix}-schedule-error`} role="alert" className="mt-2 text-sm text-danger">{fields.scheduledStartAt}</p>}
      <p className="mt-2 text-xs text-muted">{creating ? '브라우저 현지 시각으로 입력하세요. 서버의 현재 시각보다 뒤여야 합니다.' : '예정 시각을 바꿀 때는 서버의 현재 시각보다 뒤로 지정하세요. 지난 시각을 그대로 두고 시간 값만 바꿀 수도 있습니다.'}</p>
    </div>
    <div className="grid min-w-0 gap-4 sm:grid-cols-2">{timingNames.map(name => {
      const id = `${prefix}-${name}`; const field = `timing.${name}`;
      return <div key={name} className="min-w-0"><label htmlFor={id} className="text-sm">{timingLabels[name]}</label>
        <input id={id} inputMode="numeric" className={inputClass} value={draft.timing[name]} placeholder={creating ? '서버 기본값 사용' : undefined}
          aria-invalid={!!fields[field]} aria-describedby={fields[field] ? `${id}-error` : undefined}
          onChange={event => { change({ ...draft, timing: { ...draft.timing, [name]: event.target.value } }); }} />
        {fields[field] && <p id={`${id}-error`} role="alert" className="mt-2 text-sm text-danger">{fields[field]}</p>}
      </div>;
    })}</div>
    <p className="text-xs leading-relaxed text-muted">각 시간 값은 1,000~60,000ms입니다.{creating ? ' 비워 두면 서버 기본값을 사용합니다.' : ' 실제 시작 전까지 변경할 수 있습니다.'}</p>
  </div>;
}

function Failure({ error }: { error: unknown }) {
  return <div role="alert" className="rounded-lg bg-danger-soft p-4 text-sm text-danger">
    <p>{apiErrorMessage(error)}</p>
    {(!(error instanceof ApiRequestError) || error.code === 'INTERNAL_ERROR') && <p className="mt-2">{apiErrorMessage(new ApiRequestError('SESSION_RESULT_UNCONFIRMED', ''))}</p>}
  </div>;
}

function CreateForm({ memberId, created }: { memberId: number; created: (value: AdminSessionResponse) => void }) {
  const cache = useQueryClient();
  const [draft, setDraft] = useState(sessionDraft);
  const [validation, setValidation] = useState<FieldError[]>([]);
  const submitting = useRef(false);
  const mutation = useMutation({
    retry: false,
    mutationFn: async ({ input, identity }: { input: SessionCreateRequest; identity: string }) => {
      const response = await adminRequest(options => createSession(input, options), undefined, identity);
      if (response.status !== 201) throw responseError(response.data);
      return response.data;
    },
    onSuccess: (data, variables) => {
      if (variables.identity !== requestConnectionAuth().identity) return;
      cache.setQueryData(['adminSession', memberId, data.session.sessionId], data);
      created(data);
    },
    onSettled: async (_data, _error, variables) => {
      try { if (variables.identity === requestConnectionAuth().identity) await cache.invalidateQueries({ queryKey: ['sessions'] }); }
      finally { submitting.current = false; }
    },
  });
  const fields = sessionFieldErrors(validation.length ? validation : mutation.error instanceof ApiRequestError ? mutation.error.fields : []);
  function change(value: SessionDraft) { setDraft(value); setValidation([]); mutation.reset(); }
  function submit(event: FormEvent) {
    event.preventDefault(); if (submitting.current || mutation.isPending) return;
    const result = validateSessionDraft(draft, true); setValidation(result.fields);
    if (!result.input) return;
    submitting.current = true; mutation.mutate({ input: result.input, identity: requestConnectionAuth().identity });
  }
  return <Card><h2 className="text-xl font-bold">회차 생성</h2>
    <form aria-label="회차 생성" className="mt-5 space-y-4" noValidate onSubmit={submit}>
      <fieldset disabled={mutation.isPending} className="min-w-0"><legend className="sr-only">새 회차 설정</legend>
        <Fields prefix="create" draft={draft} change={change} fields={fields} creating />
      </fieldset>
      {mutation.isError && <Failure error={mutation.error} />}
      {mutation.isSuccess && <p role="status" className="text-sm text-success">회차 #{mutation.data.session.sessionId}를 생성했습니다.</p>}
      <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? '생성 중…' : '회차 생성하기'}</Button>
    </form>
  </Card>;
}

function SessionEditor({ value, memberId }: { value: AdminSessionResponse; memberId: number }) {
  const cache = useQueryClient();
  const [baseline, setBaseline] = useState(value);
  const [draft, setDraft] = useState(() => sessionDraft(value.session));
  const [validation, setValidation] = useState<FieldError[]>([]);
  const submitting = useRef(false);
  const id = value.session.sessionId;
  const editable = value.session.status === SessionStatus.SCHEDULED;
  const mutation = useMutation({
    retry: false,
    mutationFn: async ({ input, identity }: { input: SessionPatchRequest; identity: string }) => {
      const response = await adminRequest(options => patchSession(id, input, options), undefined, identity);
      if (response.status !== 200) throw responseError(response.data);
      return response.data;
    },
    onSuccess: (data, variables) => {
      if (variables.identity !== requestConnectionAuth().identity) return;
      setBaseline(data); setDraft(sessionDraft(data.session));
      cache.setQueryData(['adminSession', memberId, id], data);
    },
    onSettled: async (_data, _error, variables) => {
      try {
        if (variables.identity === requestConnectionAuth().identity) await Promise.all([
          cache.invalidateQueries({ queryKey: ['sessions'] }), cache.invalidateQueries({ queryKey: ['adminSession', memberId, id] }),
        ]);
      } finally { submitting.current = false; }
    },
  });
  const fields = sessionFieldErrors(validation.length ? validation : mutation.error instanceof ApiRequestError ? mutation.error.fields : []);
  function change(next: SessionDraft) { setDraft(next); setValidation([]); mutation.reset(); }
  function reloadDraft() { change(sessionDraft(value.session)); setBaseline(value); }
  function submit(event: FormEvent) {
    event.preventDefault(); if (!editable || submitting.current || mutation.isPending) return;
    const result = validateSessionDraft(draft, false); setValidation(result.fields); if (!result.input) return;
    const timing: SessionTimingRequest = {};
    for (const name of timingNames) {
      const next = result.input.timing?.[name];
      if (next !== undefined && next !== baseline.session.timing[name]) timing[name] = next;
    }
    const input: SessionPatchRequest = {
      ...(Date.parse(result.input.scheduledStartAt) !== Date.parse(baseline.session.scheduledStartAt) ? { scheduledStartAt: result.input.scheduledStartAt } : {}),
      ...(Object.keys(timing).length ? { timing } : {}),
    };
    submitting.current = true; mutation.mutate({ input, identity: requestConnectionAuth().identity });
  }
  return <Card><div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-bold">회차 #{id} 설정</h2><Badge>{stateLabels[value.session.status]}</Badge></div>
    <a href={`#/admin/sessions/${id}/lineup`} className="mt-4 inline-block text-sm font-semibold text-brand">회차 편성 열기</a>
    <p className="mt-3 text-sm text-muted">레인 {value.session.laneCount}개 · {value.emptyLaneNos.length ? '미편성 레인 있음' : value.startable ? '시작 조건 충족' : '모든 레인 편성됨'}</p>
    <div className="mt-3 flex flex-wrap gap-2">{value.lanes.map(lane => <Badge key={lane.laneId}>레인 {lane.laneNo} · 물품 {lane.lots.length}개</Badge>)}</div>
    {!editable && <p role="status" className="mt-4 text-sm text-muted">시작한 회차는 설정을 변경할 수 없습니다.</p>}
    <form aria-label="회차 설정 변경" className="mt-5 space-y-4" noValidate onSubmit={submit}>
      <fieldset disabled={!editable || mutation.isPending} className="min-w-0"><legend className="sr-only">선택한 회차 설정</legend>
        <Fields prefix="edit" draft={draft} change={change} fields={fields} creating={false} />
      </fieldset>
      {mutation.isError && <Failure error={mutation.error} />}
      {mutation.isSuccess && <p role="status" className="text-sm text-success">회차 설정을 저장했습니다.</p>}
      <div className="flex flex-wrap gap-3"><Button type="submit" disabled={!editable || mutation.isPending}>{mutation.isPending ? '저장 중…' : '설정 저장'}</Button>
        <Button variant="secondary" disabled={mutation.isPending} onClick={reloadDraft}>조회한 값으로 되돌리기</Button></div>
    </form>
  </Card>;
}

export function AdminSessionPage() {
  const auth = useAuth();
  const [status, setStatus] = useState<SessionStatus>(SessionStatus.SCHEDULED);
  const [selected, setSelected] = useState<number | null>(null);
  const list = useSessionList(status);
  const detail = useAdminSession(auth.member?.memberId, selected);
  const items = [...new Map(list.data?.pages.flatMap(page => page.items).map(item => [item.sessionId, item]) ?? []).values()];
  return <main className="mx-auto max-w-6xl px-6 py-10">
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-semibold text-brand">운영자</p><h1 className="mt-2 text-3xl font-bold">회차 관리</h1>
      <p className="mt-3 text-sm text-muted">회차를 만들고 시작 전 설정을 관리하세요.</p></div>
      <Button variant="secondary" disabled={list.isFetching} onClick={() => { void list.refetch(); if (selected !== null) void detail.refetch(); }}>목록 새로고침</Button></div>
    <div className="grid items-start gap-6 lg:grid-cols-2">
      <section aria-label="회차 목록" className="min-w-0 space-y-4"><Card><label htmlFor="session-status" className="text-sm">회차 상태</label>
        <select id="session-status" className={inputClass} value={status} onChange={event => { setStatus(event.target.value as SessionStatus); }}>
          {Object.values(SessionStatus).map(value => <option key={value} value={value}>{stateLabels[value]}</option>)}
        </select>
        <div className="mt-5">{list.isPending ? <Spinner label="회차를 불러오는 중" /> : list.isError ? <><Failure error={list.error} /><Button className="mt-3" onClick={() => { void list.refetch(); }}>목록 다시 조회</Button></> : items.length === 0 ?
          <EmptyState title="해당 상태의 회차가 없습니다" description="회차를 생성하거나 다른 상태를 선택해 주세요." /> :
          <ul className="space-y-3">{items.map(item => <li key={item.sessionId} className="rounded-lg border border-line p-4">
            <h2 className="font-semibold"><time dateTime={item.scheduledStartAt}>{dateLabel(item.scheduledStartAt)}</time> 회차</h2>
            <p className="mt-2 text-sm text-muted">레인 {item.laneCount}개 · 물품 {item.lotCount}개</p>
            <Button variant="secondary" className="mt-3" aria-pressed={selected === item.sessionId} onClick={() => { setSelected(item.sessionId); }}>설정 열기 #{item.sessionId}</Button>
          </li>)}</ul>}
          {list.hasNextPage && <Button variant="secondary" className="mt-4 w-full" disabled={list.isFetching} onClick={() => { void list.fetchNextPage(); }}>{list.isFetchingNextPage ? '불러오는 중…' : '더 보기'}</Button>}
        </div></Card>
        {selected !== null && (detail.isPending ? <Card><Spinner label="회차 설정을 불러오는 중" /></Card> : detail.isError ? <Card><Failure error={detail.error} /><Button className="mt-3" onClick={() => { void detail.refetch(); }}>설정 다시 조회</Button></Card> :
          <SessionEditor key={detail.data.session.sessionId} value={detail.data} memberId={auth.member!.memberId} />)}
      </section>
      <section aria-label="새 회차" className="min-w-0"><CreateForm memberId={auth.member!.memberId} created={data => { setStatus(SessionStatus.SCHEDULED); setSelected(data.session.sessionId); }} /></section>
    </div>
  </main>;
}
