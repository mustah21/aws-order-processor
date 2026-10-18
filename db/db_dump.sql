CREATE TABLE `Items`(
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `Name` VARCHAR(255) NOT NULL,
    `Description` TEXT NULL,
    `image_url` VARCHAR(255) NULL,
    `price` DECIMAL(8, 2) NOT NULL
);
CREATE TABLE `order_items`(
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `order_id` BIGINT NOT NULL,
    `item_id` INT NOT NULL,
    `quantity` INT NOT NULL
);
CREATE TABLE `Orders`(
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `created_at` DATETIME NOT NULL,
    `user_id` BIGINT NOT NULL
);
CREATE TABLE `Customers`(
    `id` BIGINT UNSIGNED NOT NULL,
    `Username` VARCHAR(255) NOT NULL,
    `Password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    PRIMARY KEY(`id`)
);
CREATE TABLE `Notification`(
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `message` VARCHAR(255) NOT NULL,
    `type` VARCHAR(255) NOT NULL
);
ALTER TABLE
    `Notification` ADD CONSTRAINT `notification_user_id_foreign` FOREIGN KEY(`user_id`) REFERENCES `Customers`(`id`);
ALTER TABLE
    `Items` ADD CONSTRAINT `items_id_foreign` FOREIGN KEY(`id`) REFERENCES `order_items`(`item_id`);
ALTER TABLE
    `Orders` ADD CONSTRAINT `orders_user_id_foreign` FOREIGN KEY(`user_id`) REFERENCES `Customers`(`id`);
ALTER TABLE
    `Orders` ADD CONSTRAINT `orders_id_foreign` FOREIGN KEY(`id`) REFERENCES `order_items`(`order_id`);