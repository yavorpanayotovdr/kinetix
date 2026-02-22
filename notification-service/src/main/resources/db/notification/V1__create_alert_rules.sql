CREATE TABLE alert_rules (
    id         VARCHAR(255)   NOT NULL,
    name       VARCHAR(255)   NOT NULL,
    type       VARCHAR(50)    NOT NULL,
    threshold  DOUBLE PRECISION NOT NULL,
    operator   VARCHAR(50)    NOT NULL,
    severity   VARCHAR(50)    NOT NULL,
    channels   VARCHAR(500)   NOT NULL,
    enabled    BOOLEAN        NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_alert_rules PRIMARY KEY (id)
);
