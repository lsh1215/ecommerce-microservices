interface PriceDisplayProps {
  amount: number;
  className?: string;
}

export function PriceDisplay({ amount, className = '' }: PriceDisplayProps) {
  const formatted = new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(amount);

  return <span className={className}>{formatted}</span>;
}
