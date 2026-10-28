import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { registerItem } from '../api/generated/sessions';
import type { ItemRegistrationRequest } from '../api/generated/models';
import { authenticatedRequest } from '../api/authenticatedRequest';
import { ApiRequestError, apiErrorMessage, itemFieldErrors, responseError } from '../api/errorMessages';
import { useAuth } from '../features/auth/useAuth';
import { requestConnectionAuth } from '../features/auth/authStore';
import { navigate } from '../features/auth/navigation';
import { validateItemForm } from '../features/item/validation';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';

interface SelectedImage { id: string; file: File; preview: string }
const inputClass = 'mt-2 w-full rounded-lg border border-line bg-surface px-3 py-2 text-ink focus-visible:outline-brand';

export function ItemRegistrationPage() {
  const auth = useAuth();
  const queries = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [startPrice, setStartPrice] = useState('');
  const [reservePrice, setReservePrice] = useState('');
  const [images, setImages] = useState<SelectedImage[]>([]);
  const imageRefs = useRef<SelectedImage[]>([]);
  const [validation, setValidation] = useState<Record<string, string>>({});
  const [validating, setValidating] = useState(false);
  useEffect(() => { window.scrollTo(0, 0); }, []);
  useEffect(() => () => { imageRefs.current.forEach(image => { URL.revokeObjectURL(image.preview); }); }, []);
  const registration = useMutation({ retry: false,
    mutationFn: async ({ body, identity }: { body: ItemRegistrationRequest; identity: string }) => {
      const response = await authenticatedRequest(options => {
        if (identity !== requestConnectionAuth().identity) throw new ApiRequestError('AUTH_SESSION_CHANGED', '');
        return registerItem(body, options);
      });
      if (response.status !== 201) throw responseError(response.data);
      return response.data;
    },
  });
  const fields = { ...itemFieldErrors(registration.error instanceof ApiRequestError ? registration.error.fields : []), ...validation };
  const busy = validating || registration.isPending;
  function edit(field: string) {
    registration.reset();
    setValidation(previous => { const next = { ...previous }; delete next[field]; return next; });
  }
  function updateImages(next: SelectedImage[]) { imageRefs.current = next; setImages(next); edit('images'); }
  function removeImage(id: string) {
    const image = images.find(image => image.id === id);
    if (image) URL.revokeObjectURL(image.preview);
    updateImages(images.filter(image => image.id !== id));
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy || !auth.member) return;
    const identity = requestConnectionAuth().identity;
    const memberId = auth.member.memberId;
    setValidating(true);
    try {
      const form = { name, description, startPrice, reservePrice, images: images.map(image => image.file) };
      const errors = await validateItemForm(form);
      if (identity !== requestConnectionAuth().identity) return;
      setValidation(itemFieldErrors(errors));
      if (errors.length) return;
      await registration.mutateAsync({ identity, body: { name, description, startPrice,
        ...(reservePrice.trim() ? { reservePrice } : {}), images: form.images } });
      if (identity !== requestConnectionAuth().identity) return;
      await queries.invalidateQueries({ queryKey: ['myItems', memberId] });
      if (identity === requestConnectionAuth().identity) navigate('/me/items');
    } catch { /* Display mutation errors; an unconfirmed POST is never retried automatically. */ }
    finally { setValidating(false); }
  }
  return <main className="mx-auto max-w-3xl px-6 py-10">
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div><p className="text-sm font-semibold text-brand">판매자 출품</p><h1 className="mt-2 text-3xl font-bold">출품 등록</h1></div>
      <a href="#/me/items" className="text-sm text-brand">내 출품 목록</a>
    </div>
    <Card><p className="text-sm leading-relaxed text-muted">물품 정보를 등록하면 검수가 시작됩니다. 등록 후 진행 단계는 내 출품 목록에서 확인할 수 있습니다.</p>
      <form noValidate onSubmit={event => { void submit(event); }} className="mt-6 space-y-6">
        <fieldset disabled={busy} className="space-y-6">
          <div><label htmlFor="item-images" className="font-semibold">이미지</label>
            <p id="images-help" className="mt-2 text-sm text-muted">JPEG · PNG · WebP, 1~10장, 장당 10MB 이하. 첫 번째 이미지가 대표 이미지입니다.</p>
            <input id="item-images" name="images" type="file" multiple accept="image/jpeg,image/png,image/webp"
              className="mt-3 block w-full min-w-0 rounded-lg border border-line p-3 text-sm"
              aria-invalid={!!fields.images} aria-describedby="images-help images-error"
              onChange={event => { const files = Array.from(event.target.files ?? []);
                updateImages([...images, ...files.map(file => ({ file, id: crypto.randomUUID(), preview: URL.createObjectURL(file) }))]);
                event.target.value = ''; }} />
            {images.length > 0 && <ol className="mt-4 space-y-3">{images.map((image, index) => <li key={image.id} className="flex items-center gap-3 rounded-lg border border-line p-3">
              <img src={image.preview} alt={`${image.file.name} 미리보기`} className="h-20 w-20 shrink-0 rounded-lg bg-canvas object-cover" />
              <div className="min-w-0 flex-1"><p className="text-xs font-semibold text-brand">{index === 0 ? '대표 이미지' : `${index + 1}번째 이미지`}</p>
                <p className="mt-1 break-all text-sm">{image.file.name}</p><p className="mt-1 text-xs text-muted">{(image.file.size / 1_000_000).toFixed(2)} MB</p></div>
              <Button variant="secondary" aria-label={`${index + 1}번째 이미지 삭제`} onClick={() => { removeImage(image.id); }}>삭제</Button>
            </li>)}</ol>}
            {images.length > 0 && <p className="mt-2 text-sm text-muted">선택한 이미지 {images.length} / 10장</p>}
            <p id="images-error" role={fields.images ? 'alert' : undefined} className="mt-2 text-sm text-danger">{fields.images}</p>
          </div>
          <div><label htmlFor="item-name" className="font-semibold">물품 이름</label><input id="item-name" name="name" value={name} className={inputClass}
            aria-invalid={!!fields.name} aria-describedby="name-help name-error" onChange={event => { setName(event.target.value); edit('name'); }} />
            <p id="name-help" className="mt-1 text-xs text-muted">{Array.from(name).length} / 100자</p>
            {fields.name && <p id="name-error" role="alert" className="mt-1 text-sm text-danger">{fields.name}</p>}</div>
          <div><label htmlFor="item-description" className="font-semibold">설명 <span className="text-sm font-normal text-muted">(선택)</span></label>
            <textarea id="item-description" name="description" rows={4} value={description} className={inputClass}
              aria-invalid={!!fields.description} aria-describedby="description-help description-error" onChange={event => { setDescription(event.target.value); edit('description'); }} />
            <p id="description-help" className="mt-1 text-xs text-muted">{Array.from(description).length} / 2,000자</p>
            {fields.description && <p id="description-error" role="alert" className="mt-1 text-sm text-danger">{fields.description}</p>}</div>
          <div className="grid gap-6 sm:grid-cols-2">
            <div><label htmlFor="item-start-price" className="font-semibold">시작가 (원)</label><input id="item-start-price" name="startPrice" inputMode="numeric" value={startPrice} className={inputClass}
              aria-invalid={!!fields.startPrice} aria-describedby="start-price-help start-price-error" onChange={event => { setStartPrice(event.target.value); edit('startPrice'); }} />
              <p id="start-price-help" className="mt-2 text-xs text-muted">1,000원 단위 · 1,000원 이상 10억 원 이하</p>
              {fields.startPrice && <p id="start-price-error" role="alert" className="mt-1 text-sm text-danger">{fields.startPrice}</p>}</div>
            <div><label htmlFor="item-reserve-price" className="font-semibold">최저 낙찰가 (원, 선택)</label><input id="item-reserve-price" name="reservePrice" inputMode="numeric" value={reservePrice} className={inputClass}
              aria-invalid={!!fields.reservePrice} aria-describedby="reserve-price-help reserve-price-error" onChange={event => { setReservePrice(event.target.value); edit('reservePrice'); }} />
              <p id="reserve-price-help" className="mt-2 text-xs leading-relaxed text-muted">시작가 이상 10억 원 이하, 1,000원 단위. 비워두면 시작가로 저장합니다. 등록 후 조회 화면에는 표시되지 않습니다.</p>
              {fields.reservePrice && <p id="reserve-price-error" role="alert" className="mt-1 text-sm text-danger">{fields.reservePrice}</p>}</div>
          </div>
          {registration.isError && <div role="alert" className="rounded-lg bg-danger-soft p-4 text-sm text-danger"><p>{apiErrorMessage(registration.error)}</p>
            <p className="mt-2">전송 후 결과를 확인하지 못했다면 <a href="#/me/items" className="underline">내 출품 목록</a>을 먼저 확인해 주세요.</p></div>}
          <div className="flex flex-wrap items-center justify-between gap-4 border-t border-line pt-6">
            <p className="text-sm text-muted">등록한 물품은 검수 대기로 접수됩니다.</p>
            <Button type="submit" disabled={busy}>{busy ? '등록 처리 중' : '출품 등록하기'}</Button>
          </div>
        </fieldset>
      </form>
    </Card>
  </main>;
}
