import { useContext } from 'react';
import { AuthContext } from './AuthProvider';

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('AuthProvider is required');
  return context;
}
