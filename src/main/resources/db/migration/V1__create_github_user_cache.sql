CREATE TABLE github_user_cache (
    username   TEXT        PRIMARY KEY,
    response   TEXT        NOT NULL,
    cached_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
