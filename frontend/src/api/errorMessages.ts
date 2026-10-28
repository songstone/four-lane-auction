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
  AUTH_SESSION_CHANGED: '로그인 계정이 바뀌었습니다. 내 출품 목록을 확인해 주세요.',
};

const fieldMessages: Record<string, string> = {
  email: '올바른 이메일을 255자 이내로 입력해 주세요.',
  password: '비밀번호는 8~64자이며 UTF-8 최대 72바이트입니다.',
  nickname: '닉네임을 2~20자로 입력해 주세요. 중복은 허용됩니다.',
};

export interface FieldError { field: string; reason: string }

export class ApiRequestError extends Error {
  constructor(readonly code: string, message: string, readonly fields: FieldError[] = []) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

export function responseError(data: { error: { code: string; message: string; fields?: FieldError[] } }) {
  return new ApiRequestError(data.error.code, data.error.message, data.error.fields ?? []);
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
