
DROP TABLE IF EXISTS `t_oplog`;
CREATE TABLE `t_oplog` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `_operator_type` tinyint NOT NULL,
  `_operator_id` varchar(64) NOT NULL,
  `_client_id` varchar(64) DEFAULT NULL,
  `_operation` varchar(128) NOT NULL,
  `_target` varchar(256) DEFAULT NULL,
  `_data` text,
  `_ip` varchar(64) DEFAULT NULL,
  `_result` int(11) NOT NULL DEFAULT 0,
  `_dt` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_oplog_operator` (`_operator_type`, `_operator_id`),
  INDEX `idx_oplog_dt` (`_dt`)
);
