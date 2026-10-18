-- Brands
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES
('1HNY', 'Acura', 'Honda Motor Co., Ltd.', 'US'),
('4T1B', 'Alfa Romeo', 'Stellantis', 'IT'),
('WVW', 'Audi', 'Volkswagen Group', 'DE'),
('1G1', 'Buick', 'General Motors', 'US'),
('1G4', 'Cadillac', 'General Motors', 'US'),
('1HGC', 'Chevrolet', 'General Motors', 'US'),
('1HNY', 'Chrysler', 'Stellantis', 'US'),
('SAL', 'Citroen', 'Stellantis', 'FR'),
('WDB', 'Dodge', 'Stellantis', 'US'),
('1FTR', 'Ford', 'Ford Motor Company', 'US'),
('4T3B', 'Genesis', 'Hyundai Motor Group', 'KR'),
('1HG', 'Honda', 'Honda Motor Co., Ltd.', 'JP'),
('SAL', 'Hyundai', 'Hyundai Motor Group', 'KR'),
('1N4', 'Infiniti', 'Nissan Motor Co., Ltd.', 'JP'),
('SAL', 'Jaguar', 'Tata Motors', 'GB'),
('SAL', 'Jeep', 'Stellantis', 'US'),
('KNA', 'Kia', 'Hyundai Motor Group', 'KR'),
('SAL', 'Land Rover', 'Tata Motors', 'GB'),
('WDB', 'Lexus', 'Toyota Motor Corporation', 'JP'),
('WDB', 'Mercedes-Benz', 'Mercedes-Benz Group AG', 'DE'),
('WVW', 'Mini', 'BMW Group', 'DE'),
('1N4', 'Mitsubishi', 'Mitsubishi Motors', 'JP'),
('1N4', 'Nissan', 'Nissan Motor Co., Ltd.', 'JP'),
('SAL', 'Peugeot', 'Stellantis', 'FR'),
('SAL', 'Porsche', 'Volkswagen Group', 'DE'),
('SAL', 'Renault', 'Renault Group', 'FR'),
('SAL', 'Seat', 'Volkswagen Group', 'ES'),
('SAL', 'Skoda', 'Volkswagen Group', 'CZ'),
('1HNY', 'Subaru', 'Subaru Corporation', 'JP'),
('SAL', 'Suzuki', 'Suzuki Motor Corporation', 'JP'),
('1HNY', 'Toyota', 'Toyota Motor Corporation', 'JP'),
('1G1', 'Volkswagen', 'Volkswagen Group', 'DE'),
('1HNY', 'Volvo', 'Geely Auto Group', 'SE');

-- Vehicles
-- Toyota
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HNY', 'Corolla', 'Corolla', 'Sedan', 1966, 1966, '1966-11-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HNY', 'Camry', 'Camry', 'Sedan', 1982, 1982, '1982-03-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HNY', 'RAV4', 'RAV4', 'Suv', 1994, 1994, '1994-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HNY', 'Land Cruiser', 'Land Cruiser', 'OffRoader', 1951, 1951, '1951-01-01T00:00:00Z', NULL);

-- Volkswagen
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1G1', 'Golf', 'Golf', 'Hatchback', 1974, 1974, '1974-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1G1', NULL, 'Passat', 'Sedan', 1973, 1973, '1973-02-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1G1', NULL, 'Tiguan', 'Suv', 2007, 2007, '2007-09-01T00:00:00Z', NULL);

-- Ford
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1FTR', 'Mustang', 'Mustang', 'Coupe', 1964, 1964, '1964-04-17T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1FTR', NULL, 'F-150', 'Pickup', 1948, 1948, '1948-01-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1FTR', NULL, 'Explorer', 'Suv', 1990, 1990, '1990-03-01T00:00:00Z', NULL);

-- Honda
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HG', NULL, 'Civic', 'Hatchback', 1972, 1972, '1972-07-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HG', NULL, 'Accord', 'Sedan', 1976, 1976, '1976-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HG', NULL, 'CR-V', 'Suv', 1995, 1995, '1995-10-01T00:00:00Z', NULL);

-- Hyundai
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('SAL', NULL, 'Tucson', 'Suv', 2004, 2004, '2004-07-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('SAL', NULL, 'Elantra', 'Sedan', 1990, 1990, '1990-08-01T00:00:00Z', NULL);

-- Kia
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('KNA', NULL, 'Sportage', 'Suv', 1993, 1993, '1993-03-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('KNA', NULL, 'Rio', 'Hatchback', 1999, 1999, '1999-11-01T00:00:00Z', NULL);

-- BMW
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('WDB', '3 Series', '320i', 'Sedan', 1975, 1975, '1975-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('WDB', 'X', 'X5', 'Suv', 1999, 1999, '1999-09-01T00:00:00Z', NULL);

-- Mercedes-Benz
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('WDB', 'C-Class', 'C300', 'Sedan', 1993, 1993, '1993-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('WDB', 'M-Class', 'ML350', 'Suv', 1997, 1997, '1997-09-01T00:00:00Z', NULL);

-- Nissan
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1N4', NULL, 'Altima', 'Sedan', 1992, 1992, '1992-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1N4', NULL, 'Rogue', 'Suv', 2007, 2007, '2007-03-01T00:00:00Z', NULL);

-- Tesla
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HNY', NULL, 'Model 3', 'Sedan', 2017, 2017, '2017-07-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES
('1HNY', NULL, 'Model X', 'Suv', 2015, 2015, '2015-09-01T00:00:00Z', NULL);

-- Market Data for some vehicles
-- Toyota Corolla in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES
(1, 'US', '1966-11-01T00:00:00Z', NULL, 'Corolla', 'USD', 20000.0, 22000.0, 10.0);

-- Toyota Corolla in ES
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES
(1, 'ES', '1970-01-01T00:00:00Z', NULL, 'Corolla', 'EUR', 18000.0, 20000.0, 11.11);

-- Toyota RAV4 in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES
(3, 'US', '1994-05-01T00:00:00Z', NULL, 'RAV4', 'USD', 28000.0, 30000.0, 7.14);

-- Ford Mustang in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES
(7, 'US', '1964-04-17T00:00:00Z', NULL, 'Mustang', 'USD', 35000.0, 40000.0, 14.29);

-- Ford F-150 in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES
(8, 'US', '1948-01-01T00:00:00Z', NULL, 'F-150', 'USD', 45000.0, 50000.0, 11.11);

-- Honda Civic in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES
(10, 'US', '1972-07-01T00:00:00Z', NULL, 'Civic', 'USD', 22000.0, 24000.0, 9.09);

-- Parts for Toyota Corolla
INSERT INTO vehicle_parts (vehicle_id, part_number, name, mounting_reference, market_price, margin) VALUES
(1, 'TRD-001', 'Engine Oil Filter', 'Engine Bay', 25.0, 8.0);
INSERT INTO vehicle_parts (vehicle_id, part_number, name, mounting_reference, market_price, margin) VALUES
(1, 'TRD-002', 'Brake Pad Set', 'Front Wheels', 120.0, 40.0);

-- Parts for Ford Mustang
INSERT INTO vehicle_parts (vehicle_id, part_number, name, mounting_reference, market_price, margin) VALUES
(7, 'FRD-001', 'Air Filter', 'Engine Bay', 35.0, 12.0);
INSERT INTO vehicle_parts (vehicle_id, part_number, name, mounting_reference, market_price, margin) VALUES
(7, 'FRD-002', 'Spark Plug Set', 'Engine', 80.0, 25.0);
