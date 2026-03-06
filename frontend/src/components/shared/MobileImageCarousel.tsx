'use client';

import { useRef, useState, useEffect, useCallback } from 'react';
import Image from 'next/image';

interface MobileImageCarouselProps {
  images: string[];
  alt: string;
}

export function MobileImageCarousel({ images, alt }: MobileImageCarouselProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [activeIndex, setActiveIndex] = useState(0);

  const handleScroll = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    const scrollLeft = el.scrollLeft;
    const width = el.clientWidth;
    const index = Math.round(scrollLeft / width);
    setActiveIndex(index);
  }, []);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  if (images.length === 0) return null;

  return (
    <div className="relative">
      <div ref={containerRef} className="no-scrollbar flex snap-x snap-mandatory overflow-x-auto">
        {images.map((url, i) => (
          <div key={i} className="relative aspect-[3/4] w-full shrink-0 snap-center bg-[#e8e4df]">
            <Image
              src={url}
              alt={`${alt} ${i + 1}`}
              fill
              priority={i === 0}
              className="object-cover"
              sizes="100vw"
            />
          </div>
        ))}
      </div>

      {images.length > 1 && (
        <div className="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-1.5">
          {images.map((_, i) => (
            <span
              key={i}
              className={`h-1.5 w-1.5 rounded-full transition-colors ${
                i === activeIndex ? 'bg-[#1a1a1a]' : 'bg-[#1a1a1a]/30'
              }`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
