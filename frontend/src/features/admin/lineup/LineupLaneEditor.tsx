import { useRef, useState } from 'react';
import type { AdminSessionLane, AdminSessionLot } from '../../../api/generated/models';
import { useSaveSessionLineup } from '../../../api/adminSessionQueries';
import { ApiRequestError, apiErrorMessage, lineupErrorDetails } from '../../../api/errorMessages';
import { requestConnectionAuth } from '../../auth/authStore';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { EmptyState } from '../../../components/ui/EmptyState';

export type LineupChoice = Pick<AdminSessionLot, 'itemId' | 'name' | 'imageUrl' | 'startPrice'>;
const fingerprint = (choices: LineupChoice[]) => choices.map(item => item.itemId).join(',');

export function LineupLaneEditor({ memberId, sessionId, lane, editable, candidates }: {
  memberId: number; sessionId: number; lane: AdminSessionLane; editable: boolean; candidates: LineupChoice[];
}) {
  const [baseline, setBaseline] = useState(lane);
  const [draft, setDraft] = useState<LineupChoice[]>(lane.lots);
  const [selected, setSelected] = useState('');
  const pending = useRef(false);
  const mutation = useSaveSessionLineup(memberId, sessionId, lane.laneNo);
  const dirty = fingerprint(draft) !== fingerprint(baseline.lots);
  const changed = fingerprint(lane.lots) !== fingerprint(baseline.lots);
  const options = [...new Map([...candidates, ...baseline.lots].map(item => [item.itemId, item])).values()]
    .filter(item => !draft.some(choice => choice.itemId === item.itemId));
  const chosen = options.find(item => item.itemId === Number(selected));
  const names = Object.fromEntries(draft.map(item => [item.itemId, item.name]));
  const details = lineupErrorDetails(mutation.error, names);
  function change(next: LineupChoice[]) { setDraft(next); mutation.reset(); }
  function move(index: number, direction: number) {
    const next = [...draft]; const target = index + direction;
    [next[index], next[target]] = [next[target]!, next[index]!]; change(next);
  }
  function reload() { setBaseline(lane); setDraft(lane.lots); setSelected(''); mutation.reset(); }
  function save() {
    if (!editable || pending.current || mutation.isPending || !dirty) return;
    pending.current = true;
    const identity = requestConnectionAuth().identity;
    mutation.mutate({ itemIds: draft.map(item => item.itemId), identity }, {
      onSuccess: response => {
        if (identity !== requestConnectionAuth().identity) return;
        setBaseline(response); setDraft(response.lots); setSelected('');
      },
      onSettled: () => { pending.current = false; },
    });
  }
  return <Card><section aria-label={`레인 ${lane.laneNo} 편성`} className="min-w-0">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-bold">레인 {lane.laneNo}</h2>
      <span className="text-sm text-muted">저장 {lane.lots.length}개{dirty ? ` · 편집 ${draft.length}개` : ''}</span></div>
    {changed && <p role="status" className="mt-3 rounded-lg bg-brand-soft p-3 text-sm text-brand">서버의 편성이 바뀌었습니다. 편집 내용은 유지됩니다. 새로 조회한 편성을 불러와 확인해 주세요.</p>}
    <fieldset disabled={!editable || mutation.isPending} className="mt-4 min-w-0 space-y-4"><legend className="sr-only">레인 {lane.laneNo} 편성 편집</legend>
      {draft.length === 0 ? <EmptyState title="아직 편성된 물품이 없습니다" description="아래에서 물품을 선택해 추가하세요." /> :
        <ol className="space-y-3" aria-label={`레인 ${lane.laneNo} 순서`}>{draft.map((item, index) => <li key={item.itemId} className="rounded-lg border border-line p-3">
          <p className="break-words font-semibold">{index + 1}. {item.name}</p>
          <p className="mt-1 text-sm text-muted">시작가 {item.startPrice.toLocaleString('ko-KR')}원</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button variant="secondary" aria-label={`${item.name} 위로`} disabled={index === 0} onClick={() => { move(index, -1); }}>↑</Button>
            <Button variant="secondary" aria-label={`${item.name} 아래로`} disabled={index === draft.length - 1} onClick={() => { move(index, 1); }}>↓</Button>
            <Button variant="secondary" aria-label={`${item.name} 편성에서 제거`} onClick={() => { change(draft.filter(choice => choice.itemId !== item.itemId)); }}>제거</Button>
          </div>
        </li>)}</ol>}
      <div><label htmlFor={`lane-${lane.laneNo}-candidate`} className="text-sm font-semibold">레인 {lane.laneNo} 편성 후보</label>
        <select id={`lane-${lane.laneNo}-candidate`} className="mt-2 w-full min-w-0 rounded-lg border border-line bg-surface px-3 py-2 text-sm" value={selected}
          onChange={event => { setSelected(event.target.value); }}>
          <option value="">물품을 선택하세요</option>{options.map(item => <option key={item.itemId} value={item.itemId}>{item.name} · {item.startPrice.toLocaleString('ko-KR')}원</option>)}
        </select>
        {options.length === 0 && <p className="mt-2 text-sm text-muted">추가할 수 있는 물품이 없습니다.</p>}
        <Button variant="secondary" className="mt-3" disabled={!chosen} onClick={() => { if (chosen) { change([...draft, chosen]); setSelected(''); } }}>선택한 물품 추가</Button>
      </div>
    </fieldset>
    {mutation.isError && <div role="alert" className="mt-4 rounded-lg bg-danger-soft p-3 text-sm text-danger">
      <p>{apiErrorMessage(mutation.error)}</p>{details.length > 0 && <ul className="mt-2 space-y-1">{details.map((detail, index) => <li key={index}>{detail}</li>)}</ul>}
      {(!(mutation.error instanceof ApiRequestError) || mutation.error.code === 'INTERNAL_ERROR') && <p className="mt-2">{apiErrorMessage(new ApiRequestError('SESSION_RESULT_UNCONFIRMED', ''))}</p>}
    </div>}
    {mutation.isSuccess && <p role="status" className="mt-4 text-sm text-success">레인 {lane.laneNo} 편성을 저장했습니다.</p>}
    <div className="mt-4 flex flex-wrap gap-3">
      <Button disabled={!editable || !dirty || mutation.isPending} onClick={save}>{mutation.isPending ? '저장 중…' : '편성 저장'}</Button>
      <Button variant="secondary" disabled={mutation.isPending} onClick={reload}>조회한 편성 불러오기</Button>
    </div>
    {dirty && <p className="mt-3 text-xs text-muted">물품 추가·제거·순서 변경은 저장한 뒤 반영됩니다. 모두 제거하고 저장하면 이 레인은 비워집니다.</p>}
  </section></Card>;
}
