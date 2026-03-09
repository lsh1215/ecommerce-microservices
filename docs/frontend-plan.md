# FOUNDRY Frontend Page Plan

## Tech Stack

- Next.js 16 (App Router), React 19, TypeScript
- TanStack Query (server state), Zustand (client state)
- Tailwind CSS 4, Radix UI primitives (dialog, dropdown, tabs, toast)
- react-hook-form + zod (forms)
- nuqs (URL search params state)
- dayjs (countdown timers, timezone-aware formatting)

## Design Direction: Editorial Minimalism with Craft Texture

### Design Tokens

| Token | Value |
|-------|-------|
| **Font heading** | Playfair Display (serif) |
| **Font body** | DM Sans (sans-serif) |
| **Color base** | Off-white `#FAF9F6`, Warm gray `#E8E4DF`, Stone `#A39E93` |
| **Color text** | Primary `#1A1A1A`, Secondary `#6B6560` |
| **Color accent** | Rust `#C4633E` (CTAs, drop live badge) |
| **Color status** | Blue `#3B82F6` (coming soon), Red `#EF4444` (live), Gray `#9CA3AF` (ended/sold out) |
| **Spacing scale** | 4px base (4, 8, 12, 16, 24, 32, 48, 64, 96) |
| **Border radius** | 0px (cards), 4px (buttons, inputs) — sharp aesthetic for heritage feel |
| **Max content width** | 1280px (`max-w-7xl`) |
| **Body font size** | 16px min |
| **Line height** | 1.6 body, 1.2 headings |

### Visual Principles

- Full-bleed product imagery with generous whitespace
- Muted earth tones; only drop status badges use saturated color
- Urgency communicated through typography weight, not flashing animations
- Sharp corners — no rounded cards (heritage/craft aesthetic)
- Dark text on light backgrounds; no dark mode for MVP

## Navigation

### Desktop Header
- Left: FOUNDRY wordmark (serif, link to `/`)
- Center: Drops, Brands, Products
- Right: Search icon (overlay), Currency (KRW/USD/JPY), Cart badge, Login/Avatar

### Mobile
- Top: FOUNDRY wordmark + Cart icon
- Bottom tab bar (fixed): Home, Drops, Search, Cart, Account
- Filter drawer (sheet from bottom) for product listing

### Announcement Bar
- Above header when a drop is SELLING: "LIVE NOW: [Drop Name] — [countdown]"

## Pages

### P0 — MVP (9 pages)

#### 1. Home `/`

| Section | Description |
|---------|-------------|
| Hero | Active/upcoming drop with countdown timer, hero image, "Shop Drop" CTA |
| Live Drops | Horizontal scroll cards — drop name, brand, countdown, items remaining |
| Featured Brands | 3 cards (Outstanding, Warehouse, RRL) with origin flag, one-line description |
| New Arrivals | 8-product grid, currency-aware pricing |
| Editorial Block | FOUNDRY curation philosophy — static content |

**API**: `GET /api/drops?status=SELLING,ANNOUNCED&limit=4`, `GET /api/brands?featured=true`, `GET /api/products?sort=createdAt,desc&size=8`

#### 2. Product Listing `/products` (also handles `/products?q=searchterm`)

Search and catalog browsing on a single page. Search query appears as a filter chip.

| Section | Description |
|---------|-------------|
| Search bar | Persistent at top, current query shown |
| Filter sidebar (desktop) / Filter drawer (mobile) | Brand, Origin, Category, Fabric Weight range, Era, Price range, Drop Status |
| Active filters bar | Removable chips |
| Sort controls | Price asc/desc, Newest, Brand A-Z |
| Product grid | Image, brand, name, price, drop badge if applicable |
| Pagination | Page-based |

**Filters**: All sync to URL params via `nuqs` for shareable links.
**API**: `GET /api/products?q=&brand=&origin=&category=&fabricWeightMin=&fabricWeightMax=&era=&priceMin=&priceMax=&dropStatus=&sort=&page=&size=24`

**Mobile**: Filter drawer slides up from bottom. 44px min touch targets for filter options.

#### 3. Product Detail `/products/[id]`

| Section | Description |
|---------|-------------|
| Image gallery | Main + thumbnails (desktop). Swipe carousel (mobile) |
| Product info | Brand (linked), name, price (selected currency + original if different), origin flag |
| Fabric details | Type, weight (oz), weave, era |
| Size selector | Brand-specific labels, stock indicator ("Low Stock" <=3, "Sold Out" =0) |
| Measurements table | Per-size garment measurements in cm (chest, shoulder, sleeve, length) — collapsible |
| Drop banner | If part of active drop: name, status, countdown |
| Add to Cart | Disabled if sold out. "DROP STARTS [countdown]" if ANNOUNCED |
| Related products | 4 products from same brand/category |

**Mobile**: Image swipe carousel. Measurements table horizontal-scrollable. Sticky "Add to Cart" bottom bar.

#### 4. Drops Hub `/drops`

| Section | Description |
|---------|-------------|
| Live Now | Prominent cards — hero image, brand, countdown to close, items remaining, "Shop Now" |
| Coming Soon | ANNOUNCED drops with countdown to open, "Notify Me" (stubbed) |
| Past Drops | Archive grid — drop name, brand, date, "SOLD OUT" / "ENDED" badge |

**API**: `GET /api/drops?status=SELLING,OPEN`, `GET /api/drops?status=ANNOUNCED`, `GET /api/drops?status=CLOSED,SOLD_OUT&size=10`

#### 5. Drop Detail `/drops/[id]`

The action page during a live drop. Optimized for speed.

| Section | Description |
|---------|-------------|
| Drop header | Name, brand, hero image, status badge |
| Countdown bar | Sticky. ANNOUNCED: "Opens in X". SELLING: "Closes in X" + items remaining |
| Product grid | All drop products. Inline quick-add: size buttons on card → add to cart → toast |
| Sold out products | Pushed to bottom, grayed overlay |
| Drop info | Return policy, shipping timeline |

**Quick-Add UX (mobile)**: Tap product card → bottom sheet with sizes → select → "Added!" toast → sheet closes.
**Polling**: `GET /api/drops/[id]/summary` (drop status + per-product stock counts, single endpoint) via TanStack Query `refetchInterval: 5000` (paused when `document.hidden`).
**T=0 transition**: Polling detects status change → UI auto-updates from ANNOUNCED→SELLING or SELLING→SOLD_OUT/CLOSED.

#### 6. Cart `/cart`

| Section | Description |
|---------|-------------|
| Cart items | Image, brand, name, size, price, qty, remove button |
| Stock warnings | "Only 2 left" or "Sold Out — remove from cart" (validated on page load) |
| Drop timer | If items from active drop: "Drop closes in X — complete your purchase" |
| Not-reserved notice | "Items are not reserved until order is placed" (always shown during active drops) |
| Summary | Subtotal, estimated duty (flat rate), total in selected currency |
| Checkout CTA | Disabled if empty or contains sold-out items |

**Stock validation**: `POST /api/cart/validate` on page load — batch check stock for all cart items.
**Mobile**: Full-width layout, sticky checkout button at bottom.

#### 7. Checkout `/checkout`

| Section | Description |
|---------|-------------|
| Shipping form | Name, phone, address, country. react-hook-form + zod validation |
| Order summary | Collapsible on mobile. Items, duty estimate, total |
| Payment | Stubbed radio options (credit card, bank transfer) |
| Place Order | Loading state during submission. Error: stock depleted / payment failed with clear messaging |

**On success**: Redirect to `/orders/[id]/confirmation`.
**Partial stock failure**: Backend rejects entire order. Frontend shows which items are unavailable.

#### 8. Order Confirmation `/orders/[id]/confirmation`

| Section | Description |
|---------|-------------|
| Success | Checkmark, "Order Placed Successfully" |
| Drop celebration | If drop purchase: "You secured [item] from [Drop Name]" — subtle emphasis |
| Summary | Order number, items, total, estimated processing time |
| CTAs | "View Order" → `/orders/[id]`, "Continue Shopping" → `/` |

#### 9. Auth `/auth` (Login + Register on single page with tab toggle)

| Section | Description |
|---------|-------------|
| Tab: Login | Email + password, submit, error display |
| Tab: Register | Name, email, password, confirm password |
| After auth | Redirect to previous page or home. JWT in httpOnly cookie |

### P1 — Post-MVP (5 pages)

| Page | URL | Purpose |
|------|-----|---------|
| Brand Index | `/brands` | Brand cards with origin filter (Korea/Japan/USA) |
| Brand Detail | `/brands/[slug]` | Brand story, active drops, product grid |
| Size Guide | `/size-guide` | How to measure yourself, brand sizing overview |
| Profile | `/profile` | Currency preference, saved addresses |
| Order History + Detail | `/orders`, `/orders/[id]` | Past orders, status timeline, cancel action |

### P2 — Stretch

| Page | URL |
|------|-----|
| Size Comparison Tool | `/size-compare` |
| Shipping & Returns | `/info/shipping` |
| About | `/about` |

## Route Groups (Next.js App Router)

| Group | Pages | Layout |
|-------|-------|--------|
| `(shop)` | products, drops, brands, search, cart, checkout, size-guide, confirmation | Full header + footer |
| `(account)` | orders, profile | Header + account sidebar |
| `(auth)` | auth | Minimal — centered form, no nav |
| Root `/` | home | Hero-first layout |

## Cross-Cutting UX Patterns

### Countdown Timer
- Server timestamp based (response header `X-Server-Time`)
- Calculate drift offset on app load, apply to all countdowns
- States: days+hours (far) → hours+minutes (same day) → minutes+seconds (<1hr) → "LIVE NOW" → "ENDED"
- Client-only component (`'use client'` + `useEffect`) to avoid hydration mismatch

### Stock Urgency
- "Last 3" badge when stock <= 3
- "Sold Out" grayed overlay when stock = 0
- Stale-stock add-to-cart error: toast "This size just sold out" + disable button

### Loading States
- Skeleton screens for product grids and drop cards
- Suspense boundaries per route segment
- Loading button state during add-to-cart and checkout

### Empty States
- Empty cart: illustration + "Start browsing" CTA
- No search results: "No products found" + suggestion to browse all
- No orders: "You haven't placed any orders yet"

### Error Handling
- 404 page: styled with FOUNDRY branding
- Error boundary: "Something went wrong" with retry
- API errors: toast notifications for transient, inline for form errors

## Mobile Patterns

| Pattern | Implementation |
|---------|---------------|
| Filter drawer | Bottom sheet sliding up, full-height on mobile |
| Image gallery | Swipe carousel with dot indicators |
| Quick-add (drop page) | Bottom sheet: product image + size buttons + add CTA |
| Sticky elements | Add-to-cart bar on PDP, checkout button on cart, countdown on drop detail |
| Touch targets | 44px minimum on all interactive elements |
| Polling pause | `document.hidden` check — stop polling when tab is backgrounded |

## Cart Architecture

- **Client-side**: Zustand + localStorage persistence
- **Schema versioning**: `cartVersion` field — on mismatch, reset cart with migration toast
- **Stock validation**: `POST /api/cart/validate` called on cart page load AND before `POST /api/orders`
- **Logout behavior**: Cart persists (it's anonymous). On login, no merge (server cart is out of scope)

## API Contract Requirements (for Backend)

| Endpoint | Notes |
|----------|-------|
| Products return all 3 currency prices | `{ priceKrw, priceUsd, priceJpy }` |
| Drop summary endpoint | `GET /api/drops/{id}/summary` — lightweight status + stock counts |
| Cart validation | `POST /api/cart/validate` — batch stock check |
| Server timestamp header | `X-Server-Time` on all responses for countdown sync |
| Filter param names | `brand`, `origin`, `category`, `fabricWeightMin`, `fabricWeightMax`, `era`, `priceMin`, `priceMax`, `dropStatus`, `sort`, `page`, `size` |
