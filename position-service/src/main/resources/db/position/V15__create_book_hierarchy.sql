-- V15: Create book_hierarchy table.
-- Maps each book (owned by position-service) to its desk
-- (owned by reference-data-service). This closes the gap needed
-- for hierarchy risk aggregation in risk-orchestrator.

CREATE TABLE book_hierarchy (
    book_id    VARCHAR(64)  NOT NULL,
    desk_id    VARCHAR(64)  NOT NULL,
    book_name  VARCHAR(255),
    book_type  VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_book_hierarchy PRIMARY KEY (book_id),
    CONSTRAINT uq_book_hierarchy_book_id UNIQUE (book_id)
);

-- Index to support queries "give me all books for desk X"
CREATE INDEX idx_book_hierarchy_desk_id ON book_hierarchy (desk_id);
