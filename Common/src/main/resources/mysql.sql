SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `{prefix}`;
DROP TABLE IF EXISTS `{prefix}queue`;

DROP TABLE IF EXISTS `{prefix}data`;
CREATE TABLE `{prefix}data` (
  `id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT,
  `key` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `{prefix}name_UNIQUE` (`key`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `{prefix}servers`;
CREATE TABLE `{prefix}servers` (
  `id` bigint(8) unsigned NOT NULL AUTO_INCREMENT,
  `uuid` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(25) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `{prefix}uuid_UNIQUE` (`uuid`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `{prefix}ips`;
CREATE TABLE `{prefix}ips` (
  `id` bigint(8) unsigned NOT NULL AUTO_INCREMENT,
  `ip` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `{prefix}ip_UNIQUE` (`ip`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `{prefix}players`;
CREATE TABLE `{prefix}players` (
  `id` bigint(8) unsigned NOT NULL AUTO_INCREMENT,
  `uuid` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `{prefix}uuid_UNIQUE` (`uuid`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `{prefix}alts`;
CREATE TABLE `{prefix}alts` (
  `id` bigint(8) unsigned NOT NULL AUTO_INCREMENT,
  `ip_id` bigint(8) unsigned NOT NULL,
  `player_id` bigint(8) unsigned NOT NULL,
  `server_id` bigint(8) unsigned NOT NULL,
  `count` bigint(8) unsigned NOT NULL DEFAULT 1,
  `created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `{prefix}fk_alts_ip_id` FOREIGN KEY (`ip_id`) REFERENCES `{prefix}ips` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `{prefix}fk_alts_ip_id_player_id_UNIQUE` (`ip_id`, `player_id`),
  CONSTRAINT `{prefix}fk_alts_player_id` FOREIGN KEY (`player_id`) REFERENCES `{prefix}players` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `{prefix}fk_alts_server_id` FOREIGN KEY (`server_id`) REFERENCES `{prefix}servers` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS `{prefix}get_alts_ip`;
DELIMITER ;;
CREATE PROCEDURE `{prefix}get_alts_ip`(`ip_id` BIGINT UNSIGNED, `days` INT)
BEGIN
  DECLARE `from` DATETIME DEFAULT DATE_SUB(CURRENT_TIMESTAMP, INTERVAL `days` DAY);
  SET `days` = IFNULL(`days`, 1);
  SELECT
    `v`.`id`,
    `i`.`ip` AS `ip`,
    `p`.`uuid` AS `player_id`,
    `s`.`uuid` AS `server_id`,
    `s`.`name` AS `server_name`,
    `v`.`count`,
    `v`.`created`,
    `v`.`updated`
  FROM `{prefix}alts` `v`
  JOIN `{prefix}ips` `i` ON `i`.`id` = `v`.`ip_id`
  JOIN `{prefix}players` `p` ON `p`.`id` = `v`.`player_id`
  JOIN `{prefix}servers` `s` ON `s`.`id` = `v`.`server_id`
  WHERE `v`.`updated` >= `from` AND `v`.`ip_id` = `ip_id`;
END ;;
DELIMITER ;

DROP PROCEDURE IF EXISTS `{prefix}get_alts_player`;
DELIMITER ;;
CREATE PROCEDURE `{prefix}get_alts_player`(`player_id` BIGINT UNSIGNED, `days` INT)
BEGIN
  DECLARE `from` DATETIME DEFAULT DATE_SUB(CURRENT_TIMESTAMP, INTERVAL `days` DAY);
  SET `days` = IFNULL(`days`, 1);
  SELECT
    `v`.`id`,
    `i`.`ip` AS `ip`,
    `p`.`uuid` AS `player_id`,
    `s`.`uuid` AS `server_id`,
    `s`.`name` AS `server_name`,
    `v`.`count`,
    `v`.`created`,
    `v`.`updated`
  FROM `{prefix}alts` `v`
  JOIN `{prefix}ips` `i` ON `i`.`id` = `v`.`ip_id`
  JOIN `{prefix}players` `p` ON `p`.`id` = `v`.`player_id`
  JOIN `{prefix}servers` `s` ON `s`.`id` = `v`.`server_id`
  WHERE `v`.`updated` >= `from` AND `v`.`player_id` = `player_id`;
END ;;
DELIMITER ;

DROP PROCEDURE IF EXISTS `{prefix}get_queue_date`;
DELIMITER ;;
CREATE PROCEDURE `{prefix}get_queue_date`(`after` DATETIME)
BEGIN
  SELECT
    `v`.`id`,
    `i`.`ip` AS `ip`,
    `p`.`uuid` AS `player_id`,
    `s`.`uuid` AS `server_id`,
    `s`.`name` AS `server_name`,
    `v`.`count`,
    `v`.`created`,
    `v`.`updated`
  FROM `{prefix}vpn_values` `v`
  JOIN `{prefix}ips` `i` ON `i`.`id` = `v`.`ip_id`
  JOIN `{prefix}players` `p` ON `p`.`id` = `v`.`player_id`
  JOIN `{prefix}servers` `s` ON `s`.`id` = `v`.`server_id`
  WHERE `v`.`updated` > `after`;
END ;;
DELIMITER ;

DROP PROCEDURE IF EXISTS `{prefix}get_queue_id`;
DELIMITER ;;
CREATE PROCEDURE `{prefix}get_queue_id`(`after` BIGINT UNSIGNED)
BEGIN
  SELECT
    `v`.`id`,
    `i`.`ip` AS `ip`,
    `p`.`uuid` AS `player_id`,
    `s`.`uuid` AS `server_id`,
    `s`.`name` AS `server_name`,
    `v`.`count`,
    `v`.`created`,
    `v`.`updated`
  FROM `{prefix}vpn_values` `v`
  JOIN `{prefix}ips` `i` ON `i`.`id` = `v`.`ip_id`
  JOIN `{prefix}players` `p` ON `p`.`id` = `v`.`player_id`
  JOIN `{prefix}servers` `s` ON `s`.`id` = `v`.`server_id`
  WHERE `v`.`id` > `after`;
END ;;
DELIMITER ;

SET FOREIGN_KEY_CHECKS = 1;