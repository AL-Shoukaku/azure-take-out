-- Snapshot of db/azure.sql DDL. Only MySQL collation/engine/comment syntax is removed for H2.
DROP ALL OBJECTS;
CREATE TABLE `coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` tinyint NOT NULL,
  `name` varchar(32) NOT NULL,
  `stock` int NOT NULL,
  `total_stock` int NOT NULL,
  `discount_amount` decimal(10,2) NOT NULL,
  `threshold_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00',
  `start_time` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `valid_time` int NOT NULL,
  `end_time` datetime NOT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_coupon_status_time` (`status`, `start_time`, `end_time`)
);

CREATE TABLE `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `number` varchar(50) NOT NULL,
  `type` tinyint NOT NULL,
  `name` varchar(32) NOT NULL,
  `discount_amount` decimal(10,2) NOT NULL,
  `threshold_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `status` tinyint NOT NULL DEFAULT '0',
  `lock_order_id` bigint DEFAULT NULL,
  `expire_time` datetime NOT NULL,
  `create_time` datetime NOT NULL,
  `use_time` datetime DEFAULT NULL,
  `refund_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_coupon_number` (`number`),
  UNIQUE KEY `uk_user_coupon_user_template` (`user_id`, `coupon_id`),
  KEY `idx_user_coupon_user_status_expire` (`user_id`, `status`, `expire_time`),
  KEY `idx_user_coupon_status_expire` (`status`, `expire_time`),
  KEY `idx_user_coupon_lock_order` (`lock_order_id`)
);

CREATE TABLE `coupon_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `number` varchar(50) NOT NULL,
  `coupon_number` varchar(50) NOT NULL,
  `username` varchar(32) NOT NULL,
  `coupon_name` varchar(32) NOT NULL,
  `type` tinyint NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `discount_amount` decimal(10,2) NOT NULL,
  `threshold_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `status` tinyint NOT NULL DEFAULT '0',
  `pay_deadline` datetime NOT NULL,
  `create_time` datetime NOT NULL,
  `pay_time` datetime DEFAULT NULL,
  `cancel_time` datetime DEFAULT NULL,
  `refund_time` datetime DEFAULT NULL,
  `cancel_reason` varchar(255) DEFAULT NULL,
  `refund_reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_order_number` (`number`),
  UNIQUE KEY `uk_coupon_order_coupon_number` (`coupon_number`),
  KEY `idx_coupon_order_user_template_status` (`user_id`, `coupon_id`, `status`),
  KEY `idx_coupon_order_status_deadline` (`status`, `pay_deadline`)
);

CREATE TABLE `address_book` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `consignee` varchar(50) DEFAULT NULL,
  `sex` varchar(2) DEFAULT NULL,
  `phone` varchar(11) NOT NULL,
  `province_code` varchar(12) DEFAULT NULL,
  `province_name` varchar(32) DEFAULT NULL,
  `city_code` varchar(12) DEFAULT NULL,
  `city_name` varchar(32) DEFAULT NULL,
  `district_code` varchar(12) DEFAULT NULL,
  `district_name` varchar(32) DEFAULT NULL,
  `detail` varchar(200) DEFAULT NULL,
  `label` varchar(100) DEFAULT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
);

CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` int DEFAULT NULL,
  `name` varchar(32) NOT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `status` int DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `idx_category_name` UNIQUE (`name`)
);

CREATE TABLE `dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `category_id` bigint NOT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `status` int DEFAULT '1',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `idx_dish_name` UNIQUE (`name`)
);

CREATE TABLE `dish_flavor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dish_id` bigint NOT NULL,
  `name` varchar(32) DEFAULT NULL,
  `value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `username` varchar(32) NOT NULL,
  `password` varchar(128) NOT NULL,
  `phone` varchar(11) NOT NULL,
  `sex` varchar(2) NOT NULL,
  `id_number` varchar(18) NOT NULL,
  `status` int NOT NULL DEFAULT '1',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `idx_username` UNIQUE (`username`)
);

CREATE TABLE `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `dish_id` bigint DEFAULT NULL,
  `setmeal_id` bigint DEFAULT NULL,
  `dish_flavor` varchar(50) DEFAULT NULL,
  `number` int NOT NULL DEFAULT '1',
  `amount` decimal(10,2) NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `number` varchar(50) NOT NULL,
  `status` int NOT NULL DEFAULT '1',
  `user_id` bigint NOT NULL,
  `address_book_id` bigint NOT NULL,
  `order_time` datetime NOT NULL,
  `checkout_time` datetime DEFAULT NULL,
  `pay_method` int NOT NULL DEFAULT '1',
  `pay_status` tinyint NOT NULL DEFAULT '0',
  `amount` decimal(10,2) NOT NULL,
  `remark` varchar(100) DEFAULT NULL,
  `phone` varchar(11) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `user_name` varchar(32) DEFAULT NULL,
  `consignee` varchar(32) DEFAULT NULL,
  `cancel_reason` varchar(255) DEFAULT NULL,
  `rejection_reason` varchar(255) DEFAULT NULL,
  `cancel_time` datetime DEFAULT NULL,
  `estimated_delivery_time` datetime DEFAULT NULL,
  `delivery_status` tinyint NOT NULL DEFAULT '1',
  `delivery_time` datetime DEFAULT NULL,
  `pack_amount` int DEFAULT NULL,
  `tableware_number` int DEFAULT NULL,
  `tableware_status` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_orders_number` UNIQUE (`number`)
);

CREATE TABLE `setmeal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL,
  `name` varchar(32) NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `status` int DEFAULT '1',
  `description` varchar(255) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `idx_setmeal_name` UNIQUE (`name`)
);

CREATE TABLE `setmeal_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `setmeal_id` bigint DEFAULT NULL,
  `dish_id` bigint DEFAULT NULL,
  `name` varchar(32) DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `copies` int DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `shopping_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `dish_id` bigint DEFAULT NULL,
  `setmeal_id` bigint DEFAULT NULL,
  `dish_flavor` varchar(50) DEFAULT NULL,
  `number` int NOT NULL DEFAULT '1',
  `amount` decimal(10,2) NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account` varchar(32) NOT NULL UNIQUE,
  `password` varchar(128) NOT NULL,
  `username` varchar(32) NOT NULL,
  `status` int NOT NULL DEFAULT '1',
  `name` varchar(32) DEFAULT NULL,
  `phone` varchar(11) DEFAULT NULL,
  `sex` varchar(2) DEFAULT NULL,
  `id_number` varchar(18) DEFAULT NULL,
  `avatar` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
);
