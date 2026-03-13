import { Card } from './ui'
import { formatNum } from '../utils/format'
import type { ComponentDiffDto } from '../types'

interface ComponentDiffChartProps {
  diffs: ComponentDiffDto[]
}

const BAR_COLORS = {
  base: {
    fill: 'fill-blue-400 dark:fill-blue-500',
    text: 'text-blue-600 dark:text-blue-400',
  },
  target: {
    fill: 'fill-indigo-400 dark:fill-indigo-500',
    text: 'text-indigo-600 dark:text-indigo-400',
  },
}

const BAR_HEIGHT = 20
const LABEL_WIDTH = 100
const CHART_WIDTH = 400
const ROW_GAP = 28 // space between base+target pair and next asset class
const PAIR_GAP = 2 // gap between base and target bar within a pair

export function ComponentDiffChart({ diffs }: ComponentDiffChartProps) {
  if (diffs.length === 0) return null

  const maxVal = Math.max(
    ...diffs.flatMap((d) => [
      Math.abs(Number(d.baseContribution)),
      Math.abs(Number(d.targetContribution)),
    ]),
    1,
  )

  const barAreaWidth = CHART_WIDTH - LABEL_WIDTH - 20
  const rowHeight = BAR_HEIGHT * 2 + PAIR_GAP + ROW_GAP
  const chartHeight = diffs.length * rowHeight + 10

  return (
    <Card data-testid="component-diff-chart">
      <div className="space-y-2">
        <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
          Component Breakdown
        </h3>
        <div className="flex gap-4 text-xs mb-2" aria-hidden="true">
          <span className={BAR_COLORS.base.text}>Base</span>
          <span className={BAR_COLORS.target.text}>Target</span>
        </div>
        <svg
          width="100%"
          viewBox={`0 0 ${CHART_WIDTH} ${chartHeight}`}
          className="overflow-visible"
          role="img"
          aria-label="Component breakdown comparison chart"
        >
          {diffs.map((d, i) => {
            const y = i * rowHeight + 10
            const baseWidth = (Math.abs(Number(d.baseContribution)) / maxVal) * barAreaWidth
            const targetWidth = (Math.abs(Number(d.targetContribution)) / maxVal) * barAreaWidth

            return (
              <g key={d.assetClass}>
                <text
                  x={0}
                  y={y + 12}
                  className="fill-slate-600 dark:fill-slate-300"
                  fontSize="11"
                >
                  {d.assetClass}
                </text>

                {/* Base bar */}
                <rect
                  x={LABEL_WIDTH}
                  y={y}
                  width={baseWidth}
                  height={BAR_HEIGHT}
                  rx={3}
                  className={BAR_COLORS.base.fill}
                  aria-label={`Base ${d.assetClass}: ${formatNum(d.baseContribution)}`}
                />
                <text
                  x={LABEL_WIDTH + baseWidth + 4}
                  y={y + 14}
                  className="fill-slate-500 dark:fill-slate-400"
                  fontSize="10"
                >
                  {formatNum(d.baseContribution)}
                </text>

                {/* Target bar */}
                <rect
                  x={LABEL_WIDTH}
                  y={y + BAR_HEIGHT + PAIR_GAP}
                  width={targetWidth}
                  height={BAR_HEIGHT}
                  rx={3}
                  className={BAR_COLORS.target.fill}
                  aria-label={`Target ${d.assetClass}: ${formatNum(d.targetContribution)}`}
                />
                <text
                  x={LABEL_WIDTH + targetWidth + 4}
                  y={y + BAR_HEIGHT + PAIR_GAP + 14}
                  className="fill-slate-500 dark:fill-slate-400"
                  fontSize="10"
                >
                  {formatNum(d.targetContribution)}
                </text>
              </g>
            )
          })}
        </svg>
      </div>
    </Card>
  )
}
