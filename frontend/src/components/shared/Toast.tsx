'use client';

import { useEffect, useState } from 'react';
import { Check, X, Info } from 'lucide-react';
import { useToastStore, type ToastType } from '@/stores/toast-store';

const iconMap: Record<ToastType, React.ReactNode> = {
  success: <Check size={16} className="text-green-600" />,
  error: <X size={16} className="text-red-600" />,
  info: <Info size={16} className="text-blue-600" />,
};

const bgMap: Record<ToastType, string> = {
  success: 'border-green-200 bg-green-50',
  error: 'border-red-200 bg-red-50',
  info: 'border-blue-200 bg-blue-50',
};

function ToastItem({
  id,
  type,
  message,
}: {
  id: string;
  type: ToastType;
  message: string;
}) {
  const removeToast = useToastStore((s) => s.removeToast);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    requestAnimationFrame(() => setVisible(true));
  }, []);

  return (
    <div
      className={`flex items-center gap-3 border px-4 py-3 shadow-sm transition-all duration-300 ${bgMap[type]} ${
        visible ? 'translate-y-0 opacity-100' : 'translate-y-2 opacity-0'
      }`}
      role="status"
    >
      <span className="shrink-0">{iconMap[type]}</span>
      <p className="flex-1 text-sm font-medium text-[#1a1a1a]">{message}</p>
      <button
        type="button"
        onClick={() => removeToast(id)}
        className="shrink-0 text-[#6b6560] hover:text-[#1a1a1a]"
        aria-label="Dismiss"
      >
        <X size={14} />
      </button>
    </div>
  );
}

export function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-20 right-4 z-[100] flex flex-col gap-2 md:bottom-6 md:right-6">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} {...toast} />
      ))}
    </div>
  );
}
