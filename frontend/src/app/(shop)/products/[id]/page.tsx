'use client';

import { useState, useCallback } from 'react';
import { notFound } from 'next/navigation';
import Image from 'next/image';
import Link from 'next/link';
import { use } from 'react';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { ProductCard } from '@/components/shared/ProductCard';
import { SizeSelector } from '@/features/products/components/SizeSelector';
import { AddToCartButton } from '@/features/products/components/AddToCartButton';
import { getProductById, getRelatedProducts } from '@/mocks/products';
import type { ProductVariant } from '@/types';

interface ProductDetailPageProps {
  params: Promise<{ id: string }>;
}

export default function ProductDetailPage({ params }: ProductDetailPageProps) {
  const { id } = use(params);

  const product = getProductById(id);
  const [selectedVariant, setSelectedVariant] = useState<ProductVariant | null>(null);
  const [activeImageIdx, setActiveImageIdx] = useState(0);

  const handleVariantChange = useCallback((variant: ProductVariant | null) => {
    setSelectedVariant(variant);
  }, []);

  if (!product) return notFound();

  const relatedProducts = getRelatedProducts(product, 4);
  const sortedImages = [...product.images].sort((a, b) => a.sortOrder - b.sortOrder);
  const activeImage = sortedImages[activeImageIdx];

  return (
    <div>
      <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">
        <nav className="mb-6 text-sm text-muted-foreground">
          <Link href="/" className="hover:text-foreground">
            Home
          </Link>
          <span className="mx-2">/</span>
          <Link href="/products" className="hover:text-foreground">
            Products
          </Link>
          <span className="mx-2">/</span>
          <span className="text-foreground">{product.name}</span>
        </nav>

        <div className="grid grid-cols-1 gap-10 md:grid-cols-2">
          <div className="flex flex-col gap-4">
            <div className="relative aspect-[3/4] overflow-hidden rounded-lg bg-muted">
              {activeImage ? (
                <Image
                  src={activeImage.url}
                  alt={product.name}
                  fill
                  priority
                  className="object-cover"
                  sizes="(max-width: 768px) 100vw, 50vw"
                />
              ) : (
                <div className="h-full w-full bg-muted" />
              )}
            </div>

            {sortedImages.length > 1 && (
              <div className="flex gap-2 overflow-x-auto">
                {sortedImages.map((img, i) => (
                  <button
                    key={img.id}
                    type="button"
                    onClick={() => setActiveImageIdx(i)}
                    className={`relative h-20 w-20 shrink-0 overflow-hidden rounded-md bg-muted transition-opacity ${
                      activeImageIdx === i
                        ? 'ring-2 ring-primary ring-offset-1'
                        : 'opacity-60 hover:opacity-100'
                    }`}
                  >
                    <Image
                      src={img.url}
                      alt={`${product.name} view ${i + 1}`}
                      fill
                      className="object-cover"
                      sizes="80px"
                    />
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="flex flex-col gap-6">
            <div>
              <Link
                href={`/products?brandId=${product.brand.id}`}
                className="text-xs font-semibold uppercase tracking-widest text-muted-foreground hover:text-primary"
              >
                {product.brand.name}
              </Link>
              <h1 className="mt-2 text-2xl font-bold text-foreground md:text-3xl">{product.name}</h1>
              <p className="mt-3 text-xl font-semibold text-foreground">
                <PriceDisplay amount={selectedVariant?.price ?? product.price} />
              </p>
            </div>

            <p className="text-sm leading-relaxed text-muted-foreground">{product.description}</p>

            <div className="border-t border-border pt-5">
              <SizeSelector variants={product.variants} onVariantChange={handleVariantChange} />
            </div>

            <div className="hidden md:block">
              <AddToCartButton product={product} selectedVariant={selectedVariant} />
            </div>

            <div className="rounded-lg bg-surface p-4 text-sm text-muted-foreground">
              <p className="font-medium text-foreground">Shipping & Returns</p>
              <p className="mt-1">Free shipping on orders over ₩50,000. Returns accepted within 14 days.</p>
            </div>
          </div>
        </div>

        {relatedProducts.length > 0 && (
          <section className="mt-16 border-t border-border pt-12">
            <h2 className="mb-6 text-xl font-bold text-foreground">You May Also Like</h2>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
              {relatedProducts.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          </section>
        )}
      </div>

      <div className="fixed inset-x-0 bottom-0 z-50 border-t border-border bg-background p-3 md:hidden">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-foreground">{product.name}</p>
            <p className="text-sm font-semibold text-foreground">
              <PriceDisplay amount={selectedVariant?.price ?? product.price} />
            </p>
          </div>
          <div className="w-48">
            <AddToCartButton product={product} selectedVariant={selectedVariant} />
          </div>
        </div>
      </div>
    </div>
  );
}
