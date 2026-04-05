import { notFound } from 'next/navigation';
import { serverFetch } from '@/lib/server-fetch';
import { mapProductResponse } from '@/lib/mappers';
import type { PageResponse } from '@/types';
import type { ProductResponse } from '@/types/api-responses';
import { ProductDetailView } from './ProductDetailView';

interface ProductDetailPageProps {
  params: Promise<{ id: string }>;
}

export default async function ProductDetailPage({ params }: ProductDetailPageProps) {
  const { id } = await params;

  const productData = await serverFetch<ProductResponse>('product', `/api/products/${id}`);
  if (!productData) return notFound();
  const product = mapProductResponse(productData);

  // Related products: same category, exclude current.
  const related = await serverFetch<PageResponse<ProductResponse>>(
    'product',
    `/api/products?category=${encodeURIComponent(product.category)}&size=8`,
  );
  const relatedProducts = (related?.content ?? [])
    .map(mapProductResponse)
    .filter((p) => p.id !== product.id)
    .slice(0, 4);

  return <ProductDetailView product={product} relatedProducts={relatedProducts} />;
}
