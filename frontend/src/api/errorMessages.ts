const messages: Record<string, string> = {
  INVALID_REQUEST: '회차 조회 요청을 처리할 수 없습니다. 다시 조회해 주세요.',
  INTERNAL_ERROR: '서버에서 회차를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
};

export class ApiRequestError extends Error {
  constructor(readonly code: string, message: string) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

export function apiErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return messages[error.code] ?? error.message;
  }
  return '서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.';
}
