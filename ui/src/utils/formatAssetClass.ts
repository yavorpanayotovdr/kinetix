const ABBREVIATIONS = new Set(['FX', 'ETF', 'CDS'])

export function formatAssetClassLabel(assetClass: string): string {
  if (ABBREVIATIONS.has(assetClass.toUpperCase())) return assetClass.toUpperCase()

  return assetClass
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ')
}
