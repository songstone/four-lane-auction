import type { ItemStage } from './generated/models';
const messages: Record<string, string> = {
  INVALID_REQUEST: '입력값을 확인해 주세요.',
  INTERNAL_ERROR: '서버에서 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  UNAUTHENTICATED: '로그인이 필요합니다.',
  INVALID_CREDENTIALS: '이메일 또는 비밀번호를 확인해 주세요.',
  EMAIL_ALREADY_EXISTS: '이미 가입된 이메일입니다. 로그인해 주세요.',
  FORBIDDEN: '접근 권한이 없습니다.',
  AUTH_SYNC_UNAVAILABLE: '로그인에는 HTTPS 또는 localhost에서 Web Locks를 지원하는 브라우저가 필요합니다.',
  LOGOUT_UNCONFIRMED: '서버 로그아웃을 확인하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.',
  TOKEN_EXPIRED: '연결 인증이 만료되어 다시 연결하고 있습니다.',
  TOKEN_INVALID: '연결 인증을 복구하고 있습니다.',
  SUBSCRIPTION_DENIED: '실시간 구독이 거절되었습니다. 새로고침해 주세요.',
  SEND_DENIED: '허용하지 않는 실시간 요청입니다. 새로고침해 주세요.',
  FRAME_DENIED: '실시간 연결 요청이 거절되었습니다. 새로고침해 주세요.',
  UNSUPPORTED_IMAGE_TYPE: 'JPEG, PNG, WebP 이미지 파일을 선택해 주세요.',
  IMAGE_TOO_LARGE: '이미지는 장당 10MB, 전체 요청은 101MB까지 등록할 수 있습니다.',
  IMAGE_NOT_FOUND: '이미지를 찾을 수 없습니다.',
  AUTH_SESSION_CHANGED: '로그인 계정이 바뀌었습니다. 현재 계정으로 다시 확인해 주세요.',
  SESSION_NOT_FOUND: '회차를 찾을 수 없습니다. 목록을 다시 확인해 주세요.',
  LOT_NOT_FOUND: '편성 물품을 찾을 수 없습니다. 최신 편성에서 다시 선택해 주세요.',
  SESSION_NOT_SCHEDULED: '이미 시작한 회차는 편성이나 설정을 변경할 수 없습니다.',
  SCHEDULED_START_IN_PAST: '예정 시작 시각은 서버의 현재 시각보다 뒤여야 합니다.',
  SESSION_RESULT_UNCONFIRMED: '처리 결과가 불확실하면 다시 제출하기 전에 목록이나 상세를 새로 조회해 주세요.',
  LANE_NOT_FOUND: '레인을 찾을 수 없습니다. 회차를 다시 조회해 주세요.',
  ITEM_NOT_ASSIGNABLE: '편성할 수 없는 물품이 있습니다. 현재 단계를 확인해 주세요.',
  ITEM_NOT_FOUND: '출품물을 찾을 수 없습니다. 목록을 다시 확인해 주세요.',
  ITEM_NOT_PENDING_REVIEW: '이미 검수가 끝난 출품물입니다. 최신 상태를 확인해 주세요.',
};

const fieldMessages: Record<string, string> = {
  email: '올바른 이메일을 255자 이내로 입력해 주세요.',
  password: '비밀번호는 8~64자이며 UTF-8 최대 72바이트입니다.',
  nickname: '닉네임을 2~20자로 입력해 주세요. 중복은 허용됩니다.',
};

export interface FieldError { field: string; reason: string }

export class ApiRequestError extends Error {
  constructor(readonly code: string, message: string, readonly fields: FieldError[] = [], readonly items: ItemStage[] = [], readonly itemIds: number[] = []) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

export function responseError(data: { error: { code: string; message: string; fields?: FieldError[]; items?: ItemStage[]; itemIds?: number[] } }) {
  return new ApiRequestError(data.error.code, data.error.message, data.error.fields ?? [], data.error.items ?? [], data.error.itemIds ?? []);
}

export function authFieldErrors(error: unknown): Record<string, string> {
  return error instanceof ApiRequestError ? Object.fromEntries(error.fields.map(({ field }) =>
    [field, fieldMessages[field] ?? '입력값을 확인해 주세요.'])) : {};
}

export function validateAuthForm(password: string, nickname?: string): Record<string, string> {
  const fields: Record<string, string> = {};
  const length = Array.from(password).length;
  if (length < 8 || length > 64 || new TextEncoder().encode(password).length > 72 || !password.trim()) {
    fields.password = fieldMessages.password;
  }
  if (nickname !== undefined && (Array.from(nickname).length < 2 || Array.from(nickname).length > 20 || !nickname.trim())) {
    fields.nickname = fieldMessages.nickname;
  }
  return fields;
}

export function apiErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return messages[error.code] ?? error.message;
  }
  return '서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

const itemFieldMessages: Record<string, Record<string, string>> = {
  name: { REQUIRED: '물품 이름을 입력해 주세요.', TOO_LONG: '이름은 100자까지 입력할 수 있습니다.' },
  description: { TOO_LONG: '설명은 2,000자까지 입력할 수 있습니다.' },
  startPrice: {
    REQUIRED: '시작가를 입력해 주세요.', INVALID_FORMAT: '원 단위 정수를 입력해 주세요.',
    OUT_OF_RANGE: '시작가는 1,000원 이상 10억 원 이하입니다.', NOT_MULTIPLE_OF_1000: '시작가는 1,000원 단위로 입력해 주세요.',
  },
  reservePrice: {
    INVALID_FORMAT: '원 단위 정수를 입력해 주세요.', OUT_OF_RANGE: '최저 낙찰가는 1,000원 이상 10억 원 이하입니다.',
    NOT_MULTIPLE_OF_1000: '최저 낙찰가는 1,000원 단위로 입력해 주세요.', LESS_THAN_START_PRICE: '최저 낙찰가는 시작가 이상이어야 합니다.',
  },
  images: {
    REQUIRED: '이미지를 1장 이상 선택해 주세요.', TOO_MANY: '이미지는 최대 10장까지 선택할 수 있습니다.',
    OUT_OF_RANGE: '이미지는 장당 10MB 이하로 선택해 주세요.', INVALID_FORMAT: 'JPEG, PNG, WebP 이미지 파일을 선택해 주세요.',
  },
};

export function itemFieldErrors(fields: FieldError[]): Record<string, string> {
  return Object.fromEntries(fields.map(({ field, reason }) => [field, itemFieldMessages[field]?.[reason] ?? '입력값을 확인해 주세요.']));
}

export function reviewReasonError(reason: string): string | null {
  if (!reason.trim()) return '반려 사유를 입력해 주세요.';
  if (Array.from(reason).length > 500) return '반려 사유는 500자까지 입력할 수 있습니다.';
  return null;
}

export function reviewFieldError(error: unknown): string | null {
  if (!(error instanceof ApiRequestError)) return null;
  const field = error.fields.find(field => field.field === 'reason');
  return field ? field.reason === 'TOO_LONG' ? '반려 사유는 500자까지 입력할 수 있습니다.' : '반려 사유를 입력해 주세요.' : null;
}

export function policyFieldErrors(fields: FieldError[]): Record<string, string> {
  return Object.fromEntries(fields.map(({ field, reason }) => {
    const isMin = field.endsWith('.minPrice');
    const message = reason === 'REQUIRED' ? (isMin ? '구간 하한을 입력해 주세요.' : field.endsWith('.increment') ? '호가 단위를 입력해 주세요.' : '구간을 1개 이상 입력해 주세요.') :
      reason === 'DUPLICATE' ? '같은 구간 하한을 중복해서 사용할 수 없습니다.' :
      reason === 'NOT_MULTIPLE_OF_1000' ? '호가 단위는 1,000원의 배수로 입력해 주세요.' :
      reason === 'OUT_OF_RANGE' ? (field === 'tiers[0].minPrice' ? '첫 구간의 하한은 0원입니다.' : '허용 범위의 정수를 입력해 주세요. 최대 9,007,199,254,740,991원입니다.') :
      isMin ? '원 단위 정수를 입력하고 구간 하한을 오름차순으로 배치해 주세요.' : '원 단위 정수를 입력해 주세요.';
    return [field, message];
  }));
}

export function sessionFieldErrors(fields: FieldError[]): Record<string, string> {
  return Object.fromEntries(fields.map(({ field, reason }) => [field,
    field === 'laneCount' ? '레인 수는 1~4개입니다.' :
    field === 'scheduledStartAt' ? reason === 'REQUIRED' ? '예정 시작 시각을 입력해 주세요.' : '올바른 현지 시각을 밀리초 단위로 입력해 주세요.' :
    reason === 'REQUIRED' ? '시간 값을 입력해 주세요.' : '시간 값은 1,000~60,000ms의 정수입니다.',
  ]));
}

const lineupStageLabels: Record<ItemStage['stage'], string> = {
  PENDING_REVIEW: '검수 대기', REJECTED: '반려', APPROVED: '승인됨 · 편성 대기',
  SCHEDULED: '편성됨', ON_AUCTION: '경매 중', SOLD: '낙찰', UNSOLD: '유찰',
};
export function lineupErrorDetails(error: unknown, names: Record<number, string>): string[] {
  if (!(error instanceof ApiRequestError)) return [];
  return [
    ...error.items.map(item => `${names[item.itemId] ?? `물품 #${item.itemId}`}: ${lineupStageLabels[item.stage]}`),
    ...error.itemIds.map(id => `${names[id] ?? `물품 #${id}`}: 찾을 수 없는 물품`),
    ...error.fields.map(field => field.reason === 'DUPLICATE' ? '같은 물품을 두 번 넣을 수 없습니다.' : '선택한 물품을 확인해 주세요.'),
  ];
}
