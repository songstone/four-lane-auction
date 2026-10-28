import { useEffect, useState } from 'react';
import type { MyItemSummary, MyItemSummaryStage } from '../api/generated/models';
import { useMyItems } from '../api/itemQueries';
import { apiErrorMessage } from '../api/errorMessages';
import { useAuth } from '../features/auth/useAuth';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { Spinner } from '../components/ui/Spinner';

const stages: Record<MyItemSummaryStage, string> = {
  PENDING_REVIEW: '검수 대기', REJECTED: '반려', APPROVED: '승인됨 · 편성 대기',
  SCHEDULED: '편성됨', ON_AUCTION: '경매 중', SOLD: '낙찰', UNSOLD: '유찰',
};
function ItemThumbnail({ item }: { item: MyItemSummary }) {
  const [broken, setBroken] = useState(false);
  return broken ? <div className="flex h-24 w-24 shrink-0 items-center justify-center rounded-lg bg-canvas p-2 text-center text-xs text-muted">이미지를 불러올 수 없습니다</div> :
    <img src={item.imageUrl} alt={item.name} loading="lazy" className="h-24 w-24 shrink-0 rounded-lg bg-canvas object-cover" onError={() => { setBroken(true); }} />;
}

export function MyItemsPage() {
  useEffect(() => { window.scrollTo(0, 0); }, []);
  const auth = useAuth();
  const query = useMyItems(auth.member?.memberId);
  const items = query.data?.pages.flatMap(page => page.items) ?? [];
  return <main className="mx-auto max-w-3xl px-6 py-10">
    <div className="mb-6 flex flex-wrap items-center justify-between gap-4"><div>
      <p className="text-sm font-semibold text-brand">판매자 출품</p><h1 className="mt-2 text-3xl font-bold">내 출품 목록</h1>
      <p className="mt-3 text-sm text-muted">등록한 물품의 검수와 진행 단계를 확인하세요.</p>
    </div><a href="#/items/new" className="rounded-lg bg-brand px-4 py-2 text-sm font-semibold text-surface hover:bg-brand-hover">출품 등록</a></div>
    {query.isPending ? <Card><Spinner label="내 출품을 불러오는 중" /></Card> : query.isError ? <Card>
      <p role="alert" className="text-danger">{apiErrorMessage(query.error)}</p><Button variant="secondary" className="mt-4" onClick={() => { void query.refetch(); }}>다시 조회</Button>
    </Card> : items.length === 0 ? <Card><EmptyState title="등록한 출품물이 없습니다" description="첫 물품을 등록하고 경매를 준비해 보세요."
      action={<a href="#/items/new" className="font-semibold text-brand">첫 출품 등록하기</a>} /></Card> : <>
      <ul className="space-y-4">{items.map(item => <li key={item.itemId}><Card className="flex gap-4">
        <ItemThumbnail item={item} /><div className="min-w-0 flex-1">
          <span className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${item.stage === 'REJECTED' ? 'bg-danger-soft text-danger' : item.stage === 'APPROVED' ? 'bg-success-soft text-success' : 'bg-brand-soft text-brand'}`}>{stages[item.stage]}</span>
          <h2 className="mt-2 break-words text-lg font-semibold">{item.name}</h2>
          <p className="mt-2 text-sm">시작가 <span className="font-semibold">{item.startPrice.toLocaleString('ko-KR')}원</span></p>
          {item.stage === 'REJECTED' && item.rejectReason && <p className="mt-2 break-words text-sm text-danger">반려 사유: {item.rejectReason}</p>}
          <p className="mt-2 text-xs text-muted">등록 <time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}</time></p>
        </div>
      </Card></li>)}</ul>
      {query.hasNextPage && <div className="mt-6 text-center"><Button variant="secondary" disabled={query.isFetchingNextPage} onClick={() => { void query.fetchNextPage(); }}>{query.isFetchingNextPage ? '불러오는 중' : '더 보기'}</Button></div>}
    </>}
  </main>;
}
