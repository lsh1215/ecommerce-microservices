'use client';

import { useState, useMemo } from 'react';
import { mockProducts, mockBrands } from '@/mocks';
import type { Product, Measurements } from '@/types';

const MEASUREMENT_KEYS: (keyof Measurements)[] = [
  'chest',
  'shoulder',
  'sleeve',
  'length',
  'waist',
  'inseam',
  'thigh',
  'hem',
];

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

interface Selection {
  brandSlug: string;
  productId: string;
  sizeLabel: string;
}

function SelectorColumn({
  label,
  selection,
  onChange,
}: {
  label: string;
  selection: Selection;
  onChange: (s: Selection) => void;
}) {
  const brandProducts = useMemo(
    () =>
      selection.brandSlug
        ? mockProducts.filter((p) => p.brand.slug === selection.brandSlug)
        : [],
    [selection.brandSlug],
  );

  const selectedProduct = mockProducts.find((p) => p.id === selection.productId);
  const sizesWithMeasurements = selectedProduct
    ? selectedProduct.sizes.filter((s) => s.measurements)
    : [];

  return (
    <div className="flex flex-col gap-3">
      <p className="text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
        {label}
      </p>

      <select
        value={selection.brandSlug}
        onChange={(e) =>
          onChange({ brandSlug: e.target.value, productId: '', sizeLabel: '' })
        }
        className="w-full border border-[#e8e4df] bg-white px-3 py-2 text-sm text-[#1a1a1a] focus:border-[#1a1a1a] focus:outline-none"
      >
        <option value="">Select Brand</option>
        {mockBrands.map((b) => (
          <option key={b.slug} value={b.slug}>
            {b.name}
          </option>
        ))}
      </select>

      <select
        value={selection.productId}
        onChange={(e) =>
          onChange({ ...selection, productId: e.target.value, sizeLabel: '' })
        }
        disabled={!selection.brandSlug}
        className="w-full border border-[#e8e4df] bg-white px-3 py-2 text-sm text-[#1a1a1a] focus:border-[#1a1a1a] focus:outline-none disabled:bg-[#f3f0eb] disabled:text-[#a39e93]"
      >
        <option value="">Select Product</option>
        {brandProducts.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name}
          </option>
        ))}
      </select>

      <select
        value={selection.sizeLabel}
        onChange={(e) => onChange({ ...selection, sizeLabel: e.target.value })}
        disabled={!selection.productId || sizesWithMeasurements.length === 0}
        className="w-full border border-[#e8e4df] bg-white px-3 py-2 text-sm text-[#1a1a1a] focus:border-[#1a1a1a] focus:outline-none disabled:bg-[#f3f0eb] disabled:text-[#a39e93]"
      >
        <option value="">Select Size</option>
        {sizesWithMeasurements.map((s) => (
          <option key={s.label} value={s.label}>
            {s.label}
          </option>
        ))}
      </select>
    </div>
  );
}

function getMeasurements(sel: Selection): Measurements | null {
  if (!sel.productId || !sel.sizeLabel) return null;
  const product = mockProducts.find((p) => p.id === sel.productId);
  if (!product) return null;
  const size = product.sizes.find((s) => s.label === sel.sizeLabel);
  return size?.measurements ?? null;
}

export default function SizeComparePage() {
  const [selA, setSelA] = useState<Selection>({
    brandSlug: '',
    productId: '',
    sizeLabel: '',
  });
  const [selB, setSelB] = useState<Selection>({
    brandSlug: '',
    productId: '',
    sizeLabel: '',
  });

  const measA = getMeasurements(selA);
  const measB = getMeasurements(selB);

  const hasBothSelected = measA !== null && measB !== null;

  const productA = mockProducts.find((p) => p.id === selA.productId);
  const productB = mockProducts.find((p) => p.id === selB.productId);

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 md:px-6">
      <div className="mb-10">
        <h1 className="font-heading text-3xl font-bold text-[#1a1a1a]">Size Comparison</h1>
        <p className="mt-2 text-sm text-[#6b6560]">
          Select two products and sizes to compare garment measurements side by side.
        </p>
      </div>

      {/* Selectors */}
      <div className="mb-10 grid grid-cols-1 gap-8 md:grid-cols-2">
        <SelectorColumn label="Product A" selection={selA} onChange={setSelA} />
        <SelectorColumn label="Product B" selection={selB} onChange={setSelB} />
      </div>

      {/* Comparison Table */}
      {hasBothSelected ? (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b-2 border-[#1a1a1a]">
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]">
                  Measurement
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]">
                  {productA?.brand.name} — {productA?.name} ({selA.sizeLabel})
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]">
                  {productB?.brand.name} — {productB?.name} ({selB.sizeLabel})
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]">
                  Difference
                </th>
              </tr>
            </thead>
            <tbody>
              {MEASUREMENT_KEYS.map((key) => {
                const valA = measA?.[key];
                const valB = measB?.[key];

                if (valA === undefined && valB === undefined) return null;

                const diff =
                  valA !== undefined && valB !== undefined ? valB - valA : null;
                const hasDiff = diff !== null && diff !== 0;

                return (
                  <tr key={key} className="border-b border-[#e8e4df]">
                    <td className="px-4 py-3 text-sm font-medium text-[#1a1a1a]">
                      {MEASUREMENT_LABELS[key]}
                    </td>
                    <td className="px-4 py-3 text-sm text-[#6b6560]">
                      {valA !== undefined ? `${valA} cm` : '—'}
                    </td>
                    <td className="px-4 py-3 text-sm text-[#6b6560]">
                      {valB !== undefined ? `${valB} cm` : '—'}
                    </td>
                    <td
                      className={`px-4 py-3 text-sm font-medium ${
                        hasDiff ? 'text-[#c4633e]' : 'text-[#a39e93]'
                      }`}
                    >
                      {diff !== null
                        ? diff === 0
                          ? 'Same'
                          : `${diff > 0 ? '+' : ''}${diff} cm`
                        : '—'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="border border-dashed border-[#e8e4df] py-16 text-center">
          <p className="text-sm text-[#6b6560]">
            Select a brand, product, and size on both sides to see the comparison.
          </p>
        </div>
      )}
    </div>
  );
}
