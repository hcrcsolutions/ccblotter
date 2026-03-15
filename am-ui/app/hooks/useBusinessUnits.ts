import { useState, useEffect } from 'react';
import { getBackendUrl } from '../lib/settings';

export function useBusinessUnits() {
  const [businessUnits, setBusinessUnits] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    fetch(`${getBackendUrl()}/api/v1/business-units`)
      .then((res) => {
        if (!res.ok) {
          throw new Error('Failed to fetch business units');
        }
        return res.json();
      })
      .then((data: string[]) => {
        if (!cancelled) {
          setBusinessUnits(data);
        }
      })
      .catch(() => {
        // silently ignore — dropdown will just be empty
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { businessUnits, loading };
}
