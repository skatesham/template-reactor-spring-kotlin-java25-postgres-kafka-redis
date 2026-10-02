CREATE TABLE roles
(
    name VARCHAR(32) PRIMARY KEY,
    CONSTRAINT ck_roles_name CHECK (name IN ('USER', 'ADMIN'))
);
INSERT INTO roles (name)
VALUES ('USER'),
       ('ADMIN');

CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(trim(email)))
);

CREATE TABLE user_roles
(
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_name VARCHAR(32) NOT NULL REFERENCES roles (name),
    PRIMARY KEY (user_id, role_name)
);
