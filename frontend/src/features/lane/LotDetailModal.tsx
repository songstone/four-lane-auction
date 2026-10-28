import { usePublicLot } from '../../api/publicSessionQueries';
import { apiErrorMessage } from '../../api/errorMessages';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Spinner } from '../../components/ui/Spinner';

function LotDetail({ lotId }: { lotId: number }) {
  const query = usePublicLot(lotId);
  return <>
    {query.isPending && <Spinner label="물품 상세를 불러오는 중" />}
    {query.isError && <div role="alert" className="text-danger">
      <p>{apiErrorMessage(query.error)}</p>
      <Button className="mt-3" variant="secondary" disabled={query.isFetching}
        onClick={() => { void query.refetch(); }}>다시 조회</Button>
    </div>}
    {query.data && !query.isError && <div className="max-h-96 space-y-4 overflow-y-auto break-words">
      <h3 className="font-semibold">{query.data.name}</h3>
      <p className="text-sm text-muted">레인 {query.data.laneNo} · {query.data.seqNo}번째</p>
      <div className="grid grid-cols-2 gap-3" aria-label="물품 이미지">
        {query.data.images.map((image, index) => <img key={image.url} src={image.url}
          alt={`${query.data.name} 이미지 ${index + 1}`} className="aspect-square w-full rounded-lg border border-line object-contain" />)}
      </div>
      <p className="whitespace-pre-wrap">{query.data.description || '등록된 설명이 없습니다.'}</p>
      <p className="font-semibold">시작가 {query.data.startPrice.toLocaleString('ko-KR')}원</p>
      {query.data.finalPrice !== null && <p>최종 가격 {query.data.finalPrice.toLocaleString('ko-KR')}원</p>}
    </div>}
  </>;
}

export function LotDetailModal({ lotId, available, onClose }: { lotId: number; available: boolean; onClose: () => void }) {
  return <Modal open title="물품 상세" onClose={onClose}>
    {available ? <LotDetail lotId={lotId} /> : <div role="status" className="space-y-3">
      <p>편성이 변경되어 이 물품을 더 이상 볼 수 없습니다.</p>
      <p className="text-sm text-muted">닫은 뒤 최신 편성에서 물품을 다시 선택해 주세요.</p>
    </div>}
  </Modal>;
}
