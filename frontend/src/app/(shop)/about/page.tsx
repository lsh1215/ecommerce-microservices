import Image from 'next/image';
import Link from 'next/link';

export const metadata = {
  title: 'About — FOUNDRY',
  description:
    'The story behind FOUNDRY: curated heritage menswear from Korea, Japan, and the USA.',
};

export default function AboutPage() {
  return (
    <div>
      {/* Hero */}
      <section className="relative h-[60vh] min-h-[400px] max-h-[600px] overflow-hidden bg-[#1a1a1a]">
        <Image
          src="https://images.unsplash.com/photo-1604644401890-0bd678c83788?w=1600&q=80"
          alt="Heritage craft workshop"
          fill
          priority
          className="object-cover opacity-50"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#1a1a1a]/80 via-transparent to-transparent" />
        <div className="absolute inset-0 flex flex-col justify-end px-6 py-12 md:px-12 md:py-16">
          <div className="mx-auto w-full max-w-3xl">
            <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#a39e93]">
              About
            </p>
            <h1 className="font-heading text-4xl font-bold leading-tight text-white md:text-6xl">
              Heritage wear should not be a treasure hunt.
            </h1>
          </div>
        </div>
      </section>

      {/* Story */}
      <section className="mx-auto max-w-3xl px-4 py-16 md:px-6 md:py-24">
        <h2 className="font-heading mb-8 text-3xl font-bold text-[#1a1a1a]">The FOUNDRY Story</h2>
        <div className="flex flex-col gap-6 text-base leading-relaxed text-[#6b6560]">
          <p>
            FOUNDRY started with a simple frustration: the best heritage menswear in the world is
            scattered across three countries, dozens of boutiques, and a tangle of proxy services.
            An Outstanding & Co. jacket from Seoul requires a Korean address. Warehouse denim from
            Kobe means navigating Japanese retail sites. RRL drops sell out in minutes on the other
            side of the planet.
          </p>
          <p>
            We built FOUNDRY to solve that. One platform. Three origins. Curated drops. No proxies,
            no translators, no missed restocks.
          </p>
        </div>
      </section>

      {/* What is heritage menswear */}
      <section className="border-t border-[#e8e4df] bg-[#f3f0eb]">
        <div className="mx-auto grid max-w-7xl grid-cols-1 md:grid-cols-2 md:items-center">
          <div className="px-6 py-16 md:px-12 md:py-24">
            <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
              The Category
            </p>
            <h2 className="font-heading mb-6 text-3xl font-bold text-[#1a1a1a]">
              What is Heritage Menswear?
            </h2>
            <div className="flex flex-col gap-4 text-sm leading-relaxed text-[#6b6560]">
              <p>
                Heritage menswear is clothing built on history. It draws from workwear, military
                surplus, and outdoor gear of the early-to-mid 20th century — garments designed for
                labor, exploration, and survival.
              </p>
              <p>
                These are not reproductions for nostalgia. They are studies in durability: selvedge
                denim woven on shuttle looms, waxed canvas that develops character over years,
                hardware forged to military spec. The fabrics tell you where they have been.
              </p>
              <p>
                Three countries dominate this space. Korea brings a fresh reinterpretation. Japan
                brings obsessive reproduction accuracy. America brings the originals. FOUNDRY brings
                all three together.
              </p>
            </div>
          </div>
          <div className="relative aspect-square overflow-hidden bg-[#e8e4df] md:aspect-auto md:h-full">
            <Image
              src="https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80"
              alt="Selvedge denim detail"
              fill
              className="object-cover"
              sizes="(max-width: 768px) 100vw, 50vw"
            />
          </div>
        </div>
      </section>

      {/* Why three origins */}
      <section className="mx-auto max-w-3xl px-4 py-16 md:px-6 md:py-24">
        <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
          Our Curation
        </p>
        <h2 className="font-heading mb-8 text-3xl font-bold text-[#1a1a1a]">Korea. Japan. USA.</h2>
        <div className="flex flex-col gap-6 text-sm leading-relaxed text-[#6b6560]">
          <p>
            <strong className="text-[#1a1a1a]">Korea</strong> — Brands like Outstanding & Co. take
            classic American silhouettes and filter them through Korean sensibility. The result is
            heritage wear that feels lighter, more refined, and slightly subversive. Korean makers
            are not bound by reproduction fidelity — they reinterpret.
          </p>
          <p>
            <strong className="text-[#1a1a1a]">Japan</strong> — Warehouse & Co. and Buzz
            Rickson&apos;s represent Japan&apos;s legendary reproduction culture. These makers study
            original garments with archaeological precision, sourcing deadstock fabrics and
            rebuilding vintage looms to achieve textures that modern manufacturing cannot replicate.
          </p>
          <p>
            <strong className="text-[#1a1a1a]">USA</strong> — RRL is the direct descendant of the
            workwear and frontier traditions that inspired every brand on this platform. It is the
            source material. Small-batch, ranch-inspired, and built with some of the finest American
            and Japanese mills.
          </p>
        </div>
      </section>

      {/* Mission */}
      <section className="border-t border-[#e8e4df] bg-[#1a1a1a]">
        <div className="mx-auto max-w-3xl px-4 py-16 text-center md:px-6 md:py-24">
          <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
            Our Mission
          </p>
          <h2 className="font-heading mb-6 text-3xl font-bold text-white md:text-4xl">
            Every drop is final sale. No restocks. That is the point.
          </h2>
          <p className="mx-auto max-w-xl text-sm leading-relaxed text-[#a39e93]">
            FOUNDRY exists to give heritage enthusiasts worldwide equal access to the brands that
            define this space. No geographic gatekeeping. No proxy fees. Just the best craftsmanship
            from three continents, delivered to your door.
          </p>
          <Link
            href="/drops"
            className="mt-10 inline-block bg-white px-8 py-3 text-sm font-semibold uppercase tracking-widest text-[#1a1a1a] transition-opacity hover:opacity-90"
          >
            View Current Drops
          </Link>
        </div>
      </section>
    </div>
  );
}
