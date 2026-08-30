import type { Product } from '../types/api'

const SOURCE_COLORS: Record<string, string> = {
  Amazon: 'bg-orange-100 text-orange-800',
  Flipkart: 'bg-blue-100 text-blue-800',
  BigBasket: 'bg-lime-100 text-lime-800',
  Blinkit: 'bg-yellow-100 text-yellow-800',
}

function formatPerUnit(product: Product): string | null {
  if (product.price_per_unit === null || product.unit === null) return null
  return `\u20B9${product.price_per_unit.toFixed(2)}/${product.unit}`
}

export default function ProductCard({ product }: { product: Product }) {
  const perUnit = formatPerUnit(product)

  return (
    <a
      href={product.link || undefined}
      target="_blank"
      rel="noopener noreferrer"
      className={`group relative flex gap-3 rounded-xl bg-white p-3 shadow-sm ring-1 transition duration-150 hover:-translate-y-0.5 hover:shadow-md ${
        product.bestValue ? 'ring-2 ring-emerald-500' : 'ring-slate-200'
      }`}
    >
      {product.bestValue && (
        <span className="absolute -top-2.5 left-3 rounded-full bg-emerald-600 px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white shadow-sm">
          Best value
        </span>
      )}

      <div className="h-20 w-20 shrink-0 overflow-hidden rounded-lg bg-slate-50">
        {product.image_url ? (
          <img
            src={product.image_url}
            alt=""
            loading="lazy"
            className="h-full w-full object-contain"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-xl text-slate-300">
            &#128722;
          </div>
        )}
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <p className="line-clamp-2 text-sm font-medium text-slate-800 group-hover:text-brand-600">
          {product.title}
        </p>

        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
          <span
            className={`rounded-full px-2 py-0.5 font-semibold ${
              SOURCE_COLORS[product.source] ?? 'bg-slate-100 text-slate-700'
            }`}
          >
            {product.source}
          </span>
          {product.weight && <span className="text-slate-400">{product.weight}</span>}
        </div>

        <div className="mt-auto flex items-end justify-between gap-2 pt-2">
          <span className="text-base font-bold text-slate-900">
            {product.numericPrice !== null ? `\u20B9${product.price}` : 'Price unavailable'}
          </span>
          {perUnit && (
            <span
              className={`rounded-md px-1.5 py-0.5 text-xs font-semibold ${
                product.bestValue
                  ? 'bg-emerald-50 text-emerald-700'
                  : 'bg-slate-100 text-slate-600'
              }`}
            >
              {perUnit}
            </span>
          )}
        </div>
      </div>
    </a>
  )
}
