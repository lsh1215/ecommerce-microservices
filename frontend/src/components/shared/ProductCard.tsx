import Link from 'next/link';
import Image from 'next/image';
import type { Product } from '@/types';
import { PriceDisplay } from './PriceDisplay';

interface ProductCardProps {
  product: Product;
}

export function ProductCard({ product }: ProductCardProps) {
  const primaryImage = product.images.find((img) => img.isPrimary) ?? product.images[0];
  const totalStock = product.variants.reduce((sum, v) => sum + v.stockQuantity, 0);
  const isSoldOut = totalStock === 0;

  return (
    <Link
      href={`/products/${product.id}`}
      className="group flex flex-col gap-0"
      aria-label={`${product.brand.name} ${product.name}`}
    >
      <div className="relative aspect-[3/4] overflow-hidden bg-muted">
        {primaryImage ? (
          <Image
            src={primaryImage.url}
            alt={product.name}
            fill
            sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
            className={`object-cover transition-transform duration-500 group-hover:scale-105 ${
              isSoldOut ? 'opacity-60' : ''
            }`}
          />
        ) : (
          <div className="h-full w-full bg-muted" />
        )}

        {isSoldOut && (
          <div className="absolute inset-0 flex items-center justify-center bg-background/60">
            <span className="bg-foreground px-3 py-1 text-xs font-medium uppercase tracking-widest text-background">
              Sold Out
            </span>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-1 pt-3">
        <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          {product.brand.name}
        </p>
        <h3 className="text-sm font-medium leading-snug text-foreground group-hover:underline">
          {product.name}
        </h3>
        <p className="mt-0.5 text-sm font-semibold text-foreground">
          <PriceDisplay amount={product.price} />
        </p>
      </div>
    </Link>
  );
}
