-- Convert regulatory capital charges from DOUBLE PRECISION to NUMERIC
-- for exact reproducibility.
-- frtb_calculations is a hypertable — decompress chunks before altering.

SELECT decompress_chunk(c) FROM show_chunks('frtb_calculations') c
WHERE is_compressed;

ALTER TABLE frtb_calculations
    ALTER COLUMN total_sbm_charge     TYPE NUMERIC(28,8) USING total_sbm_charge::NUMERIC(28,8),
    ALTER COLUMN gross_jtd            TYPE NUMERIC(28,8) USING gross_jtd::NUMERIC(28,8),
    ALTER COLUMN hedge_benefit        TYPE NUMERIC(28,8) USING hedge_benefit::NUMERIC(28,8),
    ALTER COLUMN net_drc              TYPE NUMERIC(28,8) USING net_drc::NUMERIC(28,8),
    ALTER COLUMN exotic_notional      TYPE NUMERIC(28,8) USING exotic_notional::NUMERIC(28,8),
    ALTER COLUMN other_notional       TYPE NUMERIC(28,8) USING other_notional::NUMERIC(28,8),
    ALTER COLUMN total_rrao           TYPE NUMERIC(28,8) USING total_rrao::NUMERIC(28,8),
    ALTER COLUMN total_capital_charge TYPE NUMERIC(28,8) USING total_capital_charge::NUMERIC(28,8);

SELECT compress_chunk(c) FROM show_chunks('frtb_calculations') c
WHERE NOT is_compressed
  AND range_end < NOW() - INTERVAL '90 days';
