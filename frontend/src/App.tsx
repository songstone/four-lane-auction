import { Card } from './components/ui/Card';
import { EmptyState } from './components/ui/EmptyState';

export function App() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-6 py-5">
          <span className="flex size-10 items-center justify-center rounded-lg bg-ink text-lg font-bold text-surface" aria-hidden="true">
            4
          </span>
          <span className="text-lg font-semibold tracking-tight">Four-Lane Auction</span>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-12">
        <h1 className="text-3xl font-bold tracking-tight">실시간 경매</h1>
        <p className="mt-3 text-muted">여러 레인의 경매를 한 화면에서 만나는 공간입니다.</p>
        <Card className="mt-8">
          <EmptyState
            title="경매 공간을 준비하고 있습니다"
            description="회차와 레인 정보는 서비스 연결 후 이곳에 표시됩니다."
          />
        </Card>
      </main>
    </div>
  );
}
