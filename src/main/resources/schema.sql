-- Schema for Vehicle Database

-- Brands table
CREATE TABLE IF NOT EXISTS brands (
    manufacturer_wmi VARCHAR(10) NOT NULL PRIMARY KEY,
    commercial_brand_name VARCHAR(100) NOT NULL,
    manufacturer VARCHAR(100) NOT NULL,
    country VARCHAR(2) NOT NULL
);

-- Vehicles table
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    brand_wmi VARCHAR(10) NOT NULL,
    line VARCHAR(100),
    model VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    inception_year INT NOT NULL,
    construction_year INT NOT NULL,
    commercialization TIMESTAMP,
    retirement TIMESTAMP,
    FOREIGN KEY (brand_wmi) REFERENCES brands(manufacturer_wmi)
);

-- Vehicle Market Data table
CREATE TABLE IF NOT EXISTS vehicle_market_data (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    country VARCHAR(2) NOT NULL,
    start_commercializing TIMESTAMP,
    end_commercializing TIMESTAMP,
    commercial_name VARCHAR(100),
    currency VARCHAR(3),
    local_market_price FLOAT NOT NULL,
    market_price FLOAT NOT NULL,
    margin FLOAT NOT NULL,
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
);

-- Vehicle Parts table
CREATE TABLE IF NOT EXISTS vehicle_parts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    part_number VARCHAR(50) NOT NULL,
    name VARCHAR(100),
    mounting_reference VARCHAR(100),
    market_price FLOAT NOT NULL,
    margin FLOAT NOT NULL,
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
);

-- Indexes for Brands
CREATE INDEX IF NOT EXISTS idx_brands_manufacturer ON brands(manufacturer);
CREATE INDEX IF NOT EXISTS idx_brands_country ON brands(country);
CREATE INDEX IF NOT EXISTS idx_brands_commercial_name ON brands(commercial_brand_name);

-- Indexes for Vehicles
CREATE INDEX IF NOT EXISTS idx_vehicles_brand ON vehicles(brand_wmi);
CREATE INDEX IF NOT EXISTS idx_vehicles_type ON vehicles(type);
CREATE INDEX IF NOT EXISTS idx_vehicles_model ON vehicles(model);
CREATE INDEX IF NOT EXISTS idx_vehicles_inception_year ON vehicles(inception_year);

-- Indexes for Vehicle Market Data
CREATE INDEX IF NOT EXISTS idx_vehicle_market_data_vehicle ON vehicle_market_data(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_vehicle_market_data_country ON vehicle_market_data(country);
CREATE INDEX IF NOT EXISTS idx_vehicle_market_data_commercial_name ON vehicle_market_data(commercial_name);

-- Indexes for Vehicle Parts
CREATE INDEX IF NOT EXISTS idx_vehicle_parts_vehicle ON vehicle_parts(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_vehicle_parts_number ON vehicle_parts(part_number);
