-- Benchmark definitions (e.g., S&P 500, MSCI World).
-- Used as the comparison baseline for Brinson performance attribution.

CREATE TABLE benchmarks (
    benchmark_id  VARCHAR(36)   NOT NULL,
    name          VARCHAR(255)  NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_benchmarks PRIMARY KEY (benchmark_id)
);

CREATE INDEX idx_benchmarks_name ON benchmarks (name);

-- Benchmark constituent weights, snapshotted per date.
-- Weights for a given (benchmark_id, as_of_date) should sum to 1.0.

CREATE TABLE benchmark_constituents (
    benchmark_id  VARCHAR(36)   NOT NULL REFERENCES benchmarks(benchmark_id),
    instrument_id VARCHAR(255)  NOT NULL,
    weight        DECIMAL       NOT NULL,
    as_of_date    DATE          NOT NULL,
    CONSTRAINT pk_benchmark_constituents PRIMARY KEY (benchmark_id, instrument_id, as_of_date)
);

CREATE INDEX idx_benchmark_constituents_date ON benchmark_constituents (benchmark_id, as_of_date);

-- Daily benchmark returns used for multi-period attribution.

CREATE TABLE benchmark_returns (
    benchmark_id  VARCHAR(36)   NOT NULL REFERENCES benchmarks(benchmark_id),
    return_date   DATE          NOT NULL,
    daily_return  DECIMAL       NOT NULL,
    CONSTRAINT pk_benchmark_returns PRIMARY KEY (benchmark_id, return_date)
);

CREATE INDEX idx_benchmark_returns_date ON benchmark_returns (benchmark_id, return_date);
