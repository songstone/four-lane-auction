import { defineConfig } from 'orval';

// Generation contacts the running backend directly; browser requests remain same-origin.
const input = new URL(process.env.OPENAPI_URL ?? 'http://127.0.0.1:8080/v3/api-docs');
if (!['http:', 'https:'].includes(input.protocol) || input.pathname !== '/v3/api-docs') {
  throw new Error('OPENAPI_URL must point to a running backend /v3/api-docs');
}

export default defineConfig({
  fourlane: {
    input: { target: input.toString() },
    output: {
      target: './src/api/generated/sessions.ts',
      schemas: './src/api/generated/models',
      client: 'fetch',
      baseUrl: '',
      clean: true,
      override: {
        fetch: { includeHttpResponseReturnType: true },
        // Orval's blob decoder otherwise reads JSON errors as Blob too.
        // Only this binary operation throws with { status, info: ApiErrorResponse }.
        operations: { getImage: { fetch: { forceSuccessResponse: true } } },
      },
    },
  },
});
