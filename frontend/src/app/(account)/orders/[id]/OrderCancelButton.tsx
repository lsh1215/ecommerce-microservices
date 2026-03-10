'use client';

import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { OrderAPI } from '@/features/orders/api/order-api';
import { useToastStore } from '@/stores/toast-store';

interface OrderCancelButtonProps {
  orderId: string;
}

export function OrderCancelButton({ orderId }: OrderCancelButtonProps) {
  const [showConfirm, setShowConfirm] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const queryClient = useQueryClient();
  const addToast = useToastStore((s) => s.addToast);

  const handleCancel = async () => {
    setIsCancelling(true);
    try {
      const res = await OrderAPI.cancel(orderId);
      if (!res.success) {
        addToast('error', res.error?.message ?? 'Unable to cancel this order.');
        return;
      }
      await queryClient.invalidateQueries({ queryKey: ['order', orderId] });
      await queryClient.invalidateQueries({ queryKey: ['orders'] });
      addToast('success', 'Order has been cancelled.');
      setShowConfirm(false);
    } catch {
      addToast('error', 'An unexpected error occurred. Please try again.');
    } finally {
      setIsCancelling(false);
    }
  };

  return (
    <>
      {!showConfirm ? (
        <button
          type="button"
          onClick={() => setShowConfirm(true)}
          className="border border-red-300 px-6 py-2.5 text-sm font-medium text-red-600 transition-colors hover:bg-red-50"
        >
          Cancel Order
        </button>
      ) : (
        <div className="border border-red-200 bg-red-50 p-5">
          <p className="mb-4 text-sm text-red-700">
            Are you sure you want to cancel order <strong>{orderId}</strong>? This action cannot be
            undone.
          </p>
          <div className="flex gap-3">
            <button
              type="button"
              disabled={isCancelling}
              onClick={handleCancel}
              className="flex items-center gap-2 bg-red-600 px-6 py-2 text-sm font-medium text-white transition-colors hover:bg-red-700 disabled:opacity-60"
            >
              {isCancelling ? (
                <>
                  <Loader2 size={14} className="animate-spin" />
                  Cancelling...
                </>
              ) : (
                'Confirm Cancellation'
              )}
            </button>
            <button
              type="button"
              disabled={isCancelling}
              onClick={() => setShowConfirm(false)}
              className="border border-[#e8e4df] px-6 py-2 text-sm font-medium text-[#1a1a1a] transition-colors hover:border-[#1a1a1a]"
            >
              Keep Order
            </button>
          </div>
        </div>
      )}
    </>
  );
}
