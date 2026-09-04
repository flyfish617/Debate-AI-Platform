CREATE DATABASE IF NOT EXISTS debate_ai
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE debate_ai;

CREATE TABLE IF NOT EXISTS users (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(30) NOT NULL UNIQUE,
  email VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  avatar_url VARCHAR(500) NULL,
  bio VARCHAR(200) NULL,
  role ENUM('user','admin') NOT NULL DEFAULT 'user',
  status ENUM('active','disabled') NOT NULL DEFAULT 'active',
  points INT UNSIGNED NOT NULL DEFAULT 0,
  wins INT UNSIGNED NOT NULL DEFAULT 0,
  losses INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  INDEX idx_users_status (status),
  INDEX idx_users_points (points)
);

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
  ended_at DATETIME NULL,
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
