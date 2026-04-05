'use client';

import { useState } from 'react';
import { ShoppingBag, Check } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { useToastStore } from '@/stores/toast-store';
import type { Product, ProductVariant } from '@/types';

interface AddToCartButtonProps {
  product: Product;
  selectedVariant: ProductVariant | null;
}

export function AddToCartButton({ product, selectedVariant }: AddToCartButtonProps) {
  const [added, setAdded] = useState(false);
  const addItem = useCartStore((s) => s.addItem);
  const addToast = useToastStore((s) => s.addToast);

  const isSoldOut = selectedVariant ? selectedVariant.stockQuantity === 0 : false;
  const primaryImage = product.images.find((img) => img.isPrimary) ?? product.images[0];

  const handleAdd = () => {
    if (!selectedVariant || isSoldOut) return;

    addItem({
      productId: product.id,
      variantId: selectedVariant.id,
      productName: product.name,
      brandName: product.brand.name,
      size: selectedVariant.size,
      color: selectedVariant.color,
      price: selectedVariant.price ?? product.price,
      imageUrl: primaryImage?.url ?? '',
      quantity: 1,
      stockAvailable: selectedVariant.stockQuantity,
    });

    addToast('success', `${product.name} (${selectedVariant.size}) added to cart`);
    setAdded(true);
    setTimeout(() => setAdded(false), 2000);
  };

  if (!selectedVariant) {
    return (
      <button
        type="button"
        disabled
        className="flex w-full items-center justify-center gap-2 rounded-md bg-muted px-6 py-3 text-sm font-semibold text-muted-foreground"
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
        className="flex w-full items-center justify-center gap-2 rounded-md bg-muted px-6 py-3 text-sm font-semibold text-muted-foreground"
      >
        Sold Out
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={handleAdd}
      className={`flex w-full items-center justify-center gap-2 rounded-md px-6 py-3 text-sm font-semibold transition-colors ${
        added
          ? 'bg-foreground text-background'
          : 'bg-primary text-primary-foreground hover:bg-primary/90'
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
