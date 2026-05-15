
CREATE TABLE organizations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    plan_tier   VARCHAR(50)  NOT NULL DEFAULT 'free',
    user_limit  INTEGER      NOT NULL DEFAULT 5,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    password_hash   VARCHAR(255),
    role            VARCHAR(50)  NOT NULL DEFAULT 'MEMBER',
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_organization_id ON users(organization_id);
CREATE INDEX idx_users_email            ON users(email);

CREATE TABLE api_keys (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    key_hash        VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix      VARCHAR(10)  NOT NULL,
    last_used_at    TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_keys_user_id  ON api_keys(user_id);
CREATE INDEX idx_api_keys_key_hash ON api_keys(key_hash);
