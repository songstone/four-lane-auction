import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { AuthProvider } from './features/auth/AuthProvider';
import { connectAuthQueryCache } from './features/auth/authStore';
import './styles.css';

const queryClient = new QueryClient();
connectAuthQueryCache(queryClient);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider><App /></AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
