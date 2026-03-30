INSERT INTO users (username, display_name, email, phone, password, bio, region)
VALUES
  ('demo_admin', 'Demo Admin', 'demo-admin@example.com', '13800000000', '$2y$10$iyZHxJwAtgU/UVpn47l46Oi48p/CHwy7tH9FtCjcm425r3GMoJc9q', 'Template seed user', 'Shanghai')
ON CONFLICT (username) DO NOTHING;

INSERT INTO posts (content, user_id, location_name, location_address, latitude, longitude, gis_point, like_count, comment_count)
SELECT
  'Welcome to QuickStart Template. This post validates the social demo flow.',
  users.id,
  'Template Plaza',
  'No.1 Example Road',
  31.2304,
  121.4737,
  'POINT(121.4737 31.2304)',
  0,
  0
FROM users
WHERE users.username = 'demo_admin'
  AND NOT EXISTS (SELECT 1 FROM posts WHERE content LIKE 'Welcome to QuickStart Template%');
