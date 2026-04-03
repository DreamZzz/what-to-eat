INSERT INTO users (username, display_name, email, phone, password, bio, region)
VALUES
  ('demo_admin', 'Demo Admin', 'demo-admin@example.com', '13800000000', '$2y$10$iyZHxJwAtgU/UVpn47l46Oi48p/CHwy7tH9FtCjcm425r3GMoJc9q', 'What To Eat demo account', 'Shanghai')
ON CONFLICT (username) DO NOTHING;
