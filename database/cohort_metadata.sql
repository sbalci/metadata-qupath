-- SQL schema for storing cohort metadata
CREATE TABLE cohort_metadata (
    id SERIAL PRIMARY KEY,
    image_name VARCHAR(255) NOT NULL,
    project_name VARCHAR(255),
    width_pixels INTEGER,
    height_pixels INTEGER,
    pixel_width_um DECIMAL(10,6),
    pixel_height_um DECIMAL(10,6),
    estimated_magnification INTEGER,
    scanner VARCHAR(100),
    scan_date TIMESTAMP,
    file_size_mb DECIMAL(10,2),
    area_mm2 DECIMAL(10,2),
    extraction_date TIMESTAMP,
    file_path TEXT,
    INDEX idx_scanner (scanner),
    INDEX idx_magnification (estimated_magnification),
    INDEX idx_area (area_mm2)
);