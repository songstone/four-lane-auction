import { useServerClock } from '../time/useServerClock';

export function ScheduledCountdown({ scheduledStartAt }: { scheduledStartAt: string }) {
  const now = useServerClock();
  const remaining = now === null ? null : Date.parse(scheduledStartAt) - now;
  return <p className="mt-3 text-xl font-semibold text-brand" data-testid="scheduled-countdown">
    {remaining === null ? '시작 시각 확인 중' : remaining <= -10_000 ? '운영자 시작 대기 중' :
      remaining <= 0 ? '곧 시작합니다' : `시작까지 ${Math.ceil(remaining / 1000).toLocaleString('ko-KR')}초`}
  </p>;
}
