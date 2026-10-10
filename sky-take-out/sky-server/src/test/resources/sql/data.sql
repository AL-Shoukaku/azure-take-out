INSERT INTO category(id,type,name,sort,status,create_time,update_time) VALUES
 (1,1,'热菜',2,1,'2026-10-01 10:00:00','2026-10-01 10:00:00'),
 (2,1,'停用分类',1,0,'2026-10-02 10:00:00','2026-10-02 10:00:00'),
 (3,2,'套餐分类',3,1,'2026-10-03 10:00:00','2026-10-03 10:00:00');
INSERT INTO employee(id,name,username,password,phone,sex,id_number,status,create_time) VALUES
 (1,'管理员','admin','e10adc3949ba59abbe56e057f20f883e','13800000000','1','123456789012345678',1,'2026-10-01 10:00:00'),
 (2,'停用员工','disabled','e10adc3949ba59abbe56e057f20f883e','13800000001','0','123456789012345679',0,'2026-10-02 10:00:00');
INSERT INTO dish(id,name,category_id,price,image,description,status,create_time,update_time) VALUES
 (1,'鸡丁',1,12.50,'dish.png','鸡丁说明',1,'2026-10-01 10:00:00','2026-10-02 10:00:00'),
 (2,'米饭',1,8.00,'rice.png','米饭说明',1,'2026-10-02 10:00:00','2026-10-03 10:00:00'),
 (3,'停售菜品',1,10.00,'stopped.png','停售说明',0,'2026-10-03 10:00:00','2026-10-04 10:00:00');
INSERT INTO dish_flavor(id,dish_id,name,`value`) VALUES (1,1,'辣度','["微辣","中辣"]');
INSERT INTO setmeal(id,category_id,name,price,status,create_time) VALUES
 (1,3,'单人套餐',29.90,1,'2026-10-01 10:00:00'),(2,3,'停售套餐',39.90,0,'2026-10-02 10:00:00');
INSERT INTO setmeal_dish(id,setmeal_id,dish_id,name,price,copies) VALUES
 (1,1,1,'鸡丁',12.50,2),(2,1,2,'米饭',8.00,1),(3,2,3,'停售菜品',10.00,1);
INSERT INTO `user`(id,openid,name,create_time) VALUES
 (42,'openid-42','测试用户','2026-10-01 00:00:00'),(43,'openid-43','其他用户','2026-10-02 12:00:00');
INSERT INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES
 (1,42,'张三','13800000000','测试路1号',1),(2,42,'张三','13800000000','测试路2号',0),(3,43,'李四','13800000001','他人地址',1);
INSERT INTO shopping_cart(id,name,user_id,dish_id,dish_flavor,number,amount,create_time) VALUES
 (1,'鸡丁',42,1,'微辣',2,12.50,'2026-10-08 10:00:00'),(2,'米饭',42,2,'',1,8.00,'2026-10-08 10:01:00'),
 (3,'鸡丁',43,1,'微辣',1,12.50,'2026-10-08 10:02:00');
INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,pay_method,pay_status,amount,phone,delivery_status,tableware_status) VALUES
 (1,'order-1',1,42,1,'2026-10-01 00:00:00',1,0,42.00,'13800000000',1,1),
 (2,'order-2',5,42,1,'2026-10-02 12:00:00',1,1,60.00,'13800000000',1,1),
 (3,'order-3',5,43,3,'2026-10-03 12:00:00',1,1,90.00,'13800000001',1,1),
 (4,'order-4',6,42,1,'2026-10-04 12:00:00',1,0,80.00,'13800000000',1,1);
INSERT INTO order_detail(id,order_id,dish_id,name,number,amount) VALUES
 (1,1,1,'鸡丁',2,12.50),(2,2,1,'鸡丁',3,12.50),(3,2,2,'米饭',1,8.00),
 (4,3,1,'鸡丁',2,12.50),(5,4,2,'米饭',100,8.00);
