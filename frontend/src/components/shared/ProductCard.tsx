import Link from 'next/link';
import Image from 'next/image';
import type { Product, DropStatus } from '@/types';
import { DropStatusBadge } from './DropStatusBadge';
import { CurrencyPrice } from './CurrencyPrice';

interface ProductCardProps {
  product: Product;
  dropStatus?: DropStatus;
}

export function ProductCard({ product, dropStatus }: ProductCardProps) {
  const mainImage = product.imageUrls[0] ?? '';
  const totalStock = product.sizes.reduce((sum, s) => sum + s.stock, 0);
  const isSoldOut = totalStock === 0;
  const isLowStock = !isSoldOut && totalStock <= 3;

  return (
    <Link
      href={`/products/${product.id}`}
      className="group flex flex-col gap-0"
      aria-label={`${product.brand.name} ${product.name}`}
    >
      {/* Image */}
      <div className="relative aspect-[3/4] overflow-hidden bg-[#e8e4df]">
        {mainImage && (
          <Image
            src={mainImage}
            alt={product.name}
            fill
            sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
            className={`object-cover transition-transform duration-500 group-hover:scale-105 ${isSoldOut ? 'opacity-60' : ''}`}
          />
        )}

        {/* Drop badge */}
        {product.dropId && dropStatus && (
          <div className="absolute left-2 top-2">
            <DropStatusBadge status={dropStatus} />
          </div>
        )}

        {/* Sold out overlay */}
        {isSoldOut && (
          <div className="absolute inset-0 flex items-center justify-center bg-[#faf9f6]/60">
            <span className="bg-[#1a1a1a] px-3 py-1 text-xs font-medium uppercase tracking-widest text-[#faf9f6]">
              Sold Out
            </span>
          </div>
        )}

        {/* Low stock */}
        {isLowStock && !isSoldOut && (
          <div className="absolute bottom-2 right-2">
            <span className="bg-[#c4633e] px-2 py-0.5 text-xs font-medium text-white">
              Last {totalStock}
            </span>
          </div>
        )}
      </div>

      {/* Info */}
      <div className="flex flex-col gap-1 pt-3">
        <p className="text-xs font-medium uppercase tracking-wider text-[#6b6560]">
          {product.brand.name} · {product.origin}
        </p>
        <h3 className="text-sm font-medium leading-snug text-[#1a1a1a] group-hover:underline">
          {product.name}
        </h3>
        {product.nameKo && (
          <p className="text-xs text-[#6b6560]">{product.nameKo}</p>
        )}
        <p className="mt-0.5 text-sm font-semibold text-[#1a1a1a]">
          <CurrencyPrice
            priceKrw={product.priceKrw}
            priceUsd={product.priceUsd}
            priceJpy={product.priceJpy}
          />
        </p>
      </div>
    </Link>
  );
}
