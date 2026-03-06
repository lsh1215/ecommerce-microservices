'use client';

import { useState } from 'react';

interface OrderCancelButtonProps {
  orderId: string;
}

export function OrderCancelButton({ orderId }: OrderCancelButtonProps) {
  const [showConfirm, setShowConfirm] = useState(false);

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
            Are you sure you want to cancel order <strong>{orderId}</strong>? This action
            cannot be undone.
          </p>
          <div className="flex gap-3">
            <button
              type="button"
              className="bg-red-600 px-6 py-2 text-sm font-medium text-white transition-colors hover:bg-red-700"
            >
              Confirm Cancellation
            </button>
            <button
              type="button"
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
