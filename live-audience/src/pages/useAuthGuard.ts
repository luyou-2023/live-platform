import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { token } from '../api';

export function useAuthGuard() {
  const nav = useNavigate();
  useEffect(() => {
    if (!token()) nav('/login', { replace: true });
  }, []);
}
