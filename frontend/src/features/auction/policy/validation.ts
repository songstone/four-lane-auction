import type { BidPolicyCreateRequest } from '../../../api/generated/models';
import type { FieldError } from '../../../api/errorMessages';

export interface TierDraft { key: string; minPrice: string; increment: string }
export function tierDraft(minPrice = '', increment = ''): TierDraft {
  return { key: crypto.randomUUID(), minPrice, increment };
}

export function validatePolicyForm(tiers: TierDraft[]): { fields: FieldError[]; input: BidPolicyCreateRequest | null } {
  const fields: FieldError[] = [];
  const parsed: BidPolicyCreateRequest['tiers'] = [];
  const seen = new Set<number>();
  let previous = -1;
  if (tiers.length === 0) fields.push({ field: 'tiers', reason: 'REQUIRED' });
  tiers.forEach((tier, index) => {
    const number = (value: string, name: string, minimum: number) => {
      const field = `tiers[${index}].${name}`;
      const amount = Number(value);
      const reason = value.trim() === '' ? 'REQUIRED' : !/^-?\d+$/.test(value) ? 'INVALID_FORMAT' :
        !Number.isSafeInteger(amount) || amount < minimum ? 'OUT_OF_RANGE' : null;
      if (reason) { fields.push({ field, reason }); return null; }
      return amount;
    };
    const minPrice = number(tier.minPrice, 'minPrice', 0);
    const increment = number(tier.increment, 'increment', 1);
    if (minPrice !== null) {
      const reason = index === 0 && minPrice !== 0 ? 'OUT_OF_RANGE' : seen.has(minPrice) ? 'DUPLICATE' :
        minPrice <= previous ? 'INVALID_FORMAT' : null;
      if (reason) fields.push({ field: `tiers[${index}].minPrice`, reason });
      seen.add(minPrice); previous = minPrice;
    }
    if (increment !== null && increment % 1000 !== 0) fields.push({ field: `tiers[${index}].increment`, reason: 'NOT_MULTIPLE_OF_1000' });
    if (minPrice !== null && increment !== null) parsed.push({ minPrice, increment });
  });
  return { fields, input: fields.length > 0 ? null : { tiers: parsed } };
}
