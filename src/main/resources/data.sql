-- Brands
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('1HNY', 'Acura', 'Honda Motor Co., Ltd.', 'US');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('ARO', 'Alfa Romeo', 'Stellantis', 'IT');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('AUD', 'Audi', 'Volkswagen Group', 'DE');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('BUI', 'Buick', 'General Motors', 'US');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('CAD', 'Cadillac', 'General Motors', 'US');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('CHV', 'Chevrolet', 'General Motors', 'US');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('CHR', 'Chrysler', 'Stellantis', 'US');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('CIT', 'Citroen', 'Stellantis', 'FR');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('DOD', 'Dodge', 'Stellantis', 'US');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('FRD', 'Ford', 'Ford Motor Company', 'US');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('GEN', 'Genesis', 'Hyundai Motor Group', 'KR');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('HON', 'Honda', 'Honda Motor Co., Ltd.', 'JP');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('HYU', 'Hyundai', 'Hyundai Motor Group', 'KR');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('INF', 'Infiniti', 'Nissan Motor Co., Ltd.', 'JP');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('JAG', 'Jaguar', 'Tata Motors', 'GB');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('JEE', 'Jeep', 'Stellantis', 'US');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('KIA', 'Kia', 'Hyundai Motor Group', 'KR');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('LNR', 'Land Rover', 'Tata Motors', 'GB');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('LEX', 'Lexus', 'Toyota Motor Corporation', 'JP');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('MBZ', 'Mercedes-Benz', 'Mercedes-Benz Group AG', 'DE');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('MIN', 'Mini', 'BMW Group', 'DE');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('MIT', 'Mitsubishi', 'Mitsubishi Motors', 'JP');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('NIS', 'Nissan', 'Nissan Motor Co., Ltd.', 'JP');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('PEU', 'Peugeot', 'Stellantis', 'FR');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('POR', 'Porsche', 'Volkswagen Group', 'DE');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('REN', 'Renault', 'Renault Group', 'FR');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('SEA', 'Seat', 'Volkswagen Group', 'ES');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('SKD', 'Skoda', 'Volkswagen Group', 'CZ');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('SUB', 'Subaru', 'Subaru Corporation', 'JP');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('SUZ', 'Suzuki', 'Suzuki Motor Corporation', 'JP');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('TOY', 'Toyota', 'Toyota Motor Corporation', 'JP');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('VWG', 'Volkswagen', 'Volkswagen Group', 'DE');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('VOL', 'Volvo', 'Geely Auto Group', 'SE');
INSERT INTO brands (manufacturer_wmi, commercial_brand_name, manufacturer, country) VALUES ('TES', 'Tesla', 'Tesla, Inc.', 'US');

-- Vehicles
-- Toyota
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('TOY', 'Corolla', 'Corolla', 'Sedan', 1966, 1966, '1966-11-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('TOY', 'Camry', 'Camry', 'Sedan', 1982, 1982, '1982-03-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('TOY', 'RAV4', 'RAV4', 'Suv', 1994, 1994, '1994-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('TOY', 'Land Cruiser', 'Land Cruiser', 'OffRoader', 1951, 1951, '1951-01-01T00:00:00Z', NULL);

-- Volkswagen
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('VWG', 'Golf', 'Golf', 'Hatchback', 1974, 1974, '1974-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('VWG', NULL, 'Passat', 'Sedan', 1973, 1973, '1973-02-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('VWG', NULL, 'Tiguan', 'Suv', 2007, 2007, '2007-09-01T00:00:00Z', NULL);

-- Ford
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('FRD', 'Mustang', 'Mustang', 'Coupe', 1964, 1964, '1964-04-17T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('FRD', NULL, 'F-150', 'Pickup', 1948, 1948, '1948-01-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('FRD', NULL, 'Explorer', 'Suv', 1990, 1990, '1990-03-01T00:00:00Z', NULL);

-- Honda
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('HON', NULL, 'Civic', 'Hatchback', 1972, 1972, '1972-07-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('HON', NULL, 'Accord', 'Sedan', 1976, 1976, '1976-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('HON', NULL, 'CR-V', 'Suv', 1995, 1995, '1995-10-01T00:00:00Z', NULL);

-- Hyundai
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('HYU', NULL, 'Tucson', 'Suv', 2004, 2004, '2004-07-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('HYU', NULL, 'Elantra', 'Sedan', 1990, 1990, '1990-08-01T00:00:00Z', NULL);

-- Kia
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('KIA', NULL, 'Sportage', 'Suv', 1993, 1993, '1993-03-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('KIA', NULL, 'Rio', 'Hatchback', 1999, 1999, '1999-11-01T00:00:00Z', NULL);

-- BMW (using Mini's WMI for BMW models)
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('MIN', '3 Series', '320i', 'Sedan', 1975, 1975, '1975-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('MIN', 'X', 'X5', 'Suv', 1999, 1999, '1999-09-01T00:00:00Z', NULL);

-- Mercedes-Benz
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('MBZ', 'C-Class', 'C300', 'Sedan', 1993, 1993, '1993-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('MBZ', 'M-Class', 'ML350', 'Suv', 1997, 1997, '1997-09-01T00:00:00Z', NULL);

-- Nissan
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('NIS', NULL, 'Altima', 'Sedan', 1992, 1992, '1992-05-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('NIS', NULL, 'Rogue', 'Suv', 2007, 2007, '2007-03-01T00:00:00Z', NULL);

-- Tesla
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('TES', NULL, 'Model 3', 'Sedan', 2017, 2017, '2017-07-01T00:00:00Z', NULL);
INSERT INTO vehicles (brand_wmi, line, model, type, inception_year, construction_year, commercialization, retirement) VALUES ('TES', NULL, 'Model X', 'Suv', 2015, 2015, '2015-09-01T00:00:00Z', NULL);

-- Market Data for some vehicles
-- Toyota Corolla in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES (1, 'US', '1966-11-01T00:00:00Z', NULL, 'Corolla', 'USD', 20000.0, 22000.0, 10.0);

-- Toyota Corolla in ES
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES (1, 'ES', '1970-01-01T00:00:00Z', NULL, 'Corolla', 'EUR', 18000.0, 20000.0, 11.11);

-- Toyota RAV4 in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES (3, 'US', '1994-05-01T00:00:00Z', NULL, 'RAV4', 'USD', 28000.0, 30000.0, 7.14);

-- Ford Mustang in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES (7, 'US', '1964-04-17T00:00:00Z', NULL, 'Mustang', 'USD', 35000.0, 40000.0, 14.29);

-- Ford F-150 in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES (8, 'US', '1948-01-01T00:00:00Z', NULL, 'F-150', 'USD', 45000.0, 50000.0, 11.11);

-- Honda Civic in US
INSERT INTO vehicle_market_data (vehicle_id, country, start_commercializing, end_commercializing, commercial_name, currency, local_market_price, market_price, margin) VALUES (10, 'US', '1972-07-01T00:00:00Z', NULL, 'Civic', 'USD', 22000.0, 24000.0, 9.09);

-- Parts for Toyota Corolla
INSERT INTO vehicle_parts (vehicle_id, part_number, name, mounting_reference, market_price, margin) VALUES (1, 'TRD-001', 'Engine Oil Filter', 'Engine Bay', 25.0, 8.0);
INSERT INTO vehicle_parts (vehicle_id, part_number, name, mounting_reference, market_price, margin) VALUES (1, 'TRD-002', 'Brake Pad Set', 'Front Wheels', 120.0, 40.0);

-- Parts for Ford Mustang
INSERT INTO vehicle_parts (vehicle_id, part_number, name, mounting_reference, market_price, margin) VALUES (7, 'FRD-001', 'Air Filter', 'Engine Bay', 35.0, 12.0);
INSERT INTO vehicle_parts (vehicle_id, part_number, name, mounting_reference, market_price, margin) VALUES (7, 'FRD-002', 'Spark Plug Set', 'Engine', 80.0, 25.0);
