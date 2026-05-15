
-- PostgreSQL initialization script
-- Creates the contextengine database schema on first startup.
-- All subsequent migrations are handled by Flyway in each service.

-- Enable the pgcrypto extension for UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Enable the pg_trgm extension for full-text trigram search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Keycloak database (created separately for SSO, Sprint 5)
CREATE DATABASE keycloak;
