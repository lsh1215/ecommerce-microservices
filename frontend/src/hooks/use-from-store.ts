import { useEffect, useState } from 'react';
import type { StoreApi, UseBoundStore } from 'zustand';

export function useFromStore<T, U>(store: UseBoundStore<StoreApi<T>>, callback: (state: T) => U) {
  const value = store(callback);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- SSR hydration guard
    setHydrated(true);
  }, []);

  if (!hydrated) return undefined;
  return value;
}
