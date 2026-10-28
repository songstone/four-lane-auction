import { useEffect, useState } from 'react';
import { serverClock } from './serverClock';
export function useServerClock() {
  const [now, setNow] = useState(() => serverClock.now());
  useEffect(() => {
    const timer = window.setInterval(() => { setNow(serverClock.now()); }, 250);
    return () => { window.clearInterval(timer); };
  }, []);
  return now;
}
