'use client';

import { useState } from 'react';
import { ShoppingBag, Check } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { useToastStore } from '@/stores/toast-store';
import type { Product } from '@/types';

interface AddToCartButtonProps {
  product: Product;
  selectedSize: string | null;
  isDropAnnounced?: boolean;
  dropOpensAt?: string;
}

export function AddToCartButton({
  product,
  selectedSize,
  isDropAnnounced,
  dropOpensAt,
}: AddToCartButtonProps) {
  const [added, setAdded] = useState(false);
  const addItem = useCartStore((s) => s.addItem);
  const addToast = useToastStore((s) => s.addToast);

  const sizeData = selectedSize ? product.sizes.find((s) => s.label === selectedSize) : null;

  const isSoldOut = sizeData ? sizeData.stock === 0 : false;

  const handleAdd = () => {
    if (!selectedSize || isSoldOut || isDropAnnounced) return;

    addItem({
      productId: product.id,
      productName: product.name,
      brandName: product.brand.name,
      size: selectedSize,
      priceKrw: product.priceKrw,
      priceUsd: product.priceUsd,
      priceJpy: product.priceJpy,
      imageUrl: product.imageUrls[0] ?? '',
      quantity: 1,
      dropId: product.dropId,
    });

    addToast('success', `${product.name} (${selectedSize}) added to cart`);
    setAdded(true);
    setTimeout(() => setAdded(false), 2000);
  };

  if (isDropAnnounced && dropOpensAt) {
    return (
      <button
        type="button"
        disabled
        className="flex w-full items-center justify-center gap-2 bg-[#e8e4df] px-6 py-4 text-sm font-semibold uppercase tracking-widest text-[#6b6560]"
      >
        Drop Opens Soon
      </button>
    );
  }

  if (!selectedSize) {
    return (
      <button
        type="button"
        disabled
        className="flex w-full items-center justify-center gap-2 bg-[#e8e4df] px-6 py-4 text-sm font-semibold uppercase tracking-widest text-[#6b6560]"
      >
        Select a Size
      </button>
    );
  }

  if (isSoldOut) {
    return (
      <button
        type="button"
        disabled
        className="flex w-full items-center justify-center gap-2 bg-[#e8e4df] px-6 py-4 text-sm font-semibold uppercase tracking-widest text-[#6b6560]"
      >
        Sold Out
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={handleAdd}
      className={`flex w-full items-center justify-center gap-2 px-6 py-4 text-sm font-semibold uppercase tracking-widest transition-colors ${
        added ? 'bg-[#1a1a1a] text-white' : 'bg-[#c4633e] text-white hover:bg-[#a84f2e]'
      }`}
    >
      {added ? (
        <>
          <Check size={16} />
          Added to Cart
        </>
      ) : (
        <>
          <ShoppingBag size={16} strokeWidth={1.5} />
          Add to Cart
        </>
      )}
    </button>
  );
}
