import type { LiquidityTier } from '../types'

interface LiquidityScoreBarProps {
  tier: LiquidityTier
  horizonDays: number
  advMissing?: boolean
  advStale?: boolean
}

const TIER_CONFIG: Record<
  LiquidityTier,
  { label: string; barColor: string; textColor: string; filledSegments: number }
> = {
  HIGH_LIQUID: {
    label: 'HIGH LIQUID',
    barColor: 'bg-green-500',
    textColor: 'text-green-700 dark:text-green-400',
    filledSegments: 4,
  },
  LIQUID: {
    label: 'LIQUID',
    barColor: 'bg-blue-500',
    textColor: 'text-blue-700 dark:text-blue-400',
    filledSegments: 3,
  },
  SEMI_LIQUID: {
    label: 'SEMI LIQUID',
    barColor: 'bg-yellow-500',
    textColor: 'text-yellow-700 dark:text-yellow-400',
    filledSegments: 2,
  },
  ILLIQUID: {
    label: 'ILLIQUID',
    barColor: 'bg-red-500',
    textColor: 'text-red-700 dark:text-red-400',
    filledSegments: 1,
  },
}

export function LiquidityScoreBar({
  tier,
  horizonDays,
  advMissing = false,
  advStale = false,
}: LiquidityScoreBarProps) {
  const config = TIER_CONFIG[tier]

  return (
    <div
      data-testid="liquidity-score-bar"
      data-tier={tier}
      className="flex flex-col gap-1"
    >
      <div className="flex items-center justify-between">
        <span
          data-testid="tier-label"
          className={`text-xs font-semibold ${config.textColor}`}
        >
          {config.label}
        </span>
        <span
          data-testid="horizon-days"
          className="text-xs text-gray-500 dark:text-gray-400"
        >
          {horizonDays}d horizon
        </span>
      </div>

      <div className="flex gap-0.5">
        {[1, 2, 3, 4].map((segment) => (
          <div
            key={segment}
            className={`h-2 flex-1 rounded-sm ${
              segment <= config.filledSegments
                ? config.barColor
                : 'bg-gray-200 dark:bg-gray-700'
            }`}
          />
        ))}
      </div>

      {advMissing && (
        <span
          data-testid="adv-missing-warning"
          className="text-xs text-red-600 dark:text-red-400"
        >
          ADV data missing
        </span>
      )}

      {advStale && !advMissing && (
        <span
          data-testid="adv-stale-warning"
          className="text-xs text-yellow-600 dark:text-yellow-400"
        >
          ADV data stale
        </span>
      )}
    </div>
  )
}
