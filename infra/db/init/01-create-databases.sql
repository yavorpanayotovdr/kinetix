-- Per-service databases for Kinetix
CREATE DATABASE kinetix_gateway;
CREATE DATABASE kinetix_position;
CREATE DATABASE kinetix_market_data;
CREATE DATABASE kinetix_risk;
CREATE DATABASE kinetix_audit;
CREATE DATABASE kinetix_notification;
CREATE DATABASE kinetix_regulatory;

-- Enable TimescaleDB on market-data database (time-series hypertables)
\c kinetix_market_data
CREATE EXTENSION IF NOT EXISTS timescaledb;
