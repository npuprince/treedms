CREATE DATABASE IF NOT EXISTS treedms
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE treedms;

CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT NOT NULL COMMENT 'Department id',
    pid BIGINT NULL COMMENT 'Parent department id',
    department VARCHAR(100) NOT NULL COMMENT 'Department name',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Sibling order',
    encrypted TINYINT NOT NULL DEFAULT 0 COMMENT '0 visible, 1 encrypted',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_department_pid (pid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_department_favorite (
    username VARCHAR(80) NOT NULL COMMENT 'Account username',
    dept_id BIGINT NOT NULL COMMENT 'Department id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username, dept_id),
    KEY idx_favorite_dept_id (dept_id),
    CONSTRAINT fk_favorite_department FOREIGN KEY (dept_id) REFERENCES sys_department (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS biz_file (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'File id',
    dept_id BIGINT NOT NULL COMMENT 'Department id',
    original_name VARCHAR(255) NOT NULL COMMENT 'Original file name',
    storage_name VARCHAR(255) NOT NULL COMMENT 'Stored file name',
    relative_path VARCHAR(500) NOT NULL COMMENT 'Path relative to storage root',
    content_type VARCHAR(120) NULL COMMENT 'MIME type',
    size BIGINT NOT NULL DEFAULT 0 COMMENT 'File size in bytes',
    sha256 CHAR(64) NULL COMMENT 'SHA-256 of latest version',
    uploader VARCHAR(80) NOT NULL COMMENT 'Uploader account',
    pinned TINYINT NOT NULL DEFAULT 0 COMMENT '0 normal, 1 pinned',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Sibling order',
    version_no INT NOT NULL DEFAULT 1 COMMENT 'Latest version number',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
    PRIMARY KEY (id),
    KEY idx_biz_file_dept_id (dept_id),
    KEY idx_biz_file_dept_name (dept_id, original_name),
    KEY idx_biz_file_deleted (deleted),
    CONSTRAINT fk_biz_file_department FOREIGN KEY (dept_id) REFERENCES sys_department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS biz_file_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Version id',
    file_id BIGINT NOT NULL COMMENT 'File id',
    version_no INT NOT NULL COMMENT 'Version number',
    storage_name VARCHAR(255) NOT NULL COMMENT 'Stored file name',
    relative_path VARCHAR(500) NOT NULL COMMENT 'Path relative to storage root',
    content_type VARCHAR(120) NULL COMMENT 'MIME type',
    size BIGINT NOT NULL DEFAULT 0 COMMENT 'File size in bytes',
    sha256 CHAR(64) NULL COMMENT 'SHA-256 of file content',
    uploader VARCHAR(80) NOT NULL COMMENT 'Uploader account',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_version_no (file_id, version_no),
    KEY idx_file_version_file_id (file_id),
    CONSTRAINT fk_file_version_file FOREIGN KEY (file_id) REFERENCES biz_file (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_department'
      AND COLUMN_NAME = 'sort_order'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_department ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT ''Sibling order'' AFTER department',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_department'
      AND COLUMN_NAME = 'encrypted'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_department ADD COLUMN encrypted TINYINT NOT NULL DEFAULT 0 COMMENT ''0 visible, 1 encrypted'' AFTER sort_order',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_file'
      AND COLUMN_NAME = 'sha256'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE biz_file ADD COLUMN sha256 CHAR(64) NULL COMMENT ''SHA-256 of latest version'' AFTER size',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_file'
      AND COLUMN_NAME = 'pinned'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE biz_file ADD COLUMN pinned TINYINT NOT NULL DEFAULT 0 COMMENT ''0 normal, 1 pinned'' AFTER uploader',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_file'
      AND COLUMN_NAME = 'sort_order'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE biz_file ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT ''Sibling order'' AFTER pinned',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_file'
      AND COLUMN_NAME = 'version_no'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE biz_file ADD COLUMN version_no INT NOT NULL DEFAULT 1 COMMENT ''Latest version number'' AFTER sort_order',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_department
SET sort_order = (id + 1) * 1000
WHERE sort_order = 0;

UPDATE sys_department
SET sort_order = 0
WHERE pid IS NULL;

UPDATE sys_department
SET encrypted = 0
WHERE encrypted IS NULL;

UPDATE biz_file
SET sort_order = id * 1000
WHERE sort_order = 0;

UPDATE biz_file
SET version_no = 1
WHERE version_no IS NULL OR version_no = 0;

INSERT INTO biz_file_version (
    file_id, version_no, storage_name, relative_path, content_type, size, sha256, uploader, created_at
)
SELECT bf.id, 1, bf.storage_name, bf.relative_path, bf.content_type, bf.size, bf.sha256, bf.uploader, bf.created_at
FROM biz_file bf
WHERE NOT EXISTS (
    SELECT 1
    FROM biz_file_version bfv
    WHERE bfv.file_id = bf.id
      AND bfv.version_no = 1
);

INSERT INTO sys_department (id, pid, department, sort_order, encrypted) VALUES
    (0, NULL, 'root', 0, 0),
    (1, 0, 'a', 1000, 0),
    (2, 0, 'b', 2000, 0),
    (3, 1, 'f', 1000, 0),
    (4, 1, 'd', 2000, 0),
    (5, 3, 'e', 1000, 0),
    (6, 4, 'g', 1000, 0)
ON DUPLICATE KEY UPDATE
    id = id;
