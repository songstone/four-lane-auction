import { useState } from 'react';
import type { PublicSessionLane } from '../api/generated/models';
import { apiErrorMessage } from '../api/errorMessages';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { Spinner } from '../components/ui/Spinner';
import { useAuth } from '../features/auth/useAuth';
import { requestConnectionAuth } from '../features/auth/authStore';
import { ScheduledCountdown } from '../features/lane/ScheduledCountdown';
import { LotDetailModal } from '../features/lane/LotDetailModal';
import { useLaneSnapshot } from '../realtime/laneSnapshots';
import { useScheduledSession } from '../realtime/useScheduledSession';

function ScheduledLane({ lane, myLotIds, onSelect }: {
  lane: PublicSessionLane; myLotIds: number[]; onSelect: (id: number) => void;
}) {
  const snapshot = useLaneSnapshot(lane.laneId);
  return <Card className="min-w-0" data-testid={`lane-${lane.laneNo}`}>
    <div className="flex items-center justify-between gap-3">
      <h2 className="text-lg font-semibold">레인 {lane.laneNo}</h2>
      <Badge>{snapshot.data ? '시작 전' : '편성 미리보기'} · {lane.lineup.length}개</Badge>
    </div>
    {lane.lineup.length === 0 ? <EmptyState title="편성된 물품이 없습니다" description="운영자가 편성하면 이곳에 표시됩니다." /> :
      <ol className="mt-5 space-y-3" aria-label={`레인 ${lane.laneNo} 편성`}>
        {lane.lineup.map(lot => <li key={lot.lotId}>
          <button type="button" onClick={() => onSelect(lot.lotId)}
            className="flex w-full items-center gap-3 rounded-lg border border-line p-3 text-left transition hover:bg-canvas focus-visible:outline-2 focus-visible:outline-brand">
            <img src={lot.imageUrl} alt="" className="size-16 shrink-0 rounded-lg border border-line object-cover" />
            <span className="min-w-0">
              <span className="text-sm text-muted">{lot.seqNo}번째{myLotIds.includes(lot.lotId) ? ' · 내 물품' : ''}</span>
              <span className="block break-words font-semibold">{lot.name}</span>
              <span className="mt-1 block text-sm">시작가 {lot.startPrice.toLocaleString('ko-KR')}원</span>
            </span>
          </button>
        </li>)}
      </ol>}
  </Card>;
}

function ScheduledSessionContent({ sessionId, identity }: { sessionId: number; identity: string }) {
  const { query, connection, refresh } = useScheduledSession(sessionId, identity);
  const [selectedLotId, setSelectedLotId] = useState<number | null>(null);
  const data = query.data;
  const myLotIds = data?.me?.myLotIds ?? [];
  return <main className="mx-auto max-w-6xl px-6 py-8">
    <a href="#/" className="text-sm font-semibold text-brand hover:underline">← 회차 목록</a>
    <div className="mt-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        <p className="text-sm font-semibold text-brand">예정 회차</p>
        <h1 className="mt-2 text-2xl font-bold">회차 #{sessionId} 관전</h1>
        {data && <>
          <p className="mt-3 text-muted"><time dateTime={data.session.scheduledStartAt}>
            {new Date(data.session.scheduledStartAt).toLocaleString('ko-KR', { hour12: false })}
          </time> 시작 예정</p>
          <ScheduledCountdown scheduledStartAt={data.session.scheduledStartAt} />
        </>}
      </div>
      <Badge data-testid="viewer-connection" tone={connection.status === 'connected' ? 'success' : 'neutral'}>
        {connection.status === 'connected' ? '관전 연결됨' : '관전 연결 중'}
      </Badge>
    </div>
    {connection.status !== 'connected' && <p role="status" className="mt-4 rounded-lg bg-brand-soft p-4 text-sm text-brand">
      {connection.error ? '관전 연결을 확인하지 못했습니다. 새로고침해 주세요.' : '관전 연결을 확인하고 있습니다. 편성은 조회된 시점의 정보입니다.'}
    </p>}
    {query.isPending && <div className="mt-8"><Spinner label="회차를 불러오는 중" /></div>}
    {query.isError && <div role="alert" className="mt-6 rounded-lg bg-danger-soft p-4 text-danger">
      <p>{apiErrorMessage(query.error)}</p>
      {data && <p className="mt-2 text-sm">마지막으로 조회한 편성입니다. 최신 변경을 확인하지 못했습니다.</p>}
      <Button className="mt-3" variant="secondary" disabled={query.isFetching}
        onClick={() => { void refresh(); }}>다시 조회</Button>
    </div>}
    {data && <>
      <div className="mt-6 grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        {data.lanes.map(lane => <ScheduledLane key={lane.laneId} lane={lane} myLotIds={myLotIds} onSelect={setSelectedLotId} />)}
      </div>
      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 text-sm text-muted">
        <p>편성은 60초마다 새로 확인합니다. 물품을 선택하면 상세를 볼 수 있습니다.</p>
        <Button variant="secondary" disabled={query.isFetching} onClick={() => { void refresh(); }}>
          {query.isFetching ? '편성 확인 중' : '편성 새로고침'}
        </Button>
      </div>
      <p className="mt-2 break-all text-xs text-muted">마지막 수신 · UTC · <time dateTime={data.serverTime}>{data.serverTime}</time></p>
    </>}
    {selectedLotId !== null && <LotDetailModal lotId={selectedLotId}
      available={data?.lanes.some(lane => lane.lineup.some(lot => lot.lotId === selectedLotId)) ?? false}
      onClose={() => setSelectedLotId(null)} />}
  </main>;
}

export function ScheduledSessionPage({ sessionId }: { sessionId: number }) {
  const { status } = useAuth();
  if (status === 'loading') return <main className="p-6"><Spinner label="로그인 상태를 확인하는 중" /></main>;
  const identity = requestConnectionAuth().identity;
  return <ScheduledSessionContent key={`${sessionId}:${identity}`} sessionId={sessionId} identity={identity} />;
}
