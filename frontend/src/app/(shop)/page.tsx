import Image from 'next/image';
import Link from 'next/link';
import { ProductCard } from '@/components/shared/ProductCard';
import { getFeaturedProducts } from '@/mocks/products';
import type { Category } from '@/types';

export const metadata = {
  title: 'Shop — Browse Quality Products',
  description: 'Discover quality products at great prices. Free shipping on orders over ₩50,000.',
};

const CATEGORIES: { value: Category; label: string; image: string; description: string }[] = [
  {
    value: 'tops',
    label: 'Tops',
    image: 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=600&q=80',
    description: 'Shirts, T-Shirts & More',
  },
  {
    value: 'bottoms',
    label: 'Bottoms',
    image: 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=600&q=80',
    description: 'Pants, Jeans & Shorts',
  },
  {
    value: 'outerwear',
    label: 'Outerwear',
    image: 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=600&q=80',
    description: 'Jackets & Coats',
  },
  {
    value: 'shoes',
    label: 'Shoes',
    image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&q=80',
    description: 'Sneakers & Footwear',
  },
  {
    value: 'accessories',
    label: 'Accessories',
    image: 'https://images.unsplash.com/photo-1512327536842-5aa37d1ba3e3?w=600&q=80',
    description: 'Bags, Hats & More',
  },
  {
    value: 'electronics',
    label: 'Electronics',
    image: 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&q=80',
    description: 'Gadgets & Devices',
  },
];

export default function HomePage() {
  const featuredProducts = getFeaturedProducts(8);

  return (
    <>
      {/* Hero */}
      <section className="relative h-[75vh] min-h-[480px] max-h-[720px] overflow-hidden bg-foreground">
        <Image
          src="https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=1600&q=80"
          alt="Shop hero"
          fill
          priority
          className="object-cover opacity-60"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-foreground/70 via-foreground/20 to-transparent" />

        <div className="absolute inset-0 flex flex-col items-center justify-center px-4 text-center">
          <p className="text-xs font-semibold uppercase tracking-widest text-background/80">
            New Season
          </p>
          <h1 className="mt-3 text-4xl font-bold leading-tight text-background md:text-6xl">
            Style Made Simple
          </h1>
          <p className="mt-4 max-w-md text-sm leading-relaxed text-background/80">
            Quality clothing and accessories at honest prices. Free shipping on orders over ₩50,000.
          </p>
          <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
            <Link
              href="/products"
              className="rounded-md bg-background px-8 py-3 text-sm font-semibold text-foreground transition-colors hover:bg-background/90"
            >
              Shop Now
            </Link>
            <Link
              href="/products?category=outerwear"
              className="rounded-md border border-background px-8 py-3 text-sm font-semibold text-background transition-colors hover:bg-background/10"
            >
              New Arrivals
            </Link>
          </div>
        </div>
      </section>

      {/* Category Grid */}
      <section className="mx-auto max-w-7xl px-4 py-16 md:px-6 md:py-20">
        <div className="mb-10 flex items-end justify-between">
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-muted-foreground">
              Browse by
            </p>
            <h2 className="text-3xl font-bold text-foreground">Category</h2>
          </div>
          <Link
            href="/products"
            className="hidden text-sm font-medium text-primary underline underline-offset-4 md:block"
          >
            View All
          </Link>
        </div>

        <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
          {CATEGORIES.map((cat) => (
            <Link
              key={cat.value}
              href={`/products?category=${cat.value}`}
              className="group relative aspect-square overflow-hidden rounded-xl bg-muted"
            >
              <Image
                src={cat.image}
                alt={cat.label}
                fill
                className="object-cover transition-transform duration-500 group-hover:scale-105"
                sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 300px"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-foreground/60 via-foreground/10 to-transparent" />
              <div className="absolute bottom-0 left-0 p-4">
                <p className="text-sm font-bold text-background">{cat.label}</p>
                <p className="text-xs text-background/80">{cat.description}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Featured Products */}
      <section className="border-t border-border bg-surface px-4 py-16 md:px-6 md:py-20">
        <div className="mx-auto max-w-7xl">
          <div className="mb-10 flex items-end justify-between">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-muted-foreground">
                Handpicked
              </p>
              <h2 className="text-3xl font-bold text-foreground">Featured Products</h2>
            </div>
            <Link
              href="/products"
              className="hidden text-sm font-medium text-primary underline underline-offset-4 md:block"
            >
              View All
            </Link>
          </div>

          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            {featuredProducts.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>

          <div className="mt-10 text-center md:hidden">
            <Link
              href="/products"
              className="inline-block rounded-md border border-foreground px-8 py-3 text-sm font-semibold text-foreground transition-colors hover:bg-foreground hover:text-background"
            >
              View All Products
            </Link>
          </div>
        </div>
      </section>

      {/* Promotional Banner */}
      <section className="mx-auto max-w-7xl px-4 py-16 md:px-6">
        <div className="relative overflow-hidden rounded-2xl bg-primary px-8 py-12 md:py-16">
          <div className="relative z-10 text-center">
            <p className="text-xs font-semibold uppercase tracking-widest text-primary-foreground/80">
              Limited Time
            </p>
            <h2 className="mt-2 text-3xl font-bold text-primary-foreground md:text-4xl">
              Free Shipping on All Orders
            </h2>
            <p className="mt-3 text-sm text-primary-foreground/80">
              On all orders over ₩50,000. Use code <strong>FREESHIP</strong> at checkout.
            </p>
            <Link
              href="/products"
              className="mt-6 inline-block rounded-md bg-background px-8 py-3 text-sm font-semibold text-primary transition-colors hover:bg-background/90"
            >
              Shop Now
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
