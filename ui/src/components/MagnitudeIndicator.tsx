interface MagnitudeIndicatorProps {
  magnitude: 'LARGE' | 'MEDIUM' | 'SMALL'
}

export function MagnitudeIndicator({ magnitude }: MagnitudeIndicatorProps) {
  const config = {
    LARGE: {
      icon: '\u25CF',
      label: 'LARGE',
      classes: 'text-amber-700 bg-amber-100 dark:text-amber-400 dark:bg-amber-900/30',
    },
    MEDIUM: {
      icon: '\u25C6',
      label: 'MED',
      classes: 'text-blue-700 bg-blue-100 dark:text-blue-400 dark:bg-blue-900/30',
    },
    SMALL: {
      icon: '\u25CB',
      label: 'SMALL',
      classes: 'text-slate-500 bg-slate-100 dark:text-slate-400 dark:bg-surface-700',
    },
  }

  const { icon, label, classes } = config[magnitude]

  return (
    <span
      className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium ${classes}`}
      data-testid={`magnitude-${magnitude.toLowerCase()}`}
    >
      <span aria-hidden="true">{icon}</span>
      {label}
    </span>
  )
}
