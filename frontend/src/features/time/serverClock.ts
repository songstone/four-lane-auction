// Keep network receipt times outside Query's cache: a cache hit is not a fresh clock sample.
export class ServerClock {
  private samples: { received: number; offset: number }[] = [];
  sample(serverTime: string, received = performance.now()) {
    const time = Date.parse(serverTime);
    if (!Number.isFinite(time)) return;
    this.prune(received);
    this.samples.push({ received, offset: time - received });
  }
  private prune(now: number) {
    this.samples = this.samples.filter(sample => now >= sample.received && now - sample.received < 60_000);
  }
  now(monotonic = performance.now()): number | null {
    this.prune(monotonic);
    return this.samples.length ? monotonic + Math.max(...this.samples.map(sample => sample.offset)) : null;
  }
}
export const serverClock = new ServerClock();
