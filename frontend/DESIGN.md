# E-Commerce Platform — Design System

## Brand Identity
- Name: (generic e-commerce, no specific brand name)
- Tone: Clean, modern, trustworthy
- Target: General consumers shopping for everyday products

## Color Palette
### Primary
- Primary: #2563eb (Blue 600) — CTAs, links, active states
- Primary Hover: #1d4ed8 (Blue 700)
- Primary Light: #dbeafe (Blue 100) — badges, backgrounds

### Neutral
- Text Primary: #111827 (Gray 900)
- Text Secondary: #6b7280 (Gray 500)
- Border: #e5e7eb (Gray 200)
- Background: #ffffff
- Surface: #f9fafb (Gray 50)

### Semantic
- Success: #16a34a (Green 600)
- Error: #dc2626 (Red 600)
- Warning: #d97706 (Amber 600)
- Info: #2563eb (Blue 600)

## Typography
- Font: Inter (sans-serif system font stack)
- Headings: font-semibold
- Body: font-normal, text-sm (14px) or text-base (16px)

## Spacing Scale
- Tailwind default (4px base): 1=4px, 2=8px, 3=12px, 4=16px, 6=24px, 8=32px

## Component Library
- shadcn/ui as base component library
- All components use Tailwind CSS utility classes

## Page List
1. Home (/) — Hero + Featured Products + Categories
2. Products (/products) — Grid with filters (category, price, brand)
3. Product Detail (/products/[id]) — Images, variants, add to cart
4. Cart (/cart) — Item list, quantity, total
5. Checkout (/checkout) — Shipping + Payment form
6. Order Confirmation (/orders/[id]/confirmation)
7. Auth (/auth) — Login / Register tabs
8. My Page (/account) — Order history, profile, addresses
