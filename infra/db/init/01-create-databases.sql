-- Per-service databases for Kinetix
CREATE DATABASE kinetix_gateway;
CREATE DATABASE kinetix_position;
CREATE DATABASE kinetix_price;
CREATE DATABASE kinetix_risk;
CREATE DATABASE kinetix_audit;
CREATE DATABASE kinetix_notification;
CREATE DATABASE kinetix_regulatory;
CREATE DATABASE kinetix_rates;
CREATE DATABASE kinetix_reference_data;
CREATE DATABASE kinetix_volatility;
CREATE DATABASE kinetix_correlation;

-- Enable TimescaleDB on price database (time-series hypertables)
\c kinetix_price
CREATE EXTENSION IF NOT EXISTS timescaledb;
