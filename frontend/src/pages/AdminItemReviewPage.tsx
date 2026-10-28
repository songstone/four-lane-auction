import { useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ListAdminItemsStage } from '../api/generated/models';
import { approveItem, rejectItem } from '../api/generated/sessions';
import { adminRequest, useAdminItem, useAdminItems } from '../api/adminItemQueries';
import { apiErrorMessage, responseError, reviewFieldError, reviewReasonError } from '../api/errorMessages';
import { useAuth } from '../features/auth/useAuth';
import { requestConnectionAuth } from '../features/auth/authStore';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { Spinner } from '../components/ui/Spinner';

const stages: Record<ListAdminItemsStage, string> = {
  PENDING_REVIEW: '검수 대기', APPROVED: '승인됨 · 편성 대기', REJECTED: '반려',
  SCHEDULED: '편성됨', ON_AUCTION: '경매 중', SOLD: '낙찰', UNSOLD: '유찰',
};
const money = (price: number) => `${price.toLocaleString('ko-KR')}원`;
const date = (value: string) => new Date(value).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' });

function ReviewDetail({ itemId, memberId }: { itemId: number; memberId: number }) {
  const query = useAdminItem(memberId, itemId);
  const cache = useQueryClient();
  const [reason, setReason] = useState('');
  const [validation, setValidation] = useState<string | null>(null);
  const mutation = useMutation({
    retry: false,
    mutationFn: async ({ action, identity }: { action: 'approve' | 'reject'; identity: string }) => {
      const response = await adminRequest(options => action === 'approve' ? approveItem(itemId, options) : rejectItem(itemId, { reason }, options), undefined, identity);
      if (response.status !== 200) throw responseError(response.data);
      return response.data;
    },
    onSettled: async (_data, _error, variables) => {
      if (variables.identity !== requestConnectionAuth().identity) return;
      await Promise.all([
        cache.invalidateQueries({ queryKey: ['adminItems', memberId] }),
        cache.invalidateQueries({ queryKey: ['adminItem', memberId, itemId] }),
      ]);
    },
  });
  function submit(action: 'approve' | 'reject') {
    if (mutation.isPending || query.isFetching) return;
    const error = action === 'reject' ? reviewReasonError(reason) : null;
    setValidation(error);
    if (error) return;
    mutation.mutate({ action, identity: requestConnectionAuth().identity });
  }
  if (query.isPending) return <Card><Spinner label="출품 상세를 불러오는 중" /></Card>;
  if (query.isError) return <Card><p role="alert" className="text-danger">{apiErrorMessage(query.error)}</p>
    <Button className="mt-4" variant="secondary" onClick={() => { void query.refetch(); }}>상세 다시 조회</Button></Card>;
  const item = query.data;
  const fieldError = validation ?? reviewFieldError(mutation.error);
  const pending = item.reviewStatus === 'PENDING_REVIEW';
  return <Card>
    <div className="flex flex-wrap items-start justify-between gap-3"><div>
      <span className="text-sm font-semibold text-brand">{stages[item.stage]}</span>
      <h2 className="mt-2 break-words text-2xl font-bold">{item.name}</h2>
      <p className="mt-2 text-sm text-muted">판매자 {item.sellerNickname}</p>
    </div><Button variant="secondary" disabled={query.isFetching || mutation.isPending} onClick={() => { void query.refetch(); }}>상태 새로고침</Button></div>
    <div className="mt-6 grid grid-cols-2 gap-4 rounded-lg bg-canvas p-4">
      <div><p className="text-sm text-muted">시작가</p><p className="mt-1 break-words font-semibold">{money(item.startPrice)}</p></div>
      <div><p className="text-sm text-muted">최저 낙찰가</p><p className="mt-1 break-words font-semibold">{money(item.reservePrice)}</p></div>
    </div>
    <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-relaxed">{item.description || '등록된 설명이 없습니다.'}</p>
    <div className="mt-6 space-y-4">{item.images.map(image => <figure key={image.sortOrder}>
      <img src={image.url} alt={`${item.name} 이미지 ${image.sortOrder + 1}`} className="w-full rounded-lg border border-line bg-canvas object-contain" />
      <figcaption className="mt-2 text-xs text-muted">{image.sortOrder === 0 ? '대표 이미지' : `${image.sortOrder + 1}번째 이미지`}</figcaption>
    </figure>)}</div>
    <p className="mt-4 text-xs text-muted">등록 <time dateTime={item.createdAt}>{date(item.createdAt)}</time></p>
    {item.reviewedAt && <p className="mt-2 text-xs text-muted">검수 <time dateTime={item.reviewedAt}>{date(item.reviewedAt)}</time></p>}
    {item.rejectReason && <p className="mt-4 whitespace-pre-wrap break-words rounded-lg bg-danger-soft p-4 text-sm text-danger">반려 사유: {item.rejectReason}</p>}
    {mutation.isError && <div role="alert" className="mt-4 rounded-lg bg-danger-soft p-4 text-sm text-danger">
      <p>{apiErrorMessage(mutation.error)}</p><p className="mt-2">결과가 불확실하면 상태 새로고침으로 확인해 주세요.</p>
    </div>}
    {mutation.isSuccess && <p role="status" className="mt-4 rounded-lg bg-success-soft p-4 text-sm text-success">{mutation.data.reviewStatus === 'APPROVED' ? '승인했습니다. 편성 대기 목록에서 확인할 수 있습니다.' : '반려했습니다. 판매자는 내 출품 목록에서 사유를 확인할 수 있습니다.'}</p>}
    {pending && <div className="mt-6 space-y-4 border-t border-line pt-6">
      <label className="block text-sm font-semibold" htmlFor="reject-reason">반려 사유</label>
      <textarea id="reject-reason" rows={3} value={reason} disabled={mutation.isPending}
        aria-invalid={!!fieldError} aria-describedby="reason-help reason-error"
        onChange={event => { setReason(event.target.value); setValidation(null); mutation.reset(); }}
        className="w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm focus-visible:outline-brand" />
      <p id="reason-help" className="text-xs text-muted">반려할 때 필수 · {Array.from(reason).length} / 500자</p>
      {fieldError && <p id="reason-error" role="alert" className="text-sm text-danger">{fieldError}</p>}
      <div className="flex flex-wrap gap-3"><Button disabled={mutation.isPending || query.isFetching} onClick={() => { submit('approve'); }}>승인</Button>
        <Button variant="secondary" disabled={mutation.isPending || query.isFetching} onClick={() => { submit('reject'); }}>사유와 함께 반려</Button>
        {mutation.isPending && <Spinner label="검수 결과를 저장하는 중" />}</div>
    </div>}
    {item.lots.length > 0 && <div className="mt-6 border-t border-line pt-6"><h3 className="font-semibold">경매 이력</h3>
      <ul className="mt-3 space-y-3 text-sm">{item.lots.map(lot => <li key={lot.lotId} className="rounded-lg bg-canvas p-3">
        회차 {lot.sessionId} · {lot.laneNo}레인 · {lot.seqNo}번 · {lot.status}{lot.finalPrice !== null ? ` · ${money(lot.finalPrice)}` : ''}
      </li>)}</ul></div>}
  </Card>;
}

export function AdminItemReviewPage() {
  const auth = useAuth();
  const [stage, setStage] = useState<ListAdminItemsStage>('PENDING_REVIEW');
  const [selected, setSelected] = useState<number | null>(null);
  const detailRegion = useRef<HTMLElement>(null);
  const query = useAdminItems(auth.member?.memberId, stage);
  const items = [...new Map((query.data?.pages.flatMap(page => page.items) ?? []).map(item => [item.itemId, item])).values()];
  return <main className="mx-auto max-w-6xl px-6 py-10">
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-semibold text-brand">운영자</p>
      <h1 className="mt-2 text-3xl font-bold">출품 검수</h1><p className="mt-3 text-sm text-muted">이미지와 출품 정보를 확인하고 승인하거나 사유와 함께 반려하세요.</p></div>
      <Button variant="secondary" disabled={query.isFetching} onClick={() => { void query.refetch(); }}>목록 새로고침</Button></div>
    <div className="grid items-start gap-6 lg:grid-cols-2"><section aria-label="출품 목록">
      <label htmlFor="review-stage" className="text-sm font-semibold">진행 단계</label>
      <select id="review-stage" value={stage} onChange={event => { setStage(event.target.value as ListAdminItemsStage); setSelected(null); }} className="mb-4 mt-2 block w-full rounded-lg border border-line bg-surface px-3 py-2">
        {Object.entries(stages).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
      {query.isPending ? <Card><Spinner label="출품 목록을 불러오는 중" /></Card> : query.isError ? <Card>
        <p role="alert" className="text-danger">{apiErrorMessage(query.error)}</p><Button variant="secondary" className="mt-4" onClick={() => { void query.refetch(); }}>목록 다시 조회</Button>
      </Card> : items.length === 0 ? <Card><EmptyState title="해당 단계의 출품물이 없습니다" description="다른 단계를 선택하거나 목록을 새로고침해 주세요." /></Card> : <>
        <ul className="max-h-screen space-y-3 overflow-y-auto pr-2">{items.map(item => <li key={item.itemId}><button type="button" aria-pressed={selected === item.itemId}
          onClick={() => { setSelected(item.itemId); requestAnimationFrame(() => {
            const element = detailRegion.current;
            if (element && element.getBoundingClientRect().top > window.innerHeight / 2) element.scrollIntoView({ block: 'start', behavior: 'smooth' });
          }); }} className={`flex w-full gap-4 rounded-xl border bg-surface p-4 text-left focus-visible:outline-brand ${selected === item.itemId ? 'border-brand' : 'border-line'}`}>
          <img src={item.imageUrl} alt="" loading="lazy" className="h-20 w-20 shrink-0 rounded-lg bg-canvas object-cover" />
          <div className="min-w-0"><p className="text-xs font-semibold text-brand">{stages[item.stage]}</p><h2 className="mt-1 break-words font-semibold">{item.name}</h2>
            <p className="mt-2 text-sm">시작가 {money(item.startPrice)}</p><p className="mt-1 break-words text-xs text-muted">판매자 {item.sellerNickname} · {date(item.createdAt)}</p></div>
        </button></li>)}</ul>
        {query.hasNextPage && <Button variant="secondary" className="mt-4" disabled={query.isFetchingNextPage} onClick={() => { void query.fetchNextPage(); }}>{query.isFetchingNextPage ? '불러오는 중' : '더 보기'}</Button>}
      </>}
    </section><section ref={detailRegion} aria-label="검수 상세" className="scroll-mt-4">
      {selected !== null && auth.member ? <ReviewDetail key={`${auth.member.memberId}:${selected}`} itemId={selected} memberId={auth.member.memberId} /> :
        <Card><EmptyState title="출품물을 선택해 주세요" description="목록에서 물품을 선택하면 이미지와 상세 정보를 확인할 수 있습니다." /></Card>}
    </section></div>
  </main>;
}
