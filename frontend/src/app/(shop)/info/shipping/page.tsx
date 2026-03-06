import Link from 'next/link';

export const metadata = {
  title: 'Shipping & Returns — FOUNDRY',
  description: 'Shipping timelines, international duties, and return policies.',
};

const SHIPPING_REGIONS = [
  { region: 'South Korea', timeline: '2–3 business days', duty: 'No duty' },
  { region: 'Japan', timeline: '5–7 business days', duty: 'Flat rate ¥1,500' },
  { region: 'United States', timeline: '7–14 business days', duty: 'Flat rate $15' },
  { region: 'EU / UK', timeline: '10–18 business days', duty: 'Varies by country' },
  { region: 'Rest of World', timeline: '14–21 business days', duty: 'Varies by country' },
];

const RETURN_STEPS = [
  'Contact us within 14 days of delivery via support@foundry.com.',
  'Receive a return authorization and prepaid shipping label.',
  'Pack the item in its original packaging, unworn and with tags attached.',
  'Drop off the package at the designated carrier location.',
  'Refund processed within 5–7 business days of receiving the return.',
];

export default function ShippingPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-12 md:px-6">
      <div className="mb-12">
        <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
          Information
        </p>
        <h1 className="font-heading text-3xl font-bold text-[#1a1a1a] md:text-4xl">
          Shipping & Returns
        </h1>
      </div>

      {/* Shipping */}
      <section className="mb-16">
        <h2 className="font-heading mb-6 text-2xl font-bold text-[#1a1a1a]">Shipping</h2>
        <p className="mb-6 text-sm leading-relaxed text-[#6b6560]">
          All orders ship from our warehouse in Seoul, South Korea. Delivery timelines
          below are estimates from the date of shipment, not the date of order.
        </p>

        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b-2 border-[#1a1a1a]">
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]">
                  Region
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]">
                  Estimated Delivery
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]">
                  Duties & Taxes
                </th>
              </tr>
            </thead>
            <tbody>
              {SHIPPING_REGIONS.map((r) => (
                <tr key={r.region} className="border-b border-[#e8e4df]">
                  <td className="px-4 py-3 text-sm font-medium text-[#1a1a1a]">
                    {r.region}
                  </td>
                  <td className="px-4 py-3 text-sm text-[#6b6560]">{r.timeline}</td>
                  <td className="px-4 py-3 text-sm text-[#6b6560]">{r.duty}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-6 border border-[#e8e4df] bg-[#fdf7f4] p-5">
          <p className="text-sm font-medium text-[#1a1a1a]">International Duties Note</p>
          <p className="mt-2 text-sm leading-relaxed text-[#6b6560]">
            International orders may be subject to import duties and taxes imposed by the
            destination country. FOUNDRY provides flat rate duty estimates at checkout for
            Japan and the United States. For all other regions, duties are collected upon
            delivery and are the responsibility of the buyer.
          </p>
        </div>
      </section>

      {/* Returns */}
      <section className="mb-16">
        <h2 className="font-heading mb-6 text-2xl font-bold text-[#1a1a1a]">Returns</h2>
        <p className="mb-6 text-sm leading-relaxed text-[#6b6560]">
          We accept returns on regular (non-drop) items within 14 days of delivery. Items must
          be unworn, unwashed, and in their original packaging with all tags attached.
        </p>

        <div className="mb-8">
          <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
            Return Process
          </p>
          <ol className="flex flex-col gap-3">
            {RETURN_STEPS.map((step, idx) => (
              <li key={idx} className="flex gap-3 text-sm text-[#6b6560]">
                <span className="flex h-6 w-6 shrink-0 items-center justify-center bg-[#1a1a1a] text-xs font-bold text-white">
                  {idx + 1}
                </span>
                <span className="leading-relaxed">{step}</span>
              </li>
            ))}
          </ol>
        </div>

        {/* Drop items policy */}
        <div className="border-l-4 border-[#c4633e] bg-[#fdf7f4] p-5">
          <p className="text-sm font-semibold text-[#1a1a1a]">Drop Items — Final Sale</p>
          <p className="mt-2 text-sm leading-relaxed text-[#6b6560]">
            All items purchased through timed drops are <strong>final sale</strong>. No
            returns, exchanges, or refunds. This is clearly indicated on each drop page
            and at checkout. Drop items represent limited production runs that cannot be
            restocked — this is the nature of the format.
          </p>
        </div>
      </section>

      {/* Exchanges */}
      <section className="mb-16">
        <h2 className="font-heading mb-6 text-2xl font-bold text-[#1a1a1a]">Exchanges</h2>
        <p className="text-sm leading-relaxed text-[#6b6560]">
          We do not offer direct exchanges. To get a different size or product, please
          initiate a return for the original item and place a new order. Use our{' '}
          <Link href="/size-guide" className="text-[#c4633e] underline underline-offset-4">
            Size Guide
          </Link>{' '}
          to find the right fit before ordering.
        </p>
      </section>

      {/* Contact */}
      <section>
        <h2 className="font-heading mb-6 text-2xl font-bold text-[#1a1a1a]">
          Need Help?
        </h2>
        <p className="text-sm leading-relaxed text-[#6b6560]">
          Reach out to us at{' '}
          <a
            href="mailto:support@foundry.com"
            className="font-medium text-[#c4633e] underline underline-offset-4"
          >
            support@foundry.com
          </a>
          . We respond within 24 hours on business days.
        </p>
      </section>
    </div>
  );
}
