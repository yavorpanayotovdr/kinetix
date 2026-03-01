from dataclasses import dataclass, field


@dataclass(frozen=True)
class VolSurfacePoint:
    strike: float
    maturity_days: int
    implied_vol: float


@dataclass
class VolSurface:
    points: list[VolSurfacePoint] = field(default_factory=list)

    def vol_at(self, strike: float, maturity_days: int) -> float:
        if not self.points:
            raise ValueError("VolSurface has no points")
        if len(self.points) == 1:
            return self.points[0].implied_vol

        strikes = sorted(set(p.strike for p in self.points))
        maturities = sorted(set(p.maturity_days for p in self.points))

        s_lo, s_hi = self._bracket(strikes, strike)
        m_lo, m_hi = self._bracket(maturities, maturity_days)

        def _lookup(s: float, m: int) -> float:
            for p in self.points:
                if p.strike == s and p.maturity_days == m:
                    return p.implied_vol
            # Nearest-neighbor fallback
            best = min(self.points, key=lambda p: (abs(p.strike - s) + abs(p.maturity_days - m)))
            return best.implied_vol

        if s_lo == s_hi and m_lo == m_hi:
            return _lookup(s_lo, m_lo)
        if s_lo == s_hi:
            v_lo = _lookup(s_lo, m_lo)
            v_hi = _lookup(s_lo, m_hi)
            t = (maturity_days - m_lo) / (m_hi - m_lo)
            return v_lo + t * (v_hi - v_lo)
        if m_lo == m_hi:
            v_lo = _lookup(s_lo, m_lo)
            v_hi = _lookup(s_hi, m_lo)
            t = (strike - s_lo) / (s_hi - s_lo)
            return v_lo + t * (v_hi - v_lo)

        # Bilinear interpolation
        v00 = _lookup(s_lo, m_lo)
        v01 = _lookup(s_lo, m_hi)
        v10 = _lookup(s_hi, m_lo)
        v11 = _lookup(s_hi, m_hi)

        ts = (strike - s_lo) / (s_hi - s_lo)
        tm = (maturity_days - m_lo) / (m_hi - m_lo)

        v_m_lo = v00 + ts * (v10 - v00)
        v_m_hi = v01 + ts * (v11 - v01)
        return v_m_lo + tm * (v_m_hi - v_m_lo)

    @staticmethod
    def _bracket(sorted_values: list, target) -> tuple:
        if target <= sorted_values[0]:
            return sorted_values[0], sorted_values[0]
        if target >= sorted_values[-1]:
            return sorted_values[-1], sorted_values[-1]
        for i in range(len(sorted_values) - 1):
            if sorted_values[i] <= target <= sorted_values[i + 1]:
                return sorted_values[i], sorted_values[i + 1]
        return sorted_values[-1], sorted_values[-1]


@dataclass
class YieldCurveData:
    tenors: list[tuple[int, float]] = field(default_factory=list)  # (days, rate)

    def rate_at(self, days: int) -> float:
        if not self.tenors:
            raise ValueError("YieldCurveData has no tenors")
        sorted_tenors = sorted(self.tenors, key=lambda t: t[0])
        if days <= sorted_tenors[0][0]:
            return sorted_tenors[0][1]
        if days >= sorted_tenors[-1][0]:
            return sorted_tenors[-1][1]
        for i in range(len(sorted_tenors) - 1):
            d_lo, r_lo = sorted_tenors[i]
            d_hi, r_hi = sorted_tenors[i + 1]
            if d_lo <= days <= d_hi:
                t = (days - d_lo) / (d_hi - d_lo)
                return r_lo + t * (r_hi - r_lo)
        return sorted_tenors[-1][1]
