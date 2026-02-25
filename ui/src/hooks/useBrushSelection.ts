import { useCallback, useRef, useState } from 'react'

export interface BrushState {
  active: boolean
  startX: number
  currentX: number
}

interface UseBrushSelectionOptions {
  onBrushEnd: (startX: number, endX: number) => void
  onClick?: (x: number) => void
  minDragPx?: number
}

export function useBrushSelection({ onBrushEnd, onClick, minDragPx = 5 }: UseBrushSelectionOptions) {
  const [brush, setBrush] = useState<BrushState>({ active: false, startX: 0, currentX: 0 })
  const dragging = useRef(false)
  const origin = useRef(0)

  const onMouseDown = useCallback((e: React.MouseEvent<SVGSVGElement>) => {
    const svg = e.currentTarget
    const rect = svg.getBoundingClientRect()
    const x = e.clientX - rect.left
    origin.current = x
    dragging.current = true
    setBrush({ active: true, startX: x, currentX: x })
  }, [])

  const onMouseMove = useCallback((e: React.MouseEvent<SVGSVGElement>) => {
    if (!dragging.current) return
    const svg = e.currentTarget
    const rect = svg.getBoundingClientRect()
    const x = e.clientX - rect.left
    setBrush((prev) => ({ ...prev, currentX: x }))
  }, [])

  const onMouseUp = useCallback(() => {
    if (!dragging.current) return
    dragging.current = false
    setBrush((prev) => {
      const left = Math.min(prev.startX, prev.currentX)
      const right = Math.max(prev.startX, prev.currentX)
      if (right - left >= minDragPx) {
        onBrushEnd(left, right)
      } else if (onClick) {
        onClick(origin.current)
      }
      return { active: false, startX: 0, currentX: 0 }
    })
  }, [onBrushEnd, onClick, minDragPx])

  const onMouseLeave = useCallback(() => {
    if (dragging.current) {
      dragging.current = false
      setBrush({ active: false, startX: 0, currentX: 0 })
    }
  }, [])

  return {
    brush,
    handlers: { onMouseDown, onMouseMove, onMouseUp, onMouseLeave },
  }
}
