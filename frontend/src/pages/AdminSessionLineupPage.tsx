import { useAdminSession } from '../api/adminSessionQueries';
import { useAdminItems } from '../api/adminItemQueries';
import { apiErrorMessage } from '../api/errorMessages';
import { useAuth } from '../features/auth/useAuth';
import { LineupLaneEditor } from '../features/admin/lineup/LineupLaneEditor';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Spinner } from '../components/ui/Spinner';

export function AdminSessionLineupPage({ sessionId }: { sessionId: number }) {
  const auth = useAuth();
  const detail = useAdminSession(auth.member?.memberId, sessionId);
  const candidates = useAdminItems(auth.member?.memberId, 'APPROVED');
  const choices = [...new Map(candidates.data?.pages.flatMap(page => page.items).map(item => [item.itemId, item]) ?? []).values()];
  return <main className="mx-auto max-w-6xl px-6 py-10">
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4"><div><a href="#/admin/sessions" className="text-sm font-semibold text-brand">회차 관리로</a>
      <h1 className="mt-3 text-3xl font-bold">회차 #{sessionId} 편성</h1>
      <p className="mt-3 text-sm text-muted">레인마다 물품을 선택하고 순서를 정한 뒤 저장하세요.</p></div>
      <Button variant="secondary" disabled={detail.isFetching || candidates.isFetching} onClick={() => { void detail.refetch(); void candidates.refetch(); }}>새로 조회</Button>
    </div>
    {detail.isPending ? <Card><Spinner label="회차 편성을 불러오는 중" /></Card> : !detail.data ? <Card>
      <p role="alert" className="text-danger">{apiErrorMessage(detail.error)}</p><Button className="mt-4" onClick={() => { void detail.refetch(); }}>회차 다시 조회</Button>
    </Card> : <>
      {detail.isError && <Card><p role="alert" className="text-danger">{apiErrorMessage(detail.error)} 편집 내용은 유지됩니다.</p>
        <Button className="mt-3" variant="secondary" onClick={() => { void detail.refetch(); }}>회차 다시 조회</Button></Card>}
      <Card><div className="flex flex-wrap items-center gap-3"><Badge>{detail.data.session.status === 'SCHEDULED' ? '예정 회차' : '편성 잠김'}</Badge>
        <p className={`font-semibold ${detail.data.startable ? 'text-success' : 'text-muted'}`}>{detail.data.startable ? '시작 가능' : '시작 불가'}</p></div>
        <p className="mt-3 text-sm text-muted">저장된 편성 기준 · {new Date(detail.data.session.scheduledStartAt).toLocaleString('ko-KR', { hour12: false })}</p>
        {detail.data.emptyLaneNos.length > 0 && <p className="mt-2 text-sm">비어 있는 레인: {detail.data.emptyLaneNos.join(', ')}</p>}
        {detail.data.session.status !== 'SCHEDULED' && <p role="status" className="mt-3 text-sm text-muted">시작한 회차의 편성은 변경할 수 없습니다.</p>}
      </Card>
      <div className="my-5 rounded-lg border border-line bg-surface p-4">
        {candidates.isPending ? <Spinner label="편성 후보를 불러오는 중" /> : candidates.isError ? <div role="alert" className="text-sm text-danger">
          <p>{apiErrorMessage(candidates.error)}</p><Button variant="secondary" className="mt-3" onClick={() => { void candidates.refetch(); }}>후보 다시 조회</Button>
        </div> : <><p className="text-sm text-muted">승인된 편성 후보 {choices.length}개를 불러왔습니다. 다른 레인으로 옮길 때는 원래 레인에서 제거하고 저장한 뒤 추가하세요.</p>
          {candidates.hasNextPage && <Button variant="secondary" className="mt-3" disabled={candidates.isFetchingNextPage} onClick={() => { void candidates.fetchNextPage(); }}>{candidates.isFetchingNextPage ? '불러오는 중…' : '편성 후보 더 보기'}</Button>}</>}
      </div>
      <div className="grid items-start gap-6 lg:grid-cols-2">{detail.data.lanes.map(lane => <LineupLaneEditor key={lane.laneId}
        memberId={auth.member!.memberId} sessionId={sessionId} lane={lane} editable={detail.data.session.status === 'SCHEDULED'} candidates={choices} />)}</div>
    </>}
  </main>;
}
