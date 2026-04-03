-- What To Eat baseline schema
--
-- Maintenance rule:
-- - `bootstrap.sql` is the primary schema baseline for local/bootstrap environments.
-- - When a table structure, relation, column meaning, or migration strategy changes,
--   update the nearby comments in this file in the same change so the schema notes stay accurate.

-- users:
-- - Account and identity root table.
-- - Stores login credentials, profile fields, region, and failed-login counters.
-- - Referenced by authentication, profile, favorites, and generated recipe ownership.
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(50),
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    password VARCHAR(120) NOT NULL,
    avatar_url VARCHAR(255),
    bio VARCHAR(200),
    gender VARCHAR(20),
    birthday DATE,
    region VARCHAR(100),
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- meal_catalog_datasets:
-- - Version registry for seeded base menu datasets.
-- - One row represents one imported menu source, such as `cn-home-menu-v1`.
-- - Used to support idempotent imports and future online dataset migrations/cutovers.
CREATE TABLE IF NOT EXISTS meal_catalog_datasets (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    source_file VARCHAR(200) NOT NULL,
    source_checksum VARCHAR(64) NOT NULL,
    total_items INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Compatibility backfills for databases created before `source_checksum` and `active`
-- existed on `meal_catalog_datasets`.
ALTER TABLE meal_catalog_datasets
    ADD COLUMN IF NOT EXISTS source_checksum VARCHAR(64);
ALTER TABLE meal_catalog_datasets
    ADD COLUMN IF NOT EXISTS active BOOLEAN;
ALTER TABLE meal_catalog_datasets
    ALTER COLUMN source_file TYPE VARCHAR(200);
ALTER TABLE meal_catalog_datasets
    ALTER COLUMN title TYPE VARCHAR(200);

UPDATE meal_catalog_datasets
SET source_checksum = COALESCE(
        source_checksum,
        md5(CONCAT(version, ':', source_file, ':', total_items))
)
WHERE source_checksum IS NULL;

UPDATE meal_catalog_datasets
SET active = COALESCE(active, TRUE)
WHERE active IS NULL;

ALTER TABLE meal_catalog_datasets
    ALTER COLUMN source_checksum SET NOT NULL;
ALTER TABLE meal_catalog_datasets
    ALTER COLUMN active SET NOT NULL;
ALTER TABLE meal_catalog_datasets
    ALTER COLUMN active SET DEFAULT TRUE;

-- meal_catalog_tags:
-- - Shared tag dictionary for the base menu.
-- - Holds reusable labels across multiple dimensions:
--   CATEGORY / SUBCATEGORY / COOKING_METHOD / FLAVOR / FEATURE / INGREDIENT.
CREATE TABLE IF NOT EXISTS meal_catalog_tags (
    id BIGSERIAL PRIMARY KEY,
    tag_type VARCHAR(40) NOT NULL,
    tag_key VARCHAR(120) NOT NULL,
    tag_label VARCHAR(120) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_meal_catalog_tag_type_key UNIQUE (tag_type, tag_key)
);

-- meal_catalog_items:
-- - Canonical base-menu dish table. One row equals one source dish.
-- - Stores dish naming, classification, cooking method, flavor-tag summary, source order,
--   and the dataset version it belongs to.
-- - "来点灵感" and future precise recommendation logic draw candidates from here.
CREATE TABLE IF NOT EXISTS meal_catalog_items (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES meal_catalog_datasets(id) ON DELETE CASCADE,
    dataset_version VARCHAR(80) NOT NULL,
    source_index INTEGER NOT NULL,
    code VARCHAR(80) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(80) NOT NULL,
    subcategory VARCHAR(80) NOT NULL,
    cooking_method VARCHAR(80) NOT NULL,
    raw_flavor_text VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_meal_catalog_item_dataset_code UNIQUE (dataset_id, code)
);

-- Compatibility backfills for databases created before `dataset_version` and `slug`
-- existed on `meal_catalog_items`.
ALTER TABLE meal_catalog_items
    ADD COLUMN IF NOT EXISTS dataset_version VARCHAR(80);
ALTER TABLE meal_catalog_items
    ADD COLUMN IF NOT EXISTS slug VARCHAR(120);
ALTER TABLE meal_catalog_items
    ALTER COLUMN code TYPE VARCHAR(80);
ALTER TABLE meal_catalog_items
    ALTER COLUMN name TYPE VARCHAR(120);

UPDATE meal_catalog_items item
SET dataset_version = dataset.version
FROM meal_catalog_datasets dataset
WHERE item.dataset_id = dataset.id
  AND item.dataset_version IS NULL;

UPDATE meal_catalog_items
SET slug = COALESCE(code, 'catalog-' || LPAD(source_index::text, 3, '0'))
WHERE slug IS NULL;

ALTER TABLE meal_catalog_items
    ALTER COLUMN dataset_version SET NOT NULL;
ALTER TABLE meal_catalog_items
    ALTER COLUMN slug SET NOT NULL;

-- meal_catalog_item_tags:
-- - Many-to-many relation between base-menu dishes and reusable tags.
-- - This is the core association table for recommendation features, including ingredient tags.
CREATE TABLE IF NOT EXISTS meal_catalog_item_tags (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES meal_catalog_items(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES meal_catalog_tags(id) ON DELETE CASCADE,
    CONSTRAINT uk_meal_catalog_item_tag UNIQUE (item_id, tag_id)
);

-- meal_image_assets:
-- - Dish-image cache table keyed by normalized dish name.
-- - Stores the original公网图片地址 and the persisted local/OSS image URL so repeated
--   generations can reuse an existing dish image instead of searching again.
-- - This is the persistence bridge for the async recipe-image flow:
--   `POST /api/meals/recipes/{id}/image` -> search/download -> local or OSS storage -> cache hit reuse.
CREATE TABLE IF NOT EXISTS meal_image_assets (
    id BIGSERIAL PRIMARY KEY,
    dish_name VARCHAR(200) NOT NULL,
    normalized_dish_name VARCHAR(200) NOT NULL,
    source_image_url VARCHAR(1000) NOT NULL,
    source_page_url VARCHAR(1000),
    storage_key VARCHAR(255) NOT NULL,
    public_image_url VARCHAR(1000) NOT NULL,
    source_provider VARCHAR(80) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_meal_image_asset_dish_source UNIQUE (normalized_dish_name, source_image_url)
);

-- meal_recipes:
-- - Generated recipe/result table for actual user requests.
-- - Stores the request context, model/provider output, generated steps/ingredients JSON,
--   optional image info, and the user's preference (`LIKE` / `DISLIKE`).
-- - `catalog_item_id` links a generated result back to the base-menu candidate that inspired it.
-- - `image_status` and `steps_status` support the two-phase UX:
--   first return recipe cards quickly, then lazily补图 / 补做法.
CREATE TABLE IF NOT EXISTS meal_recipes (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    catalog_item_id BIGINT,
    catalog_item_code VARCHAR(80),
    source_text VARCHAR(1000) NOT NULL,
    source_mode VARCHAR(20) NOT NULL,
    dish_count INTEGER NOT NULL,
    total_calories INTEGER,
    staple VARCHAR(40),
    locale VARCHAR(20),
    provider VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(1000),
    estimated_calories INTEGER,
    ingredients_json TEXT,
    seasonings_json TEXT,
    steps_json TEXT,
    image_url VARCHAR(500),
    image_status VARCHAR(20) NOT NULL DEFAULT 'OMITTED',
    preference VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Compatibility backfills for earlier recipe rows that were created before
-- the base-menu linkage columns were introduced.
ALTER TABLE meal_recipes
    ADD COLUMN IF NOT EXISTS catalog_item_id BIGINT;
ALTER TABLE meal_recipes
    ADD COLUMN IF NOT EXISTS catalog_item_code VARCHAR(80);
ALTER TABLE meal_recipes
    DROP COLUMN IF EXISTS flavor;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_meal_recipes_catalog_item'
          AND table_name = 'meal_recipes'
    ) THEN
        ALTER TABLE meal_recipes
            ADD CONSTRAINT fk_meal_recipes_catalog_item
            FOREIGN KEY (catalog_item_id) REFERENCES meal_catalog_items(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_meal_catalog_items_dataset_id ON meal_catalog_items(dataset_id);
CREATE INDEX IF NOT EXISTS idx_meal_catalog_items_dataset_version ON meal_catalog_items(dataset_version);
CREATE INDEX IF NOT EXISTS idx_meal_catalog_items_source_index ON meal_catalog_items(source_index);
CREATE INDEX IF NOT EXISTS idx_meal_catalog_item_tags_item_id ON meal_catalog_item_tags(item_id);
CREATE INDEX IF NOT EXISTS idx_meal_catalog_item_tags_tag_id ON meal_catalog_item_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_meal_image_assets_normalized_dish_name ON meal_image_assets(normalized_dish_name);
CREATE INDEX IF NOT EXISTS idx_meal_recipes_user_id ON meal_recipes(user_id);
CREATE INDEX IF NOT EXISTS idx_meal_recipes_catalog_item_id ON meal_recipes(catalog_item_id);
CREATE INDEX IF NOT EXISTS idx_meal_recipes_request_id ON meal_recipes(request_id);
CREATE INDEX IF NOT EXISTS idx_meal_recipes_preference ON meal_recipes(preference);
CREATE INDEX IF NOT EXISTS idx_meal_recipes_updated_at ON meal_recipes(updated_at DESC);

-- Migrate meal_recipes_id_seq from BIGSERIAL (INCREMENT BY 1) to Hibernate SEQUENCE strategy
-- (INCREMENT BY 50) to enable batch INSERT support.
-- Idempotent: skips if the sequence already has INCREMENT BY 50.
DO $$
DECLARE
    current_increment BIGINT;
    max_id            BIGINT;
    new_start         BIGINT;
BEGIN
    SELECT increment_by INTO current_increment
    FROM pg_sequences
    WHERE sequencename = 'meal_recipes_id_seq';

    IF current_increment IS DISTINCT FROM 50 THEN
        SELECT COALESCE(MAX(id), 0) INTO max_id FROM meal_recipes;
        -- Hibernate's pooled optimizer treats nextval() as the HIGH end of the range:
        -- it allocates IDs [nextval - allocationSize + 1, nextval].
        -- Setting new_start = max_id + allocationSize guarantees the range
        -- starts at max_id + 1, safely after all existing rows.
        new_start := max_id + 50;
        ALTER SEQUENCE meal_recipes_id_seq INCREMENT BY 50;
        PERFORM setval('meal_recipes_id_seq', new_start, false);
        RAISE NOTICE 'meal_recipes_id_seq migrated: INCREMENT=50, next_val=%', new_start;
    ELSE
        RAISE NOTICE 'meal_recipes_id_seq already at INCREMENT=50, skipping.';
    END IF;
END $$;
