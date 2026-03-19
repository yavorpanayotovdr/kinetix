CREATE INDEX idx_instruments_expiry_date ON instruments ((attributes->>'expiryDate')) WHERE attributes ? 'expiryDate';

CREATE INDEX idx_instruments_maturity_date ON instruments ((attributes->>'maturityDate')) WHERE attributes ? 'maturityDate';
