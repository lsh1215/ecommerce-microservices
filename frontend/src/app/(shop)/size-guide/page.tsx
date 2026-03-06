import { mockBrands } from '@/mocks';

export const metadata = {
  title: 'Size Guide — FOUNDRY',
  description: 'How to measure yourself and brand sizing comparison for heritage menswear.',
};

const MEASUREMENT_POINTS = [
  {
    name: 'Chest',
    key: 'chest',
    description: 'Measure around the fullest part of your chest, keeping the tape level under your arms.',
  },
  {
    name: 'Shoulder',
    key: 'shoulder',
    description: 'Measure from one shoulder seam to the other across the back.',
  },
  {
    name: 'Sleeve',
    key: 'sleeve',
    description: 'From shoulder seam to cuff, with arm slightly bent.',
  },
  {
    name: 'Length',
    key: 'length',
    description: 'From the highest point of the shoulder seam to the hem.',
  },
  {
    name: 'Waist',
    key: 'waist',
    description: 'Measure around your natural waistline, above the hip bones.',
  },
  {
    name: 'Inseam',
    key: 'inseam',
    description: 'From the crotch seam to the bottom of the leg opening.',
  },
];

const TOPS_SIZING = {
  headers: ['Size', 'Outstanding & Co.', 'Warehouse & Co.', 'RRL'],
  rows: [
    {
      size: 'S / 1 / 14',
      values: [
        'Chest 52, Shoulder 43, Length 62',
        'Chest 50, Shoulder 42, Length 61',
        'Chest 51, Shoulder 43, Length 63',
      ],
    },
    {
      size: 'M / 2 / 15',
      values: [
        'Chest 54, Shoulder 44, Length 63',
        'Chest 53, Shoulder 44, Length 63',
        'Chest 54, Shoulder 45, Length 65',
      ],
    },
    {
      size: 'L / 3 / 16',
      values: [
        'Chest 56, Shoulder 45, Length 64',
        'Chest 56, Shoulder 46, Length 65',
        'Chest 57, Shoulder 47, Length 67',
      ],
    },
    {
      size: 'XL / 4 / 17',
      values: [
        'Chest 58, Shoulder 46, Length 65',
        'Chest 59, Shoulder 48, Length 67',
        'Chest 60, Shoulder 49, Length 69',
      ],
    },
  ],
};

const BOTTOMS_SIZING = {
  headers: ['Waist', 'Outstanding & Co.', 'Warehouse & Co.', 'RRL'],
  rows: [
    {
      size: '30',
      values: [
        'Waist 78, Inseam 80, Thigh 31',
        'Waist 78, Inseam 83, Thigh 32',
        'Waist 79, Inseam 81, Thigh 31',
      ],
    },
    {
      size: '32',
      values: [
        'Waist 82, Inseam 80, Thigh 32',
        'Waist 82, Inseam 83, Thigh 33',
        'Waist 83, Inseam 81, Thigh 32',
      ],
    },
    {
      size: '34',
      values: [
        'Waist 86, Inseam 80, Thigh 33',
        'Waist 86, Inseam 83, Thigh 34',
        'Waist 87, Inseam 81, Thigh 33',
      ],
    },
  ],
};

export default function SizeGuidePage() {
  const featuredBrands = mockBrands.filter((b) => b.featured);

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 md:px-6">
      <div className="mb-12">
        <h1 className="font-heading text-3xl font-bold text-[#1a1a1a]">Size Guide</h1>
        <p className="mt-2 text-sm text-[#6b6560]">
          All measurements are in centimeters (cm) and refer to garment dimensions, not body measurements.
        </p>
      </div>

      {/* How to Measure */}
      <section className="mb-16">
        <h2 className="font-heading mb-6 text-2xl font-bold text-[#1a1a1a]">
          How to Measure
        </h2>
        <p className="mb-8 max-w-2xl text-sm leading-relaxed text-[#6b6560]">
          For the most accurate fit, measure a garment you already own and compare those
          measurements to the size charts below. Lay the garment flat and measure point to point.
        </p>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {MEASUREMENT_POINTS.map((point, idx) => (
            <div key={point.key} className="border border-[#e8e4df] p-5">
              <div className="mb-3 flex items-center gap-3">
                <span className="flex h-7 w-7 items-center justify-center bg-[#1a1a1a] text-xs font-bold text-white">
                  {idx + 1}
                </span>
                <h3 className="text-sm font-semibold text-[#1a1a1a]">{point.name}</h3>
              </div>
              <p className="text-sm leading-relaxed text-[#6b6560]">{point.description}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Brand Sizing Overview — Tops */}
      <section className="mb-16">
        <h2 className="font-heading mb-2 text-2xl font-bold text-[#1a1a1a]">
          Tops Sizing Comparison
        </h2>
        <p className="mb-6 text-sm text-[#6b6560]">
          Measurements in cm. Sizing labels vary by brand — see equivalents below.
        </p>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] border-collapse">
            <thead>
              <tr className="border-b-2 border-[#1a1a1a]">
                {TOPS_SIZING.headers.map((h) => (
                  <th
                    key={h}
                    className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {TOPS_SIZING.rows.map((row) => (
                <tr key={row.size} className="border-b border-[#e8e4df]">
                  <td className="px-4 py-3 text-sm font-medium text-[#1a1a1a]">
                    {row.size}
                  </td>
                  {row.values.map((val, i) => (
                    <td key={i} className="px-4 py-3 text-sm text-[#6b6560]">
                      {val}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Brand Sizing Overview — Bottoms */}
      <section className="mb-16">
        <h2 className="font-heading mb-2 text-2xl font-bold text-[#1a1a1a]">
          Bottoms Sizing Comparison
        </h2>
        <p className="mb-6 text-sm text-[#6b6560]">
          Measurements in cm. Inseam and thigh measurements vary by cut.
        </p>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] border-collapse">
            <thead>
              <tr className="border-b-2 border-[#1a1a1a]">
                {BOTTOMS_SIZING.headers.map((h) => (
                  <th
                    key={h}
                    className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[#1a1a1a]"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {BOTTOMS_SIZING.rows.map((row) => (
                <tr key={row.size} className="border-b border-[#e8e4df]">
                  <td className="px-4 py-3 text-sm font-medium text-[#1a1a1a]">
                    {row.size}
                  </td>
                  {row.values.map((val, i) => (
                    <td key={i} className="px-4 py-3 text-sm text-[#6b6560]">
                      {val}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Brand notes */}
      <section>
        <h2 className="font-heading mb-6 text-2xl font-bold text-[#1a1a1a]">
          Brand Sizing Notes
        </h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {featuredBrands.map((brand) => (
            <div key={brand.id} className="border border-[#e8e4df] p-5">
              <h3 className="font-heading text-lg font-bold text-[#1a1a1a]">
                {brand.name}
              </h3>
              <p className="mt-1 text-xs font-medium uppercase tracking-wider text-[#6b6560]">
                {brand.origin}
              </p>
              <p className="mt-3 text-sm leading-relaxed text-[#6b6560]">
                {brand.slug === 'outstanding' &&
                  'Uses numeric sizing (1-4). Fits true to size with a slightly relaxed silhouette. Size 2 is equivalent to a Western M.'}
                {brand.slug === 'warehouse' &&
                  'Uses traditional sizing (S-XL) for tops and inch sizing for denim. Reproduction fit — slightly smaller than modern brands. Consider sizing up.'}
                {brand.slug === 'rrl' &&
                  'Uses standard US sizing (S-XL) for tops and inch sizing for bottoms. Generally true to size with a worn-in feel.'}
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
