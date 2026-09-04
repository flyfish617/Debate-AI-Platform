USE debate_ai;

ALTER TABLE users
  MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS topics (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  description TEXT NULL,
  category ENUM('tech','society','philosophy','education','entertainment','other') NOT NULL,
  creator_id INT UNSIGNED NOT NULL,
  status ENUM('visible','hidden') NOT NULL DEFAULT 'visible',
  debate_count INT UNSIGNED NOT NULL DEFAULT 0,
  view_count INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  INDEX idx_topics_category_created_at (category, created_at),
  INDEX idx_topics_creator_id (creator_id),
  INDEX idx_topics_status (status),
  CONSTRAINT fk_topics_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);

ALTER TABLE topics
  MODIFY category ENUM('tech','society','philosophy','education','entertainment','other') NOT NULL,
  MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS debates (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  topic_id INT UNSIGNED NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  user_stance ENUM('pro','con') NOT NULL,
  ai_stance ENUM('pro','con') NOT NULL,
  ai_model VARCHAR(80) NOT NULL DEFAULT 'deepseek-chat',
  style ENUM('mild','intense') NOT NULL DEFAULT 'mild',
  visibility ENUM('public','private') NOT NULL DEFAULT 'public',
  status ENUM('active','ending','ended','failed') NOT NULL DEFAULT 'active',
  current_round INT UNSIGNED NOT NULL DEFAULT 1,
  max_rounds INT UNSIGNED NOT NULL DEFAULT 5,
  winner ENUM('user','ai','draw') NULL,
  vote_count_user INT UNSIGNED NOT NULL DEFAULT 0,
  vote_count_ai INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  INDEX idx_debates_topic_id (topic_id),
  INDEX idx_debates_user_id (user_id),
  INDEX idx_debates_status_created_at (status, created_at),
  CONSTRAINT fk_debates_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
  CONSTRAINT fk_debates_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS messages (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  debate_id INT UNSIGNED NOT NULL,
  user_id INT UNSIGNED NULL,
  role ENUM('user','ai','system') NOT NULL,
  content TEXT NOT NULL,
  round INT UNSIGNED NOT NULL DEFAULT 1,
  ai_provider VARCHAR(50) NULL,
  ai_model VARCHAR(80) NULL,
  latency_ms INT UNSIGNED NULL,
  status ENUM('pending','complete','failed') NOT NULL DEFAULT 'complete',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_messages_debate_round (debate_id, round),
  CONSTRAINT fk_messages_debate FOREIGN KEY (debate_id) REFERENCES debates(id),
  CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users(id)
);

ALTER TABLE debates
  MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

SET @max_rounds_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'debates'
    AND COLUMN_NAME = 'max_rounds'
);

SET @add_max_rounds_sql := IF(
  @max_rounds_missing,
  'ALTER TABLE debates ADD COLUMN max_rounds INT UNSIGNED NOT NULL DEFAULT 5 AFTER current_round',
  'SELECT 1'
);

PREPARE add_max_rounds_stmt FROM @add_max_rounds_sql;
EXECUTE add_max_rounds_stmt;
DEALLOCATE PREPARE add_max_rounds_stmt;

UPDATE debates
SET max_rounds = 5
WHERE max_rounds IS NULL OR max_rounds < 5;

UPDATE debates
SET max_rounds = 15
WHERE max_rounds > 15;

SET @ended_at_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'debates'
    AND COLUMN_NAME = 'ended_at'
);

SET @add_ended_at_sql := IF(
  @ended_at_missing,
  'ALTER TABLE debates ADD COLUMN ended_at DATETIME NULL AFTER updated_at',
  'SELECT 1'
);

PREPARE add_ended_at_stmt FROM @add_ended_at_sql;
EXECUTE add_ended_at_stmt;
DEALLOCATE PREPARE add_ended_at_stmt;

ALTER TABLE messages
  MODIFY status ENUM('pending','complete','failed') NOT NULL DEFAULT 'complete',
  MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS comments (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  debate_id INT UNSIGNED NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  content TEXT NOT NULL,
  status ENUM('visible','hidden') NOT NULL DEFAULT 'visible',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  INDEX idx_comments_debate_created_at (debate_id, created_at),
  INDEX idx_comments_user_created_at (user_id, created_at),
  INDEX idx_comments_status (status),
  CONSTRAINT fk_comments_debate FOREIGN KEY (debate_id) REFERENCES debates(id),
  CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id)
);

ALTER TABLE comments
  MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS votes (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  debate_id INT UNSIGNED NOT NULL,
  voter_id INT UNSIGNED NOT NULL,
  voted_for ENUM('user','ai') NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_votes_debate_voter (debate_id, voter_id),
  INDEX idx_votes_debate_voted_for (debate_id, voted_for),
  CONSTRAINT fk_votes_debate FOREIGN KEY (debate_id) REFERENCES debates(id),
  CONSTRAINT fk_votes_voter FOREIGN KEY (voter_id) REFERENCES users(id)
);

ALTER TABLE votes
  MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS reports (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  reporter_id INT UNSIGNED NOT NULL,
  target_type ENUM('topic','debate','comment','user') NOT NULL,
  target_id INT UNSIGNED NOT NULL,
  reason VARCHAR(200) NOT NULL,
  status ENUM('pending','resolved','rejected') NOT NULL DEFAULT 'pending',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_reports_status_created_at (status, created_at),
  INDEX idx_reports_target (target_type, target_id),
  INDEX idx_reports_reporter_created_at (reporter_id, created_at),
  UNIQUE KEY uk_reports_pending_once (reporter_id, target_type, target_id, status),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id)
);

SET @reports_updated_at_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'reports'
    AND COLUMN_NAME = 'updated_at'
);

SET @add_reports_updated_at_sql := IF(
  @reports_updated_at_missing,
  'ALTER TABLE reports ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at',
  'SELECT 1'
);

PREPARE add_reports_updated_at_stmt FROM @add_reports_updated_at_sql;
EXECUTE add_reports_updated_at_stmt;
DEALLOCATE PREPARE add_reports_updated_at_stmt;

ALTER TABLE reports
  MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

SET @reports_pending_once_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'reports'
    AND INDEX_NAME = 'uk_reports_pending_once'
);

SET @add_reports_pending_once_sql := IF(
  @reports_pending_once_missing,
  'ALTER TABLE reports ADD UNIQUE KEY uk_reports_pending_once (reporter_id, target_type, target_id, status)',
  'SELECT 1'
);

PREPARE add_reports_pending_once_stmt FROM @add_reports_pending_once_sql;
EXECUTE add_reports_pending_once_stmt;
DEALLOCATE PREPARE add_reports_pending_once_stmt;

UPDATE users u
LEFT JOIN (
  SELECT
    user_id,
    SUM(CASE WHEN winner = 'user' THEN 1 ELSE 0 END) AS wins,
    SUM(CASE WHEN winner = 'ai' THEN 1 ELSE 0 END) AS losses,
    SUM(CASE WHEN winner = 'draw' THEN 1 ELSE 0 END) AS draws
  FROM debates
  WHERE status = 'ended'
    AND deleted_at IS NULL
  GROUP BY user_id
) s ON s.user_id = u.id
SET
  u.wins = COALESCE(s.wins, 0),
  u.losses = COALESCE(s.losses, 0),
  u.points = COALESCE(s.wins, 0) * 10 + COALESCE(s.draws, 0) * 3
WHERE u.deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS notifications (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  type ENUM('comment','vote','report_created','report_handled','system') NOT NULL DEFAULT 'system',
  title VARCHAR(80) NOT NULL,
  content VARCHAR(500) NULL,
  link_url VARCHAR(255) NULL,
  status ENUM('unread','read') NOT NULL DEFAULT 'unread',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL,
  deleted_at DATETIME NULL,
  INDEX idx_notifications_user_status_created_at (user_id, status, created_at),
  INDEX idx_notifications_user_created_at (user_id, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
);

SET @notifications_title_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'notifications'
    AND COLUMN_NAME = 'title'
);

SET @add_notifications_title_sql := IF(
  @notifications_title_missing,
  'ALTER TABLE notifications ADD COLUMN title VARCHAR(80) NOT NULL DEFAULT ''系统通知'' AFTER type',
  'SELECT 1'
);

PREPARE add_notifications_title_stmt FROM @add_notifications_title_sql;
EXECUTE add_notifications_title_stmt;
DEALLOCATE PREPARE add_notifications_title_stmt;

SET @notifications_content_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'notifications'
    AND COLUMN_NAME = 'content'
);

SET @add_notifications_content_sql := IF(
  @notifications_content_missing,
  'ALTER TABLE notifications ADD COLUMN content VARCHAR(500) NULL AFTER title',
  'SELECT 1'
);

PREPARE add_notifications_content_stmt FROM @add_notifications_content_sql;
EXECUTE add_notifications_content_stmt;
DEALLOCATE PREPARE add_notifications_content_stmt;

SET @notifications_link_url_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'notifications'
    AND COLUMN_NAME = 'link_url'
);

SET @add_notifications_link_url_sql := IF(
  @notifications_link_url_missing,
  'ALTER TABLE notifications ADD COLUMN link_url VARCHAR(255) NULL AFTER content',
  'SELECT 1'
);

PREPARE add_notifications_link_url_stmt FROM @add_notifications_link_url_sql;
EXECUTE add_notifications_link_url_stmt;
DEALLOCATE PREPARE add_notifications_link_url_stmt;

SET @notifications_status_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'notifications'
    AND COLUMN_NAME = 'status'
);

SET @add_notifications_status_sql := IF(
  @notifications_status_missing,
  'ALTER TABLE notifications ADD COLUMN status ENUM(''unread'',''read'') NOT NULL DEFAULT ''unread'' AFTER link_url',
  'SELECT 1'
);

PREPARE add_notifications_status_stmt FROM @add_notifications_status_sql;
EXECUTE add_notifications_status_stmt;
DEALLOCATE PREPARE add_notifications_status_stmt;

SET @notifications_deleted_at_missing := (
  SELECT COUNT(*) = 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'notifications'
    AND COLUMN_NAME = 'deleted_at'
);

SET @add_notifications_deleted_at_sql := IF(
  @notifications_deleted_at_missing,
  'ALTER TABLE notifications ADD COLUMN deleted_at DATETIME NULL AFTER read_at',
  'SELECT 1'
);

PREPARE add_notifications_deleted_at_stmt FROM @add_notifications_deleted_at_sql;
EXECUTE add_notifications_deleted_at_stmt;
DEALLOCATE PREPARE add_notifications_deleted_at_stmt;

SET @notifications_payload_exists := (
  SELECT COUNT(*) > 0
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'notifications'
    AND COLUMN_NAME = 'payload'
);

SET @modify_notifications_payload_sql := IF(
  @notifications_payload_exists,
  'ALTER TABLE notifications MODIFY payload JSON NULL',
  'SELECT 1'
);

PREPARE modify_notifications_payload_stmt FROM @modify_notifications_payload_sql;
EXECUTE modify_notifications_payload_stmt;
DEALLOCATE PREPARE modify_notifications_payload_stmt;
