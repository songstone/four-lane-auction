import { useServerClock } from '../features/time/useServerClock';
import { ApiRequestError, apiErrorMessage } from '../api/errorMessages';
import { SessionStatus } from '../api/generated/models';
import type { SessionSummary } from '../api/generated/models';
import { useSessionList } from '../api/sessionQueries';
import type { SessionListQuery } from '../api/sessionQueries';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { Spinner } from '../components/ui/Spinner';
import { useConnectionStatus } from '../realtime/useConnectionStatus';
import { useAuth } from '../features/auth/useAuth';

const connectionLabels = {
  connecting: '연결 중',
  connected: '연결됨',
  disconnected: '연결 끊김',
  reconnecting: '재연결 중',
};

const sessionTime = new Intl.DateTimeFormat('ko-KR', {
  month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
});

function SessionEntry({ session, now }: { session: SessionSummary; now: number | null }) {
  return (
    <li className="rounded-lg border border-line p-4">
      <h3 className="font-semibold">
        <time dateTime={session.scheduledStartAt}>{sessionTime.format(new Date(session.scheduledStartAt))}</time> 회차
      </h3>
      {session.status === SessionStatus.SCHEDULED && <p className="mt-2 text-sm font-medium text-brand" data-testid={`session-countdown-${session.sessionId}`}>
        {now === null ? '시작 시각 확인 중' : Date.parse(session.scheduledStartAt) <= now ? '시작 대기 중' : `시작까지 ${Math.ceil((Date.parse(session.scheduledStartAt) - now) / 1000).toLocaleString('ko-KR')}초`}
      </p>}
      <p className="mt-2 text-sm text-muted">레인 {session.laneCount}개 · 편성 물품 {session.lotCount}개</p>
      {session.status === SessionStatus.SCHEDULED && <a href={`#/sessions/${session.sessionId}`}
        className="mt-3 inline-block text-sm font-semibold text-brand hover:underline">예정 회차 관전</a>}
    </li>
  );
}

function SessionSection({ id, title, empty, query, now }: {
  now: number | null;
  id: string;
  title: string;
  empty: string;
  query: SessionListQuery;
}) {
  const items = [...new Map(query.data?.pages.flatMap((page) => page.items)
    .map((session) => [session.sessionId, session]) ?? []).values()];
  return (
    <section aria-labelledby={id} className="min-w-0">
      <Card className="h-full">
        <div className="flex items-center justify-between gap-3">
          <h2 id={id} className="text-lg font-semibold">{title}</h2>
          <Badge>{query.data ? `${items.length}${query.hasNextPage ? '+' : ''}개` : '—'}</Badge>
        </div>
        <div className="mt-6">
          {query.isPending && <Spinner label="회차를 불러오는 중" />}
          {query.isError && (
            <div role="alert" className="rounded-lg bg-danger-soft p-4 text-sm text-danger">
              <p>{apiErrorMessage(query.error)}</p>
              <Button className="mt-3" variant="secondary" disabled={query.isFetching}
                onClick={() => { void query.refetch(); }}>다시 조회</Button>
            </div>
          )}
          {query.isSuccess && items.length === 0 && (
            <EmptyState title={empty} description="회차 정보가 등록되면 이곳에 표시됩니다." />
          )}
          {items.length > 0 && (
            <ul className="space-y-3" aria-label={`${title} 회차 목록`}>
              {items.map((session) => <SessionEntry key={session.sessionId} session={session} now={now} />)}
            </ul>
          )}
          {query.hasNextPage && (
            <Button className="mt-4 w-full" variant="secondary" disabled={query.isFetching}
              onClick={() => { void query.fetchNextPage(); }}>
              {query.isFetchingNextPage ? '불러오는 중' : '더 보기'}
            </Button>
          )}
        </div>
      </Card>
    </section>
  );
}

export function HomePage() {
  const now = useServerClock();
  const running = useSessionList(SessionStatus.RUNNING);
  const scheduled = useSessionList(SessionStatus.SCHEDULED);
  const finished = useSessionList(SessionStatus.FINISHED);
  const queries = [running, scheduled, finished];
  const restFailed = queries.some((query) => query.isError);
  const restConnected = !restFailed && queries.every((query) => query.data !== undefined);
  const ws = useConnectionStatus();
  const { member } = useAuth();
  const keepsConnection = member?.role === 'USER';
  const serverTime = queries.flatMap((query) => query.data?.pages.map((page) => page.serverTime) ?? [])
    .sort().at(-1);
  return (
    <div className="min-h-screen">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-5">
          <div className="flex items-center gap-3">
            <span className="flex size-10 items-center justify-center rounded-lg bg-ink text-lg font-bold text-surface"
              aria-hidden="true">4</span>
            <span className="text-lg font-semibold tracking-tight">Four-Lane Auction</span>
          </div>
          <div className="flex flex-wrap gap-2" aria-label="서버 연결 상태" aria-live="polite">
            <Badge data-testid="rest-status" tone={restFailed ? 'danger' : restConnected ? 'success' : 'neutral'}>
              REST {restFailed ? '연결 끊김' : restConnected ? '연결됨' : '연결 중'}
            </Badge>
            <Badge data-testid="ws-status" title={!keepsConnection ? '예정 회차 관전에 들어가면 연결됩니다.' : ws.error
              ? apiErrorMessage(new ApiRequestError(ws.error, '연결 요청이 거절되었습니다.')) : 'STOMP CONNECTED 수신 기준'}
              tone={!keepsConnection ? 'neutral' : ws.status === 'connected' ? 'success' : ws.status === 'connecting' ? 'neutral' : 'danger'}>
              {keepsConnection ? <>WebSocket {ws.status === 'connected'
                ? ws.identity === 'authenticated' ? '인증 연결' : '익명 연결' : connectionLabels[ws.status]}
              </> : '관전 화면에서 연결'}
            </Badge>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-12">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-semibold text-brand">FOUR LANES, ONE AUCTION</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight">실시간 경매</h1>
            <p className="mt-3 text-muted">진행 중인 경매부터 다음 회차까지 한눈에 확인하세요.</p>
          </div>
          <div className="min-w-0 text-sm text-muted">
            <p>서버 시각 · UTC · 마지막 수신</p>
            {serverTime ? <time className="mt-1 block break-all font-medium text-ink"
              dateTime={serverTime} data-testid="server-time">{serverTime}</time> : <p className="mt-1">응답을 기다리는 중</p>}
          </div>
        </div>
        <div className="mt-8 grid gap-6 lg:grid-cols-3">
          <SessionSection id="running" title="진행 중" empty="진행 중인 회차가 없습니다" query={running} now={now} />
          <SessionSection id="scheduled" title="예정" empty="예정된 회차가 없습니다" query={scheduled} now={now} />
          <SessionSection id="finished" title="종료" empty="종료된 회차가 없습니다" query={finished} now={now} />
        </div>
        <p className="mt-6 text-sm text-muted">회차 목록은 30초마다 자동으로 갱신됩니다.</p>
      </main>
    </div>
  );
}
