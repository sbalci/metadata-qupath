# Changelog

All notable changes to the QuPath Cohort Metadata Extractor will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2025-06-02

### Added
- **QuPath 0.6+ Compatibility**: Full support for QuPath 0.6.0-rc3 and later versions
- **Enhanced Metadata Extraction**: Now captures 50+ metadata fields including:
  - Detailed scanner information (scanner type, ScanScope ID, apparent magnification)
  - Comprehensive timing data (scan date, time, timezone)
  - Acquisition settings (exposure scale, exposure time, gamma, filtering)
  - Stage position data (left, top coordinates)
  - Technical specifications (stripe width, compression details, tile organization)
  - Quality indicators (scan warnings, BigTIFF support, ICC profiles)
- **Improved Error Handling**: Better exception management and detailed error logging
- **Performance Monitoring**: Execution time tracking and performance statistics
- **Quality Control Metrics**: Automated quality assessment for each image
- **Analysis Recommendations**: Optimal pyramid level suggestions for analysis

### Changed
- **API Compatibility**: Updated method calls for QuPath 0.6+ (`getImageClass()` instead of `getImageType()`)
- **Enhanced CSV Output**: Expanded from 4 to 50+ columns of metadata
- **Improved Documentation**: Comprehensive field descriptions and usage examples
- **Better File Handling**: More robust path and URI processing

### Fixed
- **Method Signature Issues**: Resolved `getImageType()` compatibility errors in QuPath 0.6+
- **JSON Dependencies**: Removed unsupported Groovy JSON libraries
- **Memory Management**: Improved handling of large image collections
- **Error Recovery**: Better graceful handling of corrupted or inaccessible images

## [1.1.0] - 2025-06-01

### Added
- **Menu Integration**: Added QuPath menu integration for easier access
- **Configuration Options**: Customizable settings for output directories and formats
- **Batch Processing**: Support for processing entire projects automatically
- **Multiple Export Formats**: CSV and summary text file outputs

### Changed
- **User Interface**: Simplified workflow with menu-driven operations
- **Output Organization**: Better structured output directory and file naming

## [1.0.0] - 2025-05-30

### Added
- **Initial Release**: Basic metadata extraction functionality
- **Core Features**:
  - Image dimension and property extraction
  - Basic pixel calibration data
  - File format and size information
  - Simple scanner metadata detection
- **CSV Export**: Basic CSV output format
- **QuPath Integration**: Compatible with QuPath 0.3.x and later

### Known Issues
- Limited metadata extraction (only basic fields)
- JSON export dependency issues
- API compatibility issues with QuPath 0.6+

## [Unreleased]

### Planned Features
- **Extended Scanner Support**: Better support for additional scanner manufacturers
- **Machine Learning Integration**: Automated image quality assessment using ML models
- **Cloud Storage Support**: Direct integration with cloud storage platforms
- **Real-time Processing**: Live metadata extraction as images are imported
- **Advanced Filtering**: More sophisticated cohort selection tools
- **Integration APIs**: REST API for integration with LIMS and other systems

---

## Migration Guide

### From v1.x to v2.0

If you're upgrading from version 1.x:

1. **Update QuPath**: Ensure you're using QuPath 0.6.0+ for best compatibility
2. **Replace Scripts**: Use the new `QuPath_v06_Compatible.groovy` script
3. **Review Output**: The new CSV format has significantly more columns
4. **Update Analysis Code**: Field names may have changed - review your downstream analysis scripts

### Compatibility Matrix

| Version | QuPath Compatibility | Features |
|---------|---------------------|-----------|
| 2.0.0   | 0.6.0+             | Full feature set, 50+ metadata fields |
| 1.1.0   | 0.3.0 - 0.5.x      | Basic features, menu integration |
| 1.0.0   | 0.3.0 - 0.5.x      | Basic metadata extraction only |

---

For detailed upgrade instructions and breaking changes, see the [Migration Guide](docs/migration.md).