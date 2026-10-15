-- 苍穹外卖演示数据。此文件会重建 sky_take_out 全部 11 张表，导入前请备份自己的数据。
-- 菜品与套餐图片保存在 sky-server/src/main/resources/menu-images，来源见 db/IMAGE_SOURCES.md。
-- 演示用户的 openid 为占位值，仅用于后台列表和报表；微信登录会创建真实用户。
CREATE DATABASE IF NOT EXISTS `sky_take_out` DEFAULT CHARACTER SET utf8mb4;
USE `sky_take_out`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `address_book`;
CREATE TABLE `address_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `consignee` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '收货人',
  `sex` varchar(2) COLLATE utf8_bin DEFAULT NULL COMMENT '性别',
  `phone` varchar(11) COLLATE utf8_bin NOT NULL COMMENT '手机号',
  `province_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '区级名称',
  `detail` varchar(200) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '默认 0 否 1是',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='地址簿';

INSERT INTO `address_book` (`id`, `user_id`, `consignee`, `sex`, `phone`, `province_code`, `province_name`, `city_code`, `city_name`, `district_code`, `district_name`, `detail`, `label`, `is_default`) VALUES
  (1, 1, '张同学', '1', '13900000001', '110000', '北京市', '110100', '北京市', '110108', '海淀区', '中关村演示路1号', '学校', 1),
  (2, 1, '张同学', '1', '13900000001', '110000', '北京市', '110100', '北京市', '110108', '海淀区', '学院演示楼2号', '宿舍', 0),
  (3, 2, '李同学', '0', '13900000002', '110000', '北京市', '110100', '北京市', '110105', '朝阳区', '科技演示园3号', '公司', 1),
  (4, 3, '王同学', '1', '13900000003', '310000', '上海市', '310100', '上海市', '310104', '徐汇区', '漕河泾演示路4号', '公司', 1),
  (5, 4, '赵同学', '0', '13900000004', '310000', '上海市', '310100', '上海市', '310110', '杨浦区', '大学演示路5号', '学校', 1),
  (6, 5, '陈同学', '1', '13900000005', '440000', '广东省', '440100', '广州市', '440106', '天河区', '软件演示园6号', '公司', 1);

DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int DEFAULT NULL COMMENT '类型   1 菜品分类 2 套餐分类',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `status` int DEFAULT NULL COMMENT '分类状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_category_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品及套餐分类';

INSERT INTO `category` (`id`, `type`, `name`, `sort`, `status`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
  (1, 1, '川味招牌', 1, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (2, 1, '家常热菜', 2, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (3, 1, '清爽时蔬', 3, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (4, 1, '主食小吃', 4, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (5, 1, '汤羹', 5, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (6, 1, '酒水饮品', 6, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (7, 2, '单人套餐', 7, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (8, 2, '双人套餐', 8, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (9, 2, '家庭套餐', 9, 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1);

DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '菜品名称',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品价格',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `description` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '描述信息',
  `status` int DEFAULT '1' COMMENT '0 停售 1 起售',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_dish_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品';

INSERT INTO `dish` (`id`, `name`, `category_id`, `price`, `image`, `description`, `status`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
  (1, '麻婆豆腐', 1, 26.00, 'http://localhost:8080/menu-images/mapo-tofu.jpg', '豆腐配肉末与花椒，麻辣下饭', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (2, '宫保鸡丁', 1, 32.00, 'http://localhost:8080/menu-images/kung-pao-chicken.jpg', '鸡丁、花生和干辣椒炒制', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (3, '辣子鸡', 1, 38.00, 'http://localhost:8080/menu-images/spicy-chicken.jpg', '酥香鸡块配干辣椒', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (4, '酸菜鱼', 1, 58.00, 'http://localhost:8080/menu-images/pickled-fish.jpg', '鱼片搭配酸菜，酸辣开胃', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (5, '剁椒鱼头', 1, 68.00, 'http://localhost:8080/menu-images/chili-fish-head.jpg', '鲜鱼头铺剁椒蒸制', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (6, '鱼香茄子', 1, 25.00, 'http://localhost:8080/menu-images/eggplant.jpg', '茄子配鱼香汁，酸甜微辣', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (7, '糖醋里脊', 2, 36.00, 'http://localhost:8080/menu-images/sweet-sour-pork.jpg', '外酥里嫩的糖醋猪肉', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (8, '红烧肉', 2, 42.00, 'http://localhost:8080/menu-images/red-braised-pork.jpg', '五花肉慢炖，酱香浓郁', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (9, '番茄炒蛋', 2, 22.00, 'http://localhost:8080/menu-images/tomato-eggs.jpg', '番茄与鸡蛋家常炒制', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (10, '青椒回锅肉', 2, 35.00, 'http://localhost:8080/menu-images/pepper-pork.jpg', '猪肉与青椒回锅炒香', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (11, '手撕包菜炒鸡杂', 3, 18.00, 'http://localhost:8080/menu-images/fried-cabbage.jpg', '包菜和鸡杂大火快炒', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (12, '蒜蓉西兰花', 3, 20.00, 'http://localhost:8080/menu-images/broccoli.jpg', '西兰花搭配蒜蓉清炒', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (13, '干煸四季豆', 3, 24.00, 'http://localhost:8080/menu-images/green-beans.jpg', '四季豆煸香，微辣', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (14, '凉拌黄瓜', 3, 16.00, 'http://localhost:8080/menu-images/cucumber-salad.jpg', '拍黄瓜拌蒜汁', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (15, '炒菌菇', 3, 22.00, 'http://localhost:8080/menu-images/fried-mushrooms.jpg', '菌菇炒香，清淡鲜美', 0, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (16, '蛋炒饭', 4, 18.00, 'http://localhost:8080/menu-images/egg-fried-rice.jpg', '鸡蛋与米饭炒制', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (17, '白米饭', 4, 3.00, 'http://localhost:8080/menu-images/white-rice.jpg', '现蒸白米饭', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (18, '牛肉面', 4, 32.00, 'http://localhost:8080/menu-images/beef-noodles.jpg', '牛肉搭配热汤面', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (19, '鲜肉水饺', 4, 28.00, 'http://localhost:8080/menu-images/dumplings.jpg', '手工水饺，鲜肉馅', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (20, '小笼包', 4, 25.00, 'http://localhost:8080/menu-images/xiaolongbao.jpg', '蒸制小笼包，皮薄馅足', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (21, '鲜肉馄饨', 5, 22.00, 'http://localhost:8080/menu-images/wonton-soup.jpg', '鲜肉馄饨配清汤', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (22, '蛋花汤', 5, 12.00, 'http://localhost:8080/menu-images/egg-drop-soup.jpg', '家常鸡蛋汤', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (23, '酸辣汤', 5, 16.00, 'http://localhost:8080/menu-images/hot-sour-soup.jpg', '酸辣开胃热汤', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (24, '可乐', 6, 8.00, 'http://localhost:8080/menu-images/cola.jpg', '冰镇可乐', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (25, '柠檬茶', 6, 10.00, 'http://localhost:8080/menu-images/lemon-tea.jpg', '柠檬风味茶饮', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (26, '鲜榨橙汁', 6, 15.00, 'http://localhost:8080/menu-images/orange-juice.jpg', '鲜榨橙汁', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (27, '鱼香肉丝', 2, 35.00, 'http://localhost:8080/menu-images/fish-flavored-pork.jpg', '肉丝搭配鱼香酱汁，酸甜微辣', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (28, '红烧排骨', 2, 45.00, 'http://localhost:8080/menu-images/braised-ribs.jpg', '排骨酱烧入味', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (29, '炸酱面', 4, 29.00, 'http://localhost:8080/menu-images/zhajiang-noodles.jpg', '面条搭配肉酱与蔬菜', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (30, '水蒸蛋', 2, 18.00, 'http://localhost:8080/menu-images/steamed-egg.jpg', '鸡蛋慢蒸，口感细嫩', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1);

DROP TABLE IF EXISTS `dish_flavor`;
CREATE TABLE `dish_flavor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint NOT NULL COMMENT '菜品',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '口味名称',
  `value` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '口味数据list',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品口味关系表';

INSERT INTO `dish_flavor` (`id`, `dish_id`, `name`, `value`) VALUES
  (1, 1, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (2, 2, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (3, 3, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (4, 4, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (5, 5, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (6, 6, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (7, 10, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (8, 13, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (9, 27, '辣度', '["不辣","微辣","中辣","重辣"]'),
  (10, 1, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (11, 2, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (12, 3, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (13, 4, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (14, 6, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (15, 7, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (16, 8, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (17, 9, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (18, 10, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (19, 11, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (20, 12, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (21, 13, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (22, 14, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (23, 27, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (24, 28, '忌口', '["不要葱","不要蒜","不要香菜"]'),
  (25, 25, '温度', '["常温","去冰","少冰","多冰"]'),
  (26, 26, '温度', '["常温","去冰","少冰","多冰"]'),
  (27, 25, '甜度', '["无糖","半糖","正常糖"]'),
  (28, 26, '甜度', '["无糖","半糖","正常糖"]');

DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '姓名',
  `username` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '用户名',
  `password` varchar(128) COLLATE utf8_bin NOT NULL COMMENT '密码哈希',
  `phone` varchar(11) COLLATE utf8_bin NOT NULL COMMENT '手机号',
  `sex` varchar(2) COLLATE utf8_bin NOT NULL COMMENT '性别',
  `id_number` varchar(18) COLLATE utf8_bin NOT NULL COMMENT '身份证号',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='员工信息';

-- 两个初始账号的默认密码均为 123456；Argon2id 使用独立随机盐，因此哈希值不同。
INSERT INTO `employee` (`id`, `name`, `username`, `password`, `phone`, `sex`, `id_number`, `status`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
  (1, '管理员', 'admin', '$argon2id$v=19$m=19456,t=2,p=1$BLLbgan81jBCyGynj7vfnQ$7LCmkvr/o8lq0d/ydLf7QrGJ+UdP3SyKxNJgvCFjf6M', '13900000101', '1', '000000000000000001', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (2, '演示店员', 'staff', '$argon2id$v=19$m=19456,t=2,p=1$GGmSfgznQv9IEjHKlTfb7Q$Cq1HPn79F+x2DfWa7GaFpq+i8x2wn+1gjse7k6ljNpE', '13900000102', '0', '000000000000000002', 1, '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1);

DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '名字',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='订单明细表';

INSERT INTO `order_detail` (`id`, `name`, `image`, `order_id`, `dish_id`, `setmeal_id`, `dish_flavor`, `number`, `amount`) VALUES
  (1, '宫保鸡丁单人餐', 'http://localhost:8080/menu-images/setmeal-sweet-pork.jpg', 1, NULL, 1, NULL, 1, 39.00),
  (2, '酸菜鱼', 'http://localhost:8080/menu-images/pickled-fish.jpg', 2, 4, NULL, NULL, 1, 58.00),
  (3, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 2, 17, NULL, NULL, 2, 3.00),
  (4, '糖醋里脊单人餐', 'http://localhost:8080/menu-images/setmeal-sweet-pork.jpg', 3, NULL, 3, NULL, 2, 43.00),
  (5, '牛肉面', 'http://localhost:8080/menu-images/beef-noodles.jpg', 4, 18, NULL, NULL, 1, 32.00),
  (6, '酸菜鱼双人餐', 'http://localhost:8080/menu-images/setmeal-chinese.jpg', 5, NULL, 5, NULL, 1, 88.00),
  (7, '麻婆豆腐', 'http://localhost:8080/menu-images/mapo-tofu.jpg', 6, 1, NULL, NULL, 1, 26.00),
  (8, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 6, 17, NULL, NULL, 1, 3.00),
  (9, '柠檬茶', 'http://localhost:8080/menu-images/lemon-tea.jpg', 6, 25, NULL, NULL, 1, 10.00),
  (10, '川味双人餐', 'http://localhost:8080/menu-images/setmeal-chinese.jpg', 7, NULL, 6, NULL, 1, 85.00),
  (11, '剁椒鱼头', 'http://localhost:8080/menu-images/chili-fish-head.jpg', 8, 5, NULL, NULL, 1, 68.00),
  (12, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 8, 17, NULL, NULL, 2, 3.00),
  (13, '糖醋里脊', 'http://localhost:8080/menu-images/sweet-sour-pork.jpg', 9, 7, NULL, NULL, 2, 36.00),
  (14, '可乐', 'http://localhost:8080/menu-images/cola.jpg', 9, 24, NULL, NULL, 2, 8.00),
  (15, '四人分享餐', 'http://localhost:8080/menu-images/setmeal-family-dinner.jpg', 10, NULL, 8, NULL, 1, 158.00),
  (16, '鲜肉水饺', 'http://localhost:8080/menu-images/dumplings.jpg', 11, 19, NULL, NULL, 1, 28.00),
  (17, '蛋花汤', 'http://localhost:8080/menu-images/egg-drop-soup.jpg', 11, 22, NULL, NULL, 1, 12.00),
  (18, '麻婆豆腐单人餐', 'http://localhost:8080/menu-images/setmeal-braised-pork.jpg', 12, NULL, 2, NULL, 1, 33.00),
  (19, '宫保鸡丁', 'http://localhost:8080/menu-images/kung-pao-chicken.jpg', 13, 2, NULL, NULL, 1, 32.00),
  (20, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 13, 17, NULL, NULL, 1, 3.00),
  (21, '鲜榨橙汁', 'http://localhost:8080/menu-images/orange-juice.jpg', 13, 26, NULL, NULL, 1, 15.00),
  (22, '家常双人餐', 'http://localhost:8080/menu-images/setmeal-sweet-pork.jpg', 14, NULL, 7, NULL, 1, 95.00),
  (23, '小笼包', 'http://localhost:8080/menu-images/xiaolongbao.jpg', 15, 20, NULL, NULL, 1, 25.00),
  (24, '酸辣汤', 'http://localhost:8080/menu-images/hot-sour-soup.jpg', 15, 23, NULL, NULL, 1, 16.00),
  (25, '辣子鸡', 'http://localhost:8080/menu-images/spicy-chicken.jpg', 16, 3, NULL, NULL, 1, 38.00),
  (26, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 16, 17, NULL, NULL, 1, 3.00),
  (27, '酸菜鱼双人餐', 'http://localhost:8080/menu-images/setmeal-chinese.jpg', 17, NULL, 5, NULL, 1, 88.00),
  (28, '红烧肉', 'http://localhost:8080/menu-images/red-braised-pork.jpg', 18, 8, NULL, NULL, 1, 42.00),
  (29, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 18, 17, NULL, NULL, 1, 3.00),
  (30, '宫保鸡丁单人餐', 'http://localhost:8080/menu-images/setmeal-sweet-pork.jpg', 19, NULL, 1, NULL, 1, 39.00),
  (31, '番茄炒蛋', 'http://localhost:8080/menu-images/tomato-eggs.jpg', 20, 9, NULL, NULL, 1, 22.00),
  (32, '蛋炒饭', 'http://localhost:8080/menu-images/egg-fried-rice.jpg', 20, 16, NULL, NULL, 1, 18.00),
  (33, '鱼香茄子', 'http://localhost:8080/menu-images/eggplant.jpg', 21, 6, NULL, NULL, 1, 25.00),
  (34, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 21, 17, NULL, NULL, 1, 3.00),
  (35, '鱼香肉丝', 'http://localhost:8080/menu-images/fish-flavored-pork.jpg', 22, 27, NULL, NULL, 1, 35.00),
  (36, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 22, 17, NULL, NULL, 1, 3.00),
  (37, '红烧排骨', 'http://localhost:8080/menu-images/braised-ribs.jpg', 23, 28, NULL, NULL, 1, 45.00),
  (38, '水蒸蛋', 'http://localhost:8080/menu-images/steamed-egg.jpg', 23, 30, NULL, NULL, 1, 18.00),
  (39, '糖醋里脊单人餐', 'http://localhost:8080/menu-images/setmeal-sweet-pork.jpg', 24, NULL, 3, NULL, 1, 43.00),
  (40, '青椒回锅肉', 'http://localhost:8080/menu-images/pepper-pork.jpg', 25, 10, NULL, NULL, 1, 35.00),
  (41, '白米饭', 'http://localhost:8080/menu-images/white-rice.jpg', 25, 17, NULL, NULL, 1, 3.00),
  (42, '川味双人餐', 'http://localhost:8080/menu-images/setmeal-chinese.jpg', 26, NULL, 6, NULL, 1, 85.00),
  (43, '蛋炒饭', 'http://localhost:8080/menu-images/egg-fried-rice.jpg', 27, 16, NULL, NULL, 1, 18.00),
  (44, '柠檬茶', 'http://localhost:8080/menu-images/lemon-tea.jpg', 27, 25, NULL, NULL, 1, 10.00);

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) COLLATE utf8_bin NOT NULL COMMENT '订单号',
  `status` int NOT NULL DEFAULT '1' COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
  `user_id` bigint NOT NULL COMMENT '下单用户',
  `address_book_id` bigint NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '结账时间',
  `pay_method` int NOT NULL DEFAULT '1' COMMENT '支付方式 1微信,2支付宝',
  `pay_status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态 0未支付 1已支付 2退款',
  `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) COLLATE utf8_bin DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) COLLATE utf8_bin DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '用户名称',
  `consignee` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '配送状态  1立即送出  0选择具体时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '送达时间',
  `pack_amount` int DEFAULT NULL COMMENT '打包费',
  `tableware_number` int DEFAULT NULL COMMENT '餐具数量',
  `tableware_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '餐具数量状态  1按餐量提供  0选择具体数量',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_number` (`number`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='订单表';

INSERT INTO `orders` (`id`, `number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `cancel_reason`, `rejection_reason`, `cancel_time`, `estimated_delivery_time`, `delivery_status`, `delivery_time`, `pack_amount`, `tableware_number`, `tableware_status`) VALUES
  (1, '9a7a7757cbeb51de9bb0656d8c8ef411', 5, 1, 1, DATE_SUB(NOW(), INTERVAL 152 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 152 HOUR), INTERVAL 3 MINUTE), 1, 1, 46.00, NULL, '13900000001', '北京市北京市海淀区中关村演示路1号', '演示用户01', '张同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 152 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 152 HOUR), INTERVAL 45 MINUTE), 1, NULL, 1),
  (2, '20d32b9879b458e5a91084ed2f9575ca', 5, 2, 3, DATE_SUB(NOW(), INTERVAL 140 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 140 HOUR), INTERVAL 3 MINUTE), 1, 1, 73.00, NULL, '13900000002', '北京市北京市朝阳区科技演示园3号', '演示用户02', '李同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 140 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 140 HOUR), INTERVAL 45 MINUTE), 3, NULL, 1),
  (3, '0715087ff2a45f329252d1924a6b28bf', 5, 3, 4, DATE_SUB(NOW(), INTERVAL 126 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 126 HOUR), INTERVAL 3 MINUTE), 1, 1, 94.00, NULL, '13900000003', '上海市上海市徐汇区漕河泾演示路4号', '演示用户03', '王同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 126 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 126 HOUR), INTERVAL 45 MINUTE), 2, NULL, 1),
  (4, 'a6960badc589583c91a64234ce061a23', 6, 4, 5, DATE_SUB(NOW(), INTERVAL 110 HOUR), NULL, 1, 0, 39.00, NULL, '13900000004', '上海市上海市杨浦区大学演示路5号', '演示用户04', '赵同学', '用户取消', NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 110 HOUR), INTERVAL 12 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 110 HOUR), INTERVAL 50 MINUTE), 1, NULL, 1, NULL, 1),
  (5, '9afc599efcfd5938aab118cc71366a52', 5, 5, 6, DATE_SUB(NOW(), INTERVAL 102 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 102 HOUR), INTERVAL 3 MINUTE), 1, 1, 95.00, NULL, '13900000005', '广东省广州市天河区软件演示园6号', '演示用户05', '陈同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 102 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 102 HOUR), INTERVAL 45 MINUTE), 1, NULL, 1),
  (6, '52a48a4fdd355113a5df53c0aaa1f135', 5, 1, 1, DATE_SUB(NOW(), INTERVAL 95 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 95 HOUR), INTERVAL 3 MINUTE), 1, 1, 48.00, '少放辣', '13900000001', '北京市北京市海淀区中关村演示路1号', '演示用户01', '张同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 95 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 95 HOUR), INTERVAL 45 MINUTE), 3, NULL, 1),
  (7, 'dd51114f81075fc2ad7d43972f04d1f0', 5, 2, 3, DATE_SUB(NOW(), INTERVAL 84 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 84 HOUR), INTERVAL 3 MINUTE), 1, 1, 92.00, NULL, '13900000002', '北京市北京市朝阳区科技演示园3号', '演示用户02', '李同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 84 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 84 HOUR), INTERVAL 45 MINUTE), 1, NULL, 1),
  (8, '5b39f53586215db289efc921da104f16', 7, 3, 4, DATE_SUB(NOW(), INTERVAL 79 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 79 HOUR), INTERVAL 3 MINUTE), 1, 2, 83.00, NULL, '13900000003', '上海市上海市徐汇区漕河泾演示路4号', '演示用户03', '王同学', '商家退款', NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 79 HOUR), INTERVAL 12 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 79 HOUR), INTERVAL 50 MINUTE), 1, NULL, 3, NULL, 1),
  (9, '65d867bd219f53fbb314a22f16463918', 5, 4, 5, DATE_SUB(NOW(), INTERVAL 72 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 72 HOUR), INTERVAL 3 MINUTE), 1, 1, 98.00, NULL, '13900000004', '上海市上海市杨浦区大学演示路5号', '演示用户04', '赵同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 72 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 72 HOUR), INTERVAL 45 MINUTE), 4, NULL, 1),
  (10, 'b0d00f512351591b921ef27108c13da6', 5, 5, 6, DATE_SUB(NOW(), INTERVAL 63 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 63 HOUR), INTERVAL 3 MINUTE), 1, 1, 165.00, NULL, '13900000005', '广东省广州市天河区软件演示园6号', '演示用户05', '陈同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 63 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 63 HOUR), INTERVAL 45 MINUTE), 1, NULL, 1),
  (11, 'e31963b78e4050fda7e0fc2cf32c6ed7', 6, 1, 1, DATE_SUB(NOW(), INTERVAL 56 HOUR), NULL, 1, 0, 48.00, NULL, '13900000001', '北京市北京市海淀区中关村演示路1号', '演示用户01', '张同学', '用户取消', NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 56 HOUR), INTERVAL 12 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 56 HOUR), INTERVAL 50 MINUTE), 1, NULL, 2, NULL, 1),
  (12, '977931e1bcca5aa3aac8447ae634fade', 5, 2, 3, DATE_SUB(NOW(), INTERVAL 51 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 51 HOUR), INTERVAL 3 MINUTE), 1, 1, 40.00, '少放辣', '13900000002', '北京市北京市朝阳区科技演示园3号', '演示用户02', '李同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 51 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 51 HOUR), INTERVAL 45 MINUTE), 1, NULL, 1),
  (13, 'da5cfba2686053b2854c774f4dc3cdf5', 5, 3, 4, DATE_SUB(NOW(), INTERVAL 43 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 43 HOUR), INTERVAL 3 MINUTE), 1, 1, 59.00, NULL, '13900000003', '上海市上海市徐汇区漕河泾演示路4号', '演示用户03', '王同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 43 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 43 HOUR), INTERVAL 45 MINUTE), 3, NULL, 1),
  (14, '796196b90b365193aa907aabb6593006', 5, 4, 5, DATE_SUB(NOW(), INTERVAL 38 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 38 HOUR), INTERVAL 3 MINUTE), 1, 1, 102.00, NULL, '13900000004', '上海市上海市杨浦区大学演示路5号', '演示用户04', '赵同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 38 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 38 HOUR), INTERVAL 45 MINUTE), 1, NULL, 1),
  (15, 'bc3b136d5c4758caaaadea1ef2e39286', 5, 5, 6, DATE_SUB(NOW(), INTERVAL 33 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 33 HOUR), INTERVAL 3 MINUTE), 1, 1, 49.00, NULL, '13900000005', '广东省广州市天河区软件演示园6号', '演示用户05', '陈同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 33 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 33 HOUR), INTERVAL 45 MINUTE), 2, NULL, 1),
  (16, 'be4274b616125fcbb4e6042fc355b4f8', 6, 1, 1, DATE_SUB(NOW(), INTERVAL 28 HOUR), NULL, 1, 0, 49.00, NULL, '13900000001', '北京市北京市海淀区中关村演示路1号', '演示用户01', '张同学', '用户取消', NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 28 HOUR), INTERVAL 12 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 28 HOUR), INTERVAL 50 MINUTE), 1, NULL, 2, NULL, 1),
  (17, '5adf22adfe4d52c8921d497f49616bb9', 5, 2, 3, DATE_SUB(NOW(), INTERVAL 25 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 HOUR), INTERVAL 3 MINUTE), 1, 1, 95.00, NULL, '13900000002', '北京市北京市朝阳区科技演示园3号', '演示用户02', '李同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 HOUR), INTERVAL 45 MINUTE), 1, NULL, 1),
  (18, 'de9eff5301f95775b5d266ba8b8f6c3e', 5, 3, 4, DATE_SUB(NOW(), INTERVAL 22 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 22 HOUR), INTERVAL 3 MINUTE), 1, 1, 53.00, '少放辣', '13900000003', '上海市上海市徐汇区漕河泾演示路4号', '演示用户03', '王同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 22 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 22 HOUR), INTERVAL 45 MINUTE), 2, NULL, 1),
  (19, '9ecebab959695f6aaa9a40b1c69012ac', 5, 4, 5, DATE_SUB(NOW(), INTERVAL 19 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 19 HOUR), INTERVAL 3 MINUTE), 1, 1, 46.00, NULL, '13900000004', '上海市上海市杨浦区大学演示路5号', '演示用户04', '赵同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 19 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 19 HOUR), INTERVAL 45 MINUTE), 1, NULL, 1),
  (20, '626388de007f517eb1b8134f275fb1e8', 5, 5, 6, DATE_SUB(NOW(), INTERVAL 15 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 15 HOUR), INTERVAL 3 MINUTE), 1, 1, 48.00, NULL, '13900000005', '广东省广州市天河区软件演示园6号', '演示用户05', '陈同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 15 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 15 HOUR), INTERVAL 45 MINUTE), 2, NULL, 1),
  (21, 'a7507375195155c99f4e06ed765a712e', 5, 1, 1, DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 3 MINUTE), 1, 1, 36.00, NULL, '13900000001', '北京市北京市海淀区中关村演示路1号', '演示用户01', '张同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 45 MINUTE), 2, NULL, 1),
  (22, '62410308728a56c7acb4ad14fe9d9535', 5, 2, 3, DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 8 HOUR), INTERVAL 3 MINUTE), 1, 1, 46.00, NULL, '13900000002', '北京市北京市朝阳区科技演示园3号', '演示用户02', '李同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 8 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 8 HOUR), INTERVAL 45 MINUTE), 2, NULL, 1),
  (23, '18380d80054654fa861c751f72d8a562', 5, 3, 4, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 HOUR), INTERVAL 3 MINUTE), 1, 1, 71.00, NULL, '13900000003', '上海市上海市徐汇区漕河泾演示路4号', '演示用户03', '王同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 HOUR), INTERVAL 50 MINUTE), 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 HOUR), INTERVAL 45 MINUTE), 2, NULL, 1),
  (24, '5e4620e3cc5c5931b9382f8356256879', 2, 2, 3, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 2 HOUR), INTERVAL 3 MINUTE), 1, 1, 50.00, '少放辣', '13900000002', '北京市北京市朝阳区科技演示园3号', '演示用户02', '李同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 2 HOUR), INTERVAL 50 MINUTE), 1, NULL, 1, NULL, 1),
  (25, '02fcf7b73cb15f3988c40aaa11fb45ad', 3, 3, 4, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 HOUR), INTERVAL 3 MINUTE), 1, 1, 46.00, NULL, '13900000003', '上海市上海市徐汇区漕河泾演示路4号', '演示用户03', '王同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 HOUR), INTERVAL 50 MINUTE), 1, NULL, 2, NULL, 1),
  (26, '2383b61827845eee98c0082ea9cbc354', 4, 4, 5, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 HOUR), INTERVAL 3 MINUTE), 1, 1, 92.00, NULL, '13900000004', '上海市上海市杨浦区大学演示路5号', '演示用户04', '赵同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 HOUR), INTERVAL 50 MINUTE), 1, NULL, 1, NULL, 1),
  (27, '31ca6a72fc7e544bb3dce15f5eb746ba', 1, 5, 6, DATE_SUB(NOW(), INTERVAL 5 MINUTE), NULL, 1, 0, 36.00, NULL, '13900000005', '广东省广州市天河区软件演示园6号', '演示用户05', '陈同学', NULL, NULL, NULL, DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 MINUTE), INTERVAL 50 MINUTE), 1, NULL, 2, NULL, 1);

DROP TABLE IF EXISTS `setmeal`;
CREATE TABLE `setmeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '套餐名称',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `status` int DEFAULT '1' COMMENT '售卖状态 0:停售 1:起售',
  `description` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '描述信息',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_setmeal_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='套餐';

INSERT INTO `setmeal` (`id`, `category_id`, `name`, `price`, `status`, `description`, `image`, `create_time`, `update_time`, `create_user`, `update_user`) VALUES
  (1, 7, '宫保鸡丁单人餐', 39.00, 1, '宫保鸡丁、米饭、蛋花汤；图片为套餐示意图', 'http://localhost:8080/menu-images/setmeal-sweet-pork.jpg', '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (2, 7, '麻婆豆腐单人餐', 33.00, 1, '麻婆豆腐、米饭、柠檬茶；图片为套餐示意图', 'http://localhost:8080/menu-images/setmeal-braised-pork.jpg', '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (3, 7, '糖醋里脊单人餐', 43.00, 1, '糖醋里脊、米饭、蛋花汤；图片为套餐示意图', 'http://localhost:8080/menu-images/setmeal-sweet-pork.jpg', '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (4, 7, '红烧肉单人餐', 52.00, 0, '红烧肉、米饭、酸辣汤；停售示例', 'http://localhost:8080/menu-images/setmeal-braised-pork.jpg', '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (5, 8, '酸菜鱼双人餐', 88.00, 1, '酸菜鱼、凉拌黄瓜、米饭两份、柠檬茶两杯；图片为套餐示意图', 'http://localhost:8080/menu-images/setmeal-chinese.jpg', '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (6, 8, '川味双人餐', 85.00, 1, '宫保鸡丁、辣子鸡、米饭两份、柠檬茶两杯；图片为套餐示意图', 'http://localhost:8080/menu-images/setmeal-chinese.jpg', '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (7, 8, '家常双人餐', 95.00, 1, '糖醋里脊、番茄炒蛋、米饭两份、鲜肉馄饨两份；图片为套餐示意图', 'http://localhost:8080/menu-images/setmeal-sweet-pork.jpg', '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1),
  (8, 9, '四人分享餐', 158.00, 1, '酸菜鱼、糖醋里脊、包菜、西兰花、米饭四份、可乐四杯；图片为套餐示意图', 'http://localhost:8080/menu-images/setmeal-family-dinner.jpg', '2026-09-28 12:00:00', '2026-09-28 12:00:00', 1, 1);

DROP TABLE IF EXISTS `setmeal_dish`;
CREATE TABLE `setmeal_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '菜品名称 （冗余字段）',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品单价（冗余字段）',
  `copies` int DEFAULT NULL COMMENT '菜品份数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='套餐菜品关系';

INSERT INTO `setmeal_dish` (`id`, `setmeal_id`, `dish_id`, `name`, `price`, `copies`) VALUES
  (1, 1, 2, '宫保鸡丁', 32.00, 1),
  (2, 1, 17, '白米饭', 3.00, 1),
  (3, 1, 22, '蛋花汤', 12.00, 1),
  (4, 2, 1, '麻婆豆腐', 26.00, 1),
  (5, 2, 17, '白米饭', 3.00, 1),
  (6, 2, 25, '柠檬茶', 10.00, 1),
  (7, 3, 7, '糖醋里脊', 36.00, 1),
  (8, 3, 17, '白米饭', 3.00, 1),
  (9, 3, 22, '蛋花汤', 12.00, 1),
  (10, 4, 8, '红烧肉', 42.00, 1),
  (11, 4, 17, '白米饭', 3.00, 1),
  (12, 4, 23, '酸辣汤', 16.00, 1),
  (13, 5, 4, '酸菜鱼', 58.00, 1),
  (14, 5, 14, '凉拌黄瓜', 16.00, 1),
  (15, 5, 17, '白米饭', 3.00, 2),
  (16, 5, 25, '柠檬茶', 10.00, 2),
  (17, 6, 2, '宫保鸡丁', 32.00, 1),
  (18, 6, 3, '辣子鸡', 38.00, 1),
  (19, 6, 17, '白米饭', 3.00, 2),
  (20, 6, 25, '柠檬茶', 10.00, 2),
  (21, 7, 7, '糖醋里脊', 36.00, 1),
  (22, 7, 9, '番茄炒蛋', 22.00, 1),
  (23, 7, 17, '白米饭', 3.00, 2),
  (24, 7, 21, '鲜肉馄饨', 22.00, 2),
  (25, 8, 4, '酸菜鱼', 58.00, 1),
  (26, 8, 7, '糖醋里脊', 36.00, 1),
  (27, 8, 11, '手撕包菜炒鸡杂', 18.00, 1),
  (28, 8, 12, '蒜蓉西兰花', 20.00, 1),
  (29, 8, 17, '白米饭', 3.00, 4),
  (30, 8, 24, '可乐', 8.00, 4);

DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '商品名称',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `user_id` bigint NOT NULL COMMENT '主键',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='购物车';

INSERT INTO `shopping_cart` (`id`, `name`, `image`, `user_id`, `dish_id`, `setmeal_id`, `dish_flavor`, `number`, `amount`, `create_time`) VALUES
  (1, '凉拌黄瓜', 'http://localhost:8080/menu-images/cucumber-salad.jpg', 1, 14, NULL, '不要蒜', 1, 16.00, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
  (2, '四人分享餐', 'http://localhost:8080/menu-images/setmeal-family-dinner.jpg', 2, NULL, 8, NULL, 1, 158.00, DATE_SUB(NOW(), INTERVAL 1 HOUR));

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` varchar(45) COLLATE utf8_bin DEFAULT NULL COMMENT '微信用户唯一标识',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '姓名',
  `phone` varchar(11) COLLATE utf8_bin DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) COLLATE utf8_bin DEFAULT NULL COMMENT '性别',
  `id_number` varchar(18) COLLATE utf8_bin DEFAULT NULL COMMENT '身份证号',
  `avatar` varchar(500) COLLATE utf8_bin DEFAULT NULL COMMENT '头像',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='用户信息';

INSERT INTO `user` (`id`, `openid`, `name`, `phone`, `sex`, `id_number`, `avatar`, `create_time`) VALUES
  (1, 'demo_openid_01', '演示用户01', '13900000001', '1', NULL, NULL, '2026-09-28 12:00:00'),
  (2, 'demo_openid_02', '演示用户02', '13900000002', '0', NULL, NULL, '2026-09-28 12:00:00'),
  (3, 'demo_openid_03', '演示用户03', '13900000003', '1', NULL, NULL, '2026-09-28 12:00:00'),
  (4, 'demo_openid_04', '演示用户04', '13900000004', '0', NULL, NULL, '2026-09-28 12:00:00'),
  (5, 'demo_openid_05', '演示用户05', '13900000005', '1', NULL, NULL, '2026-09-28 12:00:00');
