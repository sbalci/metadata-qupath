# QuPath Cohort Metadata Extractor

![QuPath](https://img.shields.io/badge/QuPath-0.6%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Digital Pathology](https://img.shields.io/badge/Digital%20Pathology-Workflow-orange)

A comprehensive metadata extraction toolkit for digital pathology research, designed to standardize cohort definition and image analysis workflows in QuPath.

## 🎯 Overview

The QuPath Cohort Metadata Extractor is a robust workflow designed for pathologists and researchers who need to systematically analyze large collections of whole slide images (WSI). It automatically extracts comprehensive metadata from images in QuPath projects, enabling efficient cohort definition, quality control, and standardized analysis workflows.

### ✨ Key Features

- **Comprehensive Metadata Extraction**: Captures 50+ metadata fields including scanner details, acquisition settings, calibration data, and technical specifications
- **Multi-Scanner Support**: Compatible with major digital pathology scanners (Aperio, Hamamatsu, Ventana, Philips, etc.)
- **Quality Control**: Automated quality assessment and issue detection
- **Batch Processing**: Processes entire QuPath projects automatically
- **Export Flexibility**: Outputs data in CSV format for easy analysis in Excel, R, Python, or other tools
- **Analysis Recommendations**: Provides optimal analysis level suggestions for each image
- **Interoperability**: Designed to integrate with existing digital pathology workflows

## 🏥 Use Cases

### Clinical Research
- **Multi-center studies**: Standardize image analysis across different institutions and scanners
- **Retrospective studies**: Analyze historical slide collections with consistent metadata
- **Quality assurance**: Identify and flag images requiring manual review

### Biomarker Research
- **Cohort selection**: Define homogeneous patient cohorts based on image characteristics
- **Batch effect analysis**: Identify and control for technical variations
- **Protocol optimization**: Determine optimal analysis parameters for different image types

### Educational Applications
- **Teaching collections**: Organize and categorize educational slide sets
- **Research training**: Provide students with comprehensive image metadata for analysis projects

## 📋 Requirements

- **QuPath**: Version 0.6.0 or later (tested with 0.6.0-rc3)
- **Image formats**: SVS, TIFF, NDPI, VSI, SCN, and other QuPath-supported formats
- **System requirements**: Standard QuPath installation requirements

## 🚀 Installation

### Option 1: Direct Script Installation (Recommended)

1. **Download the scripts**:
   ```bash
   git clone https://github.com/sbalci/metadata-qupath.git
   cd qupath-cohort-extractor
   ```

2. **Copy to QuPath scripts directory**:
   - Windows: `%USERPROFILE%\.qupath\scripts\`
   - macOS: `~/.qupath/scripts/`
   - Linux: `~/.qupath/scripts/`

3. **Available script versions**:
   - `QuPathCohortExtractor.groovy` - Full-featured version
   - `SimpleMetadataExtractor.groovy` - Lightweight version for testing
   - `QuPath_v06_Compatible.groovy` - Optimized for QuPath 0.6+

### Option 2: Menu Integration

1. Copy `MenuSetup.groovy` to your QuPath scripts directory
2. Add the following to your QuPath startup scripts:
   ```groovy
   runScript(new File(QPEx.getQuPathUserDirectory(), "scripts/MenuSetup.groovy"))
   ```
3. Restart QuPath to see the new "Cohort Analysis" menu

## 📖 Usage

### Basic Workflow

1. **Prepare your project**:
   - Open QuPath and create/load a project with your WSI files
   - Ensure all images are properly imported and accessible

2. **Run the extraction**:
   ```groovy
   // For menu-integrated version
   // Navigate to: Analyze > Cohort Analysis > Extract Cohort Metadata
   
   // For direct script execution
   // Run the QuPath_v06_Compatible.groovy script
   ```

3. **Review the output**:
   - Find results in the `cohort_metadata/` directory within your project folder
   - Open `cohort_metadata_v06.csv` in Excel or your preferred analysis tool

### Command Examples

#### Single Image Analysis
```groovy
// Analyze currently open image
def projectEntry = QPEx.getProjectEntry()
def extractor = new CohortMetadataExtractor(projectEntry)
def metadata = extractor.extractMetadata()
println("Metadata extracted: ${metadata.size()} fields")
```

#### Batch Processing with Filtering
```groovy
// Load exported metadata
def cohortData = CohortUtils.loadCohortMetadata("cohort_metadata_v06.csv")

// Filter high-quality images
def highQualityImages = CohortUtils.filterImages(cohortData, [
    has_pyramid: true,
    scan_warning: "NONE",
    estimated_magnification: 40
])

println("Found ${highQualityImages.size()} high-quality 40x images")
```

## 📊 Output Data

### Primary Output File: `cohort_metadata_v06.csv`

Contains 50+ columns of metadata including:

#### Basic Image Properties
| Field | Description | Example |
|-------|-------------|---------|
| `image_name` | Filename of the image | `kontrol15.01.25_14_6_134952.svs` |
| `width_pixels` | Image width in pixels | `47622` |
| `height_pixels` | Image height in pixels | `63413` |
| `pixel_width_um` | Pixel size in micrometers | `0.263312` |
| `estimated_magnification` | Calculated magnification | `40` |

#### Scanner Information
| Field | Description | Example |
|-------|-------------|---------|
| `scanner_type` | Scanner model | `GT450` |
| `scanscope_id` | Scanner identifier | `1111111` |
| `scan_date` | Date of image acquisition | `01/07/2025` |
| `scan_time` | Time of image acquisition | `08:29:16` |
| `apparent_magnification` | Scanner-reported magnification | `40X` |

#### Quality Metrics
| Field | Description | Example |
|-------|-------------|---------|
| `has_pyramid` | Whether image has pyramid structure | `true` |
| `scan_warning` | Any scanner warnings | `NONE` |
| `compression_quality` | JPEG compression quality | `91` |
| `file_size_mb` | File size in megabytes | `563.87` |

#### Analysis Recommendations
| Field | Description | Example |
|-------|-------------|---------|
| `suggested_analysis_level` | Optimal pyramid level for analysis | `1` |
| `needs_pyramid` | Whether image needs pyramid for performance | `false` |

### Additional Output Files

- **`detailed_summary_v06.txt`**: Human-readable summary with statistics
- **`processing_log.txt`**: Detailed processing log with any errors

## 🔬 Analysis Examples

### Python Integration

```python
import pandas as pd
import matplotlib.pyplot as plt

# Load cohort data
df = pd.read_csv('cohort_metadata_v06.csv')

# Basic statistics
print(f"Total images: {len(df)}")
print(f"Scanners: {df['scanner_type'].unique()}")
print(f"Date range: {df['scan_date'].min()} to {df['scan_date'].max()}")

# Quality assessment
quality_issues = df[
    (df['scan_warning'] != 'NONE') | 
    (df['compression_quality'] < 85) |
    (~df['has_pyramid'])
]
print(f"Images with quality concerns: {len(quality_issues)}")

# Magnification distribution
df['estimated_magnification'].hist(bins=20)
plt.title('Magnification Distribution')
plt.xlabel('Magnification')
plt.ylabel('Number of Images')
plt.show()
```

### R Integration

```r
library(dplyr)
library(ggplot2)

# Load data
cohort_data <- read.csv("cohort_metadata_v06.csv")

# Scanner analysis
scanner_summary <- cohort_data %>%
  group_by(scanner_type, scan_date) %>%
  summarise(
    image_count = n(),
    avg_file_size = mean(file_size_mb, na.rm = TRUE),
    .groups = 'drop'
  )

# Visualization
ggplot(cohort_data, aes(x = pixel_width_um, y = estimated_magnification)) +
  geom_point(aes(color = scanner_type)) +
  labs(title = "Pixel Size vs Magnification by Scanner",
       x = "Pixel Width (μm)", y = "Estimated Magnification")
```

### Excel Analysis

1. **Open the CSV file** in Excel
2. **Create pivot tables** for:
   - Scanner type distribution
   - Acquisition date analysis
   - Quality metrics summary
3. **Apply filters** to define your cohort:
   - Magnification range
   - Scanner type
   - Date range
   - Quality criteria

## 🛠️ Advanced Configuration

### Custom Metadata Fields

Add custom extraction logic by extending the `CohortMetadataExtractor` class:

```groovy
class CustomExtractor extends CohortMetadataExtractor {
    def extractStainInfo() {
        // Custom stain detection logic
        if (metadata.description?.toLowerCase()?.contains('he')) {
            metadata.stain_type = 'H&E'
        }
    }
}
```

### Integration with Analysis Workflows

```groovy
// Use metadata to set analysis parameters
def cohortData = CohortUtils.loadCohortMetadata("cohort_metadata_v06.csv")
def currentImage = cohortData.find { it.image_name == getCurrentImageData().getServer().getMetadata().get('Name') }

if (currentImage) {
    def analysisLevel = currentImage.suggested_analysis_level
    def pixelSize = currentImage.pixel_width_um
    
    // Configure your analysis based on metadata
    println("Using analysis level: ${analysisLevel}")
    println("Target pixel size: ${pixelSize * Math.pow(2, analysisLevel)} μm")
}
```

## ⚠️ Troubleshooting

### Common Issues

**Issue**: "No signature of method getImageType()"
- **Solution**: Use `QuPath_v06_Compatible.groovy` for QuPath 0.6+

**Issue**: CSV file has only 4 columns
- **Solution**: API compatibility issue - use the v0.6+ compatible version

**Issue**: "Could not load server" errors
- **Solution**: Check that image files are accessible and not corrupted

**Issue**: Missing scanner metadata
- **Solution**: Some formats may have limited metadata; this is normal

### Performance Optimization

- **Large projects**: Process in batches or use filters
- **Network storage**: Copy files locally before processing
- **Memory issues**: Increase QuPath memory allocation in preferences

## 🤝 Contributing

We welcome contributions from the digital pathology community!

### How to Contribute

1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature/amazing-feature`
3. **Make your changes** and test thoroughly
4. **Commit your changes**: `git commit -m 'Add amazing feature'`
5. **Push to the branch**: `git push origin feature/amazing-feature`
6. **Open a Pull Request**

### Areas for Contribution

- Support for additional scanner types
- Integration with other digital pathology platforms
- Enhanced quality control metrics
- Documentation improvements
- Bug fixes and performance optimizations

## 📚 Citation

If you use this workflow in your research, please cite:

```bibtex
@software{qupath_cohort_extractor,
  title={QuPath Cohort Metadata Extractor},
  author={[Your Name/Institution]},
  year={2025},
  url={https://github.com/sbalci/qupath-cohort-extractor}
}
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **QuPath Development Team** for creating an excellent open-source platform
- **Digital pathology community** for feedback and testing
- **Bio-Formats library** for supporting multiple image formats
- **OpenSlide library** for WSI format support

## 📞 Support

- **Issues**: Report bugs and request features via [GitHub Issues](https://github.com/sbalci/qupath-cohort-extractor/issues)
- **Discussions**: Join the conversation in [GitHub Discussions](https://github.com/sbalci/qupath-cohort-extractor/discussions)
- **QuPath Forum**: For general QuPath questions, use the [QuPath Forum](https://forum.image.sc/tag/qupath)

## 🔄 Version History

- **v2.0.0**: QuPath 0.6+ compatibility, enhanced metadata extraction
- **v1.1.0**: Added menu integration and configuration options
- **v1.0.0**: Initial release with basic metadata extraction

---

**Made with ❤️ for the digital pathology community**

*Star ⭐ this repository if you find it useful!*