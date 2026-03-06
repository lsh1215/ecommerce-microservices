'use client';

import { useState, useCallback } from 'react';
import { notFound } from 'next/navigation';
import Image from 'next/image';
import Link from 'next/link';
import { use } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { CurrencyPrice } from '@/components/shared/CurrencyPrice';
import { CountdownTimer } from '@/components/shared/CountdownTimer';
import { DropStatusBadge } from '@/components/shared/DropStatusBadge';
import { ProductCard } from '@/components/shared/ProductCard';
import { MobileImageCarousel } from '@/components/shared/MobileImageCarousel';
import { SizeSelector } from '@/features/products/components/SizeSelector';
import { AddToCartButton } from '@/features/products/components/AddToCartButton';
import { getProductById, getRelatedProducts } from '@/mocks/products';
import { getDropById } from '@/mocks/drops';
import type { Measurements } from '@/types';

const ORIGIN_FLAGS: Record<string, string> = {
  Korea: '\uD83C\uDDF0\uD83C\uDDF7',
  Japan: '\uD83C\uDDEF\uD83C\uDDF5',
  USA: '\uD83C\uDDFA\uD83C\uDDF8',
};

const MEASUREMENT_LABELS: Record<keyof Measurements, string> = {
  chest: 'Chest',
  shoulder: 'Shoulder',
  sleeve: 'Sleeve',
  length: 'Length',
  waist: 'Waist',
  inseam: 'Inseam',
  thigh: 'Thigh',
  hem: 'Hem',
};

interface ProductDetailPageProps {
  params: Promise<{ id: string }>;
}

export default function ProductDetailPage({ params }: ProductDetailPageProps) {
  const { id } = use(params);
  const product = getProductById(id);

  if (!product) notFound();

  const drop = product.dropId ? getDropById(product.dropId) : null;
  const related = getRelatedProducts(product, 4);

  const isDropLive = drop?.status === 'SELLING' || drop?.status === 'OPEN';
  const isDropAnnounced = drop?.status === 'ANNOUNCED';

  const hasMeasurements = product.sizes.some((s) => s.measurements);
  const measurementKeys = hasMeasurements
    ? (Object.keys(
        product.sizes.find((s) => s.measurements)?.measurements ?? {},
      ) as (keyof Measurements)[])
    : [];

  const [selectedSize, setSelectedSize] = useState<string | null>(null);
  const [activeImage, setActiveImage] = useState(0);
  const [measurementsOpen, setMeasurementsOpen] = useState(false);

  const handleSizeChange = useCallback((size: string | null) => {
    setSelectedSize(size);
  }, []);

  return (
    <div>
      {/* Drop countdown bar */}
      {drop && (isDropLive || isDropAnnounced) && (
        <div className="sticky top-14 z-40 border-b border-[#e8e4df] bg-[#1a1a1a] px-4 py-2.5">
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
            <Link
              href={`/drops/${drop.id}`}
              className="text-xs font-medium text-[#a39e93] hover:text-white"
            >
              {drop.name}
            </Link>
            <div className="flex items-center gap-3">
              <DropStatusBadge status={drop.status} />
              <CountdownTimer
                targetDate={isDropLive ? drop.closesAt : drop.opensAt}
                className="font-semibold text-white"
              />
            </div>
          </div>
        </div>
      )}

      <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">
        <div className="grid grid-cols-1 gap-10 md:grid-cols-2">
          {/* Image gallery */}
          <div>
            {/* Mobile: swipe carousel */}
            <div className="md:hidden">
              <MobileImageCarousel images={product.imageUrls} alt={product.name} />
            </div>

            {/* Desktop: main image + thumbnails */}
            <div className="hidden flex-col gap-4 md:flex">
              <div className="relative aspect-[3/4] overflow-hidden bg-[#e8e4df]">
                <Image
                  src={product.imageUrls[activeImage] ?? product.imageUrls[0] ?? ''}
                  alt={product.name}
                  fill
                  priority
                  className="object-cover"
                  sizes="50vw"
                />
              </div>
              {product.imageUrls.length > 1 && (
                <div className="flex gap-2 overflow-x-auto">
                  {product.imageUrls.map((url, i) => (
                    <button
                      key={i}
                      type="button"
                      onClick={() => setActiveImage(i)}
                      className={`relative h-20 w-20 shrink-0 overflow-hidden bg-[#e8e4df] ${
                        activeImage === i
                          ? 'ring-2 ring-[#1a1a1a] ring-offset-1'
                          : 'opacity-60 hover:opacity-100'
                      }`}
                    >
                      <Image
                        src={url}
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
          </div>

          {/* Product info */}
          <div className="flex flex-col gap-6">
            {/* Brand + origin */}
            <div>
              <Link
                href={`/products?brand=${product.brand.slug}`}
                className="text-xs font-semibold uppercase tracking-widest text-[#6b6560] hover:text-[#c4633e]"
              >
                {product.brand.name}
              </Link>
              <h1 className="font-heading mt-2 text-2xl font-bold text-[#1a1a1a] md:text-3xl">
                {product.name}
              </h1>
              {product.nameKo && (
                <p className="mt-1 text-sm text-[#6b6560]">{product.nameKo}</p>
              )}
              {product.nameJa && (
                <p className="mt-1 text-sm text-[#6b6560]">{product.nameJa}</p>
              )}
              <div className="mt-3 flex items-center gap-3">
                <p className="text-xl font-semibold text-[#1a1a1a]">
                  <CurrencyPrice
                    priceKrw={product.priceKrw}
                    priceUsd={product.priceUsd}
                    priceJpy={product.priceJpy}
                  />
                </p>
                <span className="text-sm text-[#6b6560]">
                  {ORIGIN_FLAGS[product.origin]} {product.origin}
                </span>
              </div>
            </div>

            {/* Description */}
            <p className="text-sm leading-relaxed text-[#6b6560]">
              {product.description}
            </p>

            {/* Fabric details */}
            <div className="border-t border-[#e8e4df] pt-5">
              <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
                Fabric Details
              </p>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <span className="text-[#6b6560]">Type</span>
                  <p className="font-medium text-[#1a1a1a]">{product.fabric.type}</p>
                </div>
                <div>
                  <span className="text-[#6b6560]">Weight</span>
                  <p className="font-medium text-[#1a1a1a]">{product.fabric.weightOz}oz</p>
                </div>
                <div>
                  <span className="text-[#6b6560]">Weave</span>
                  <p className="font-medium text-[#1a1a1a]">{product.fabric.weave}</p>
                </div>
                <div>
                  <span className="text-[#6b6560]">Era</span>
                  <p className="font-medium text-[#1a1a1a]">{product.era}</p>
                </div>
              </div>
            </div>

            {/* Size selector */}
            <div className="border-t border-[#e8e4df] pt-5">
              <SizeSelector sizes={product.sizes} onSizeChange={handleSizeChange} />
            </div>

            {/* Measurements table */}
            {hasMeasurements && (
              <div className="border-t border-[#e8e4df] pt-4">
                <button
                  type="button"
                  onClick={() => setMeasurementsOpen(!measurementsOpen)}
                  className="flex w-full items-center justify-between py-1"
                >
                  <span className="text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
                    Size Measurements (cm)
                  </span>
                  {measurementsOpen ? (
                    <ChevronUp size={16} className="text-[#6b6560]" />
                  ) : (
                    <ChevronDown size={16} className="text-[#6b6560]" />
                  )}
                </button>
                {measurementsOpen && (
                  <div className="mt-3 overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-[#e8e4df]">
                          <th className="pb-2 pr-4 text-left text-xs font-semibold text-[#6b6560]">
                            Size
                          </th>
                          {measurementKeys.map((key) => (
                            <th
                              key={key}
                              className="pb-2 pr-4 text-left text-xs font-semibold text-[#6b6560]"
                            >
                              {MEASUREMENT_LABELS[key]}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {product.sizes
                          .filter((s) => s.measurements)
                          .map((size) => (
                            <tr
                              key={size.label}
                              className={`border-b border-[#e8e4df] ${
                                selectedSize === size.label ? 'bg-[#f3f0eb]' : ''
                              }`}
                            >
                              <td className="py-2 pr-4 font-medium text-[#1a1a1a]">
                                {size.label}
                              </td>
                              {measurementKeys.map((key) => (
                                <td key={key} className="py-2 pr-4 text-[#1a1a1a]">
                                  {size.measurements?.[key] ?? '-'}
                                </td>
                              ))}
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {/* Drop banner */}
            {drop && (isDropLive || isDropAnnounced) && (
              <div className="border border-[#e8e4df] bg-[#f3f0eb] p-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
                      Part of
                    </p>
                    <Link
                      href={`/drops/${drop.id}`}
                      className="text-sm font-medium text-[#1a1a1a] hover:text-[#c4633e]"
                    >
                      {drop.name}
                    </Link>
                  </div>
                  <div className="flex items-center gap-2">
                    <DropStatusBadge status={drop.status} />
                    <CountdownTimer
                      targetDate={isDropLive ? drop.closesAt : drop.opensAt}
                      className="text-sm font-semibold text-[#1a1a1a]"
                    />
                  </div>
                </div>
              </div>
            )}

            {/* Add to cart (desktop) */}
            <div className="hidden md:block">
              <AddToCartButton
                product={product}
                selectedSize={selectedSize}
                isDropAnnounced={isDropAnnounced}
                dropOpensAt={drop?.opensAt}
              />
            </div>
          </div>
        </div>

        {/* Related products */}
        {related.length > 0 && (
          <section className="mt-16 border-t border-[#e8e4df] pt-12">
            <h2 className="font-heading mb-6 text-xl font-bold text-[#1a1a1a]">
              You May Also Like
            </h2>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
              {related.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          </section>
        )}
      </div>

      {/* Mobile sticky Add to Cart bar */}
      <div className="fixed inset-x-0 bottom-0 z-50 border-t border-[#e8e4df] bg-[#faf9f6] p-3 md:hidden">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-[#1a1a1a]">{product.name}</p>
            <p className="text-sm font-semibold text-[#1a1a1a]">
              <CurrencyPrice
                priceKrw={product.priceKrw}
                priceUsd={product.priceUsd}
                priceJpy={product.priceJpy}
              />
            </p>
          </div>
          <div className="w-48">
            <AddToCartButton
              product={product}
              selectedSize={selectedSize}
              isDropAnnounced={isDropAnnounced}
              dropOpensAt={drop?.opensAt}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
