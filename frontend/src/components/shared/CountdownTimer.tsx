'use client';

import { useCallback, useEffect, useRef, useSyncExternalStore } from 'react';

interface CountdownTimerProps {
  targetDate: string;
  onExpire?: () => void;
  className?: string;
}

interface TimeLeft {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  expired: boolean;
}

function computeTimeLeft(target: string): TimeLeft {
  const diff = new Date(target).getTime() - Date.now();
  if (diff <= 0) return { days: 0, hours: 0, minutes: 0, seconds: 0, expired: true };

  const totalSeconds = Math.floor(diff / 1000);
  return {
    days: Math.floor(totalSeconds / 86400),
    hours: Math.floor((totalSeconds % 86400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
    expired: false,
  };
}

function pad(n: number) {
  return String(n).padStart(2, '0');
}

const emptySubscribe = () => () => {};

export function CountdownTimer({ targetDate, onExpire, className = '' }: CountdownTimerProps) {
  const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);

  const onExpireRef = useRef(onExpire);
  const timeLeftRef = useRef<TimeLeft>(computeTimeLeft(targetDate));

  useEffect(() => {
    onExpireRef.current = onExpire;
  }, [onExpire]);

  const getSnapshot = useCallback(() => {
    const next = computeTimeLeft(targetDate);
    const prev = timeLeftRef.current;
    if (
      next.days !== prev.days ||
      next.hours !== prev.hours ||
      next.minutes !== prev.minutes ||
      next.seconds !== prev.seconds
    ) {
      timeLeftRef.current = next;
    }
    return timeLeftRef.current;
  }, [targetDate]);

  const timeLeft = useSyncExternalStore(
    useCallback((notify: () => void) => {
      const id = setInterval(() => {
        const next = computeTimeLeft(targetDate);
        timeLeftRef.current = next;
        notify();
        if (next.expired) {
          clearInterval(id);
          onExpireRef.current?.();
        }
      }, 1000);
      return () => clearInterval(id);
    }, [targetDate]),
    getSnapshot,
    () => computeTimeLeft(targetDate),
  );

  if (!mounted) return null;

  if (timeLeft.expired) {
    return <span className={`font-medium ${className}`}>ENDED</span>;
  }

  if (timeLeft.days > 0) {
    return (
      <span className={className}>
        {timeLeft.days}d {pad(timeLeft.hours)}h {pad(timeLeft.minutes)}m
      </span>
    );
  }

  if (timeLeft.hours > 0) {
    return (
      <span className={className}>
        {pad(timeLeft.hours)}:{pad(timeLeft.minutes)}:{pad(timeLeft.seconds)}
      </span>
    );
  }

  return (
    <span className={`font-semibold text-[#ef4444] ${className}`}>
      {pad(timeLeft.minutes)}:{pad(timeLeft.seconds)}
    </span>
  );
}
