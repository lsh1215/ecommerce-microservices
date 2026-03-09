import type { Brand } from '@/types';

export const mockBrands: Brand[] = [
  {
    id: 'brand-outstanding',
    slug: 'outstanding',
    name: 'Outstanding & Co.',
    nameKo: '아웃스탠딩 앤 컴퍼니',
    origin: 'Korea',
    description:
      'Seoul-based label reinterpreting American vintage outdoor through a Korean lens. Founded 2015.',
    fullDescription:
      'Outstanding & Co. was born in Seoul in 2015, driven by a singular obsession: reinterpreting classic American outdoor and workwear through the lens of Korean craft. Every piece begins with vintage archive research, then gets rebuilt using Korean-sourced fabrics and construction techniques that honor the original while adding something new. The result is clothing that feels authentically heritage but carries a distinctly Korean sensibility — subtle, restrained, and built to last.',
    imageUrl: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80',
    logoUrl: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=200&q=80',
    featured: true,
    foundedYear: 2015,
    styleCategory: 'Vintage Outdoor',
  },
  {
    id: 'brand-warehouse',
    slug: 'warehouse',
    name: 'Warehouse & Co.',
    nameJa: 'ウエアハウス',
    origin: 'Japan',
    description:
      'Kobe-based reproduction house obsessed with 1950s American workwear. Masters of Banner Denim.',
    fullDescription:
      'Warehouse & Co. has operated from Kobe since 1995, dedicated to one mission: reproducing American workwear and denim from the 1940s and 1950s with obsessive accuracy. Their Banner Denim — woven on restored vintage shuttle looms — has become legendary among denim enthusiasts worldwide. Every detail is researched from original garments: the irregular slub of the cotton, the shape of the rivets, the exact shade of indigo. They do not innovate. They perfect.',
    imageUrl: 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&q=80',
    logoUrl: 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=200&q=80',
    featured: true,
    foundedYear: 1995,
    styleCategory: 'Reproduction Denim',
  },
  {
    id: 'brand-rrl',
    slug: 'rrl',
    name: 'RRL',
    origin: 'USA',
    description:
      "Ralph Lauren's premium Americana line. Small-batch drops inspired by frontier heritage and rodeo culture.",
    fullDescription:
      "RRL (Double RL) is Ralph Lauren's most personal line, named after his ranch in Telluride, Colorado. Since 1993, RRL has drawn on the American frontier — ranches, rodeos, and the open West — to create small-batch clothing that feels like it has a story already woven in. Each season is deliberately limited. The fabrics are sourced from the finest Japanese and American mills. RRL does not chase trends; it builds a world you want to live in.",
    imageUrl: 'https://images.unsplash.com/photo-1604644401890-0bd678c83788?w=800&q=80',
    logoUrl: 'https://images.unsplash.com/photo-1604644401890-0bd678c83788?w=200&q=80',
    featured: true,
    foundedYear: 1993,
    styleCategory: 'Western Americana',
  },
  {
    id: 'brand-buzz-ricksons',
    slug: 'buzz-ricksons',
    name: "Buzz Rickson's",
    nameJa: 'バズリクソンズ',
    origin: 'Japan',
    description:
      'Precision reproductions of US military flight jackets from the 1940s–1960s. Authenticity unmatched.',
    fullDescription:
      "Buzz Rickson's has spent over three decades reproducing US military flight jackets with a precision that borders on archaeological. Based in Tokyo, they source original military spec sheets, track down deadstock fabrics, and rebuild hardware from scratch. Each jacket is a thesis on a specific military contract — the MA-1, the B-15, the N-1 deck jacket. They have earned the respect of collectors and military historians alike. If the original factory could see these reproductions, they would not be able to tell the difference.",
    imageUrl: 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&q=80',
    logoUrl: 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=200&q=80',
    featured: false,
    foundedYear: 1991,
    styleCategory: 'Military Reproduction',
  },
];

export function getBrandBySlug(slug: string): Brand | undefined {
  return mockBrands.find((b) => b.slug === slug);
}
