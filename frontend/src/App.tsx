import { HomePage } from './pages/HomePage';
import { useSyncExternalStore } from 'react';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { AccountPage } from './pages/AccountPage';
import { AuthHeader } from './components/AuthHeader';

function subscribeRoute(listener: () => void) {
  window.addEventListener('hashchange', listener);
  return () => { window.removeEventListener('hashchange', listener); };
}
function routeSnapshot() { return window.location.hash.slice(1) || '/'; }

export function App() {
  const route = useSyncExternalStore(subscribeRoute, routeSnapshot);
  const url = new URL(route, window.location.origin);
  const returnTo = url.searchParams.get('returnTo');
  const page = url.pathname === '/login' ? <LoginPage returnTo={returnTo} /> :
    url.pathname === '/signup' ? <SignupPage returnTo={returnTo} /> :
    url.pathname === '/me' ? <AccountPage /> : <HomePage />;
  return <><AuthHeader />{page}</>;
}
