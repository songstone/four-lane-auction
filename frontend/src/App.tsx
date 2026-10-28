import { AdminSessionPage } from './pages/AdminSessionPage';
import { HomePage } from './pages/HomePage';
import { useSyncExternalStore } from 'react';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { AccountPage } from './pages/AccountPage';
import { AuthHeader } from './components/AuthHeader';
import { AuthenticatedRealtimeConnection } from './realtime/AuthenticatedRealtimeConnection';
import { RequireUser } from './features/auth/RequireUser';
import { ItemRegistrationPage } from './pages/ItemRegistrationPage';
import { MyItemsPage } from './pages/MyItemsPage';
import { RequireAdmin } from './features/auth/RequireAdmin';
import { AdminItemReviewPage } from './pages/AdminItemReviewPage';
import { AdminBidPolicyPage } from './pages/AdminBidPolicyPage';

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
    url.pathname === '/items/new' ? <RequireUser returnTo="/items/new"><ItemRegistrationPage /></RequireUser> :
    url.pathname === '/me/items' ? <RequireUser returnTo="/me/items"><MyItemsPage /></RequireUser> :
    url.pathname === '/admin/sessions' ? <RequireAdmin returnTo="/admin/sessions"><AdminSessionPage /></RequireAdmin> :
    url.pathname === '/admin/items' ? <RequireAdmin><AdminItemReviewPage /></RequireAdmin> :
    url.pathname === '/admin/bid-policies' ? <RequireAdmin returnTo="/admin/bid-policies"><AdminBidPolicyPage /></RequireAdmin> :
    url.pathname === '/me' ? <AccountPage /> : <HomePage />;
  return <><AuthenticatedRealtimeConnection /><AuthHeader />{page}</>;
}
