import type { FieldError } from '../../api/errorMessages';

export const MAX_IMAGE_BYTES = 10_000_000;
export interface ItemForm {
  name: string;
  description: string;
  startPrice: string;
  reservePrice: string;
  images: File[];
}

export async function validateItemForm(form: ItemForm): Promise<FieldError[]> {
  const fields: FieldError[] = [];
  if (!form.name.trim()) fields.push({ field: 'name', reason: 'REQUIRED' });
  else if (Array.from(form.name).length > 100) fields.push({ field: 'name', reason: 'TOO_LONG' });
  if (Array.from(form.description).length > 2000) fields.push({ field: 'description', reason: 'TOO_LONG' });
  function price(value: string, field: string, optional = false): number | null {
    if (!value.trim()) {
      if (!optional) fields.push({ field, reason: 'REQUIRED' });
      return null;
    }
    const amount = Number(value);
    const reason = !/^-?\d+$/.test(value) ? 'INVALID_FORMAT' :
      !Number.isSafeInteger(amount) || amount < 1000 || amount > 1_000_000_000 ? 'OUT_OF_RANGE' :
      amount % 1000 !== 0 ? 'NOT_MULTIPLE_OF_1000' : null;
    if (reason) fields.push({ field, reason });
    return reason ? null : amount;
  }
  const start = price(form.startPrice, 'startPrice');
  const reserve = price(form.reservePrice, 'reservePrice', true);
  if (start !== null && reserve !== null && reserve < start) fields.push({ field: 'reservePrice', reason: 'LESS_THAN_START_PRICE' });
  if (!form.images.length) fields.push({ field: 'images', reason: 'REQUIRED' });
  else if (form.images.length > 10) fields.push({ field: 'images', reason: 'TOO_MANY' });
  else if (form.images.some(file => file.size > MAX_IMAGE_BYTES)) fields.push({ field: 'images', reason: 'OUT_OF_RANGE' });
  else {
    for (const file of form.images) {
      try {
        const bytes = new Uint8Array(await file.slice(0, 16).arrayBuffer());
        const starts = (prefix: number[]) => bytes.length >= prefix.length && prefix.every((value, i) => bytes[i] === value);
        const text = (start: number, end: number) => new TextDecoder('ascii').decode(bytes.slice(start, end));
        if (!starts([255, 216, 255]) && !starts([137, 80, 78, 71, 13, 10, 26, 10]) &&
          !(bytes.length >= 16 && text(0, 4) === 'RIFF' && text(8, 12) === 'WEBP' && ['VP8 ', 'VP8L', 'VP8X'].includes(text(12, 16)))) {
          fields.push({ field: 'images', reason: 'INVALID_FORMAT' }); break;
        }
      } catch { fields.push({ field: 'images', reason: 'INVALID_FORMAT' }); break; }
    }
  }
  return fields;
}
