-- Removes legacy social/location tables after the community and location contexts
-- have been retired from What To Eat.
--
-- Run manually against existing environments that were created before the cleanup:
--   psql -d what_to_eat_db -f backend/sql/cleanup-legacy-community.sql

DROP TABLE IF EXISTS comments CASCADE;
DROP TABLE IF EXISTS post_images CASCADE;
DROP TABLE IF EXISTS posts CASCADE;
