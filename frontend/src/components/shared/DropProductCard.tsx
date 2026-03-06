'use client';

import { useState } from 'react';
import Image from 'next/image';
import { ShoppingBag, X } from 'lucide-react';
import type { Product } from '@/types';
import { DropStatusBadge } from './DropStatusBadge';
import { CurrencyPrice } from './CurrencyPrice';
import { useCartStore } from '@/features/cart/store/cart-store';
import { useToastStore } from '@/stores/toast-store';
import type { DropStatus } from '@/types';

interface DropProductCardProps {
  product: Product;
  dropStatus?: DropStatus;
}

export function DropProductCard({ product, dropStatus }: DropProductCardProps) {
  const [mobileSheetOpen, setMobileSheetOpen] = useState(false);
  const addItem = useCartStore((s) => s.addItem);
  const addToast = useToastStore((s) => s.addToast);

  const mainImage = product.imageUrls[0] ?? '';
  const totalStock = product.sizes.reduce((sum, s) => sum + s.stock, 0);
  const isSoldOut = totalStock === 0;
  const isLowStock = !isSoldOut && totalStock <= 3;
  const availableSizes = product.sizes.filter((s) => s.stock > 0);

  const handleQuickAdd = (sizeLabel: string) => {
    const size = product.sizes.find((s) => s.label === sizeLabel);
    if (!size || size.stock === 0) {
      addToast('error', 'This size just sold out');
      return;
    }

    addItem({
      productId: product.id,
      productName: product.name,
      brandName: product.brand.name,
      size: sizeLabel,
      priceKrw: product.priceKrw,
      priceUsd: product.priceUsd,
      priceJpy: product.priceJpy,
      imageUrl: mainImage,
      quantity: 1,
      dropId: product.dropId,
    });

    addToast('success', `${product.name} (${sizeLabel}) added to cart`);
    setMobileSheetOpen(false);
  };

  return (
    <>
      <div className="group flex flex-col gap-0">
        {/* Image — on mobile, tapping opens bottom sheet */}
        <button
          type="button"
          onClick={() => setMobileSheetOpen(true)}
          className="relative aspect-[3/4] w-full overflow-hidden bg-[#e8e4df] text-left md:pointer-events-none"
          aria-label={`Quick add ${product.name}`}
        >
          {mainImage && (
            <Image
              src={mainImage}
              alt={product.name}
              fill
              sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
              className={`object-cover transition-transform duration-500 group-hover:scale-105 ${isSoldOut ? 'opacity-60' : ''}`}
            />
          )}

          {dropStatus && (
            <div className="absolute left-2 top-2">
              <DropStatusBadge status={dropStatus} />
            </div>
          )}

          {isSoldOut && (
            <div className="absolute inset-0 flex items-center justify-center bg-[#faf9f6]/60">
              <span className="bg-[#1a1a1a] px-3 py-1 text-xs font-medium uppercase tracking-widest text-[#faf9f6]">
                Sold Out
              </span>
            </div>
          )}

          {isLowStock && (
            <div className="absolute bottom-2 right-2">
              <span className="bg-[#c4633e] px-2 py-0.5 text-xs font-medium text-white">
                Last {totalStock}
              </span>
            </div>
          )}
        </button>

        {/* Info */}
        <div className="flex flex-col gap-1 pt-3">
          <p className="text-xs font-medium uppercase tracking-wider text-[#6b6560]">
            {product.brand.name} · {product.origin}
          </p>
          <h3 className="text-sm font-medium leading-snug text-[#1a1a1a]">
            {product.name}
          </h3>
          <p className="mt-0.5 text-sm font-semibold text-[#1a1a1a]">
            <CurrencyPrice
              priceKrw={product.priceKrw}
              priceUsd={product.priceUsd}
              priceJpy={product.priceJpy}
            />
          </p>
        </div>

        {/* Desktop inline size buttons */}
        {!isSoldOut && (
          <div className="mt-2 hidden flex-wrap gap-1 md:flex">
            {product.sizes.map((size) => (
              <button
                key={size.label}
                type="button"
                disabled={size.stock === 0}
                onClick={() => handleQuickAdd(size.label)}
                className={`min-w-[36px] px-2 py-1.5 text-xs font-medium transition-colors ${
                  size.stock === 0
                    ? 'cursor-not-allowed border border-[#e8e4df] text-[#a39e93] line-through'
                    : 'border border-[#e8e4df] text-[#1a1a1a] hover:border-[#1a1a1a] hover:bg-[#1a1a1a] hover:text-white'
                }`}
              >
                {size.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Mobile bottom sheet */}
      {mobileSheetOpen && !isSoldOut && (
        <div className="fixed inset-0 z-50 md:hidden">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setMobileSheetOpen(false)}
          />
          <div className="absolute inset-x-0 bottom-0 bg-[#faf9f6] px-4 pb-8 pt-4 animate-in">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-[#1a1a1a]">Select Size</h3>
              <button
                type="button"
                onClick={() => setMobileSheetOpen(false)}
                className="flex h-8 w-8 items-center justify-center text-[#6b6560]"
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>

            <div className="mb-4 flex gap-3">
              <div className="relative h-24 w-20 shrink-0 overflow-hidden bg-[#e8e4df]">
                {mainImage && (
                  <Image
                    src={mainImage}
                    alt={product.name}
                    fill
                    className="object-cover"
                    sizes="80px"
                  />
                )}
              </div>
              <div>
                <p className="text-xs text-[#6b6560]">{product.brand.name}</p>
                <p className="text-sm font-medium text-[#1a1a1a]">{product.name}</p>
                <p className="mt-1 text-sm font-semibold text-[#1a1a1a]">
                  <CurrencyPrice
                    priceKrw={product.priceKrw}
                    priceUsd={product.priceUsd}
                    priceJpy={product.priceJpy}
                  />
                </p>
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              {product.sizes.map((size) => (
                <button
                  key={size.label}
                  type="button"
                  disabled={size.stock === 0}
                  onClick={() => handleQuickAdd(size.label)}
                  className={`flex min-h-[44px] min-w-[44px] items-center justify-center px-4 py-2 text-sm font-medium transition-colors ${
                    size.stock === 0
                      ? 'cursor-not-allowed border border-[#e8e4df] text-[#a39e93] line-through'
                      : 'border border-[#e8e4df] text-[#1a1a1a] active:bg-[#1a1a1a] active:text-white'
                  }`}
                >
                  {size.label}
                  {size.stock > 0 && size.stock <= 3 && (
                    <span className="ml-1 text-[10px] text-[#c4633e]">({size.stock})</span>
                  )}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
