DROP TABLE IF EXISTS note;
CREATE TABLE note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    tags TEXT,
    is_important TINYINT(1) default false,
    is_pinned TINYINT(1) default false,
    deadline DATETIME,
    is_completed TINYINT(1) default false,
    created_at DATETIME NOT NULL,
    delete_flg TINYINT(1) default false
);

DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  color VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  delete_flg TINYINT(1) NOT NULL
);

DROP TABLE IF EXISTS users;
CREATE TABLE users (
   id BIGINT AUTO_INCREMENT PRIMARY KEY,
   username VARCHAR(50) NOT NULL UNIQUE,
   password VARCHAR(255) NOT NULL,
   enabled TINYINT(1) NOT NULL DEFAULT 1,
   role VARCHAR(20) DEFAULT 'USER'
);