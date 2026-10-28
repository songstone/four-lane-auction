import type { AdminSession, SessionCreateRequest, SessionTimingRequest } from '../../../api/generated/models';
import type { FieldError } from '../../../api/errorMessages';
export const timingNames = ['firstBidWaitMs', 'softCloseMs', 'lotGapMs', 'resumeMinMs'] as const;
export type TimingName = typeof timingNames[number];
export const timingLabels: Record<TimingName, string> = {
  firstBidWaitMs: '첫 입찰 대기 (ms)', softCloseMs: '소프트 클로즈 (ms)',
  lotGapMs: 'Lot 간 간격 (ms)', resumeMinMs: '재개 최소 보장 (ms)',
};
export interface SessionDraft { laneCount: string; scheduledStartAt: string; timing: Record<TimingName, string> }
const pad = (value: number, size = 2) => String(value).padStart(size, '0');
export function localInput(value: string): string {
  const d = new Date(value);
  return `${pad(d.getFullYear(), 4)}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`;
}
export function localWithOffset(value: string): string | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/.exec(value);
  if (!m) return null;
  const [year, month, day, hour, minute, second, ms] = [Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4]), Number(m[5]), Number(m[6] ?? '0'), Number((m[7] ?? '').padEnd(3, '0'))];
  if (year! < 1000 || year! > 9999) return null;
  const date = new Date(year!, month! - 1, day!, hour!, minute!, second!, ms!);
  if (date.getFullYear() !== year || date.getMonth() + 1 !== month || date.getDate() !== day || date.getHours() !== hour || date.getMinutes() !== minute || date.getSeconds() !== second || date.getMilliseconds() !== ms) return null;
  const offset = -date.getTimezoneOffset();
  return `${localInput(date.toISOString())}${offset >= 0 ? '+' : '-'}${pad(Math.floor(Math.abs(offset) / 60))}:${pad(Math.abs(offset) % 60)}`;
}
export function sessionDraft(session?: AdminSession): SessionDraft {
  return { laneCount: String(session?.laneCount ?? 1), scheduledStartAt: session ? localInput(session.scheduledStartAt) : '',
    timing: Object.fromEntries(timingNames.map(name => [name, session ? String(session.timing[name]) : ''])) as Record<TimingName, string> };
}
export function validateSessionDraft(draft: SessionDraft, creating: boolean): { fields: FieldError[]; input?: SessionCreateRequest } {
  const fields: FieldError[] = [];
  const scheduledStartAt = localWithOffset(draft.scheduledStartAt);
  if (!scheduledStartAt) fields.push({ field: 'scheduledStartAt', reason: draft.scheduledStartAt ? 'INVALID_FORMAT' : 'REQUIRED' });
  const laneCount = Number(draft.laneCount);
  if (!/^[1-4]$/.test(draft.laneCount)) fields.push({ field: 'laneCount', reason: 'OUT_OF_RANGE' });
  const timing: SessionTimingRequest = {};
  for (const name of timingNames) {
    const raw = draft.timing[name];
    if (!raw && creating) continue;
    const value = Number(raw);
    if (!raw) fields.push({ field: `timing.${name}`, reason: 'REQUIRED' });
    else if (!/^\d+$/.test(raw) || !Number.isSafeInteger(value)) fields.push({ field: `timing.${name}`, reason: 'INVALID_FORMAT' });
    else if (value < 1000 || value > 60000) fields.push({ field: `timing.${name}`, reason: 'OUT_OF_RANGE' });
    else timing[name] = value;
  }
  return fields.length || !scheduledStartAt ? { fields } : { fields, input: { laneCount, scheduledStartAt, timing } };
}
