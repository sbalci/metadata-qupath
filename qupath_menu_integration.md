# QuPath Cohort Definition Workflow - Installation and Integration Guide

## Overview

This workflow provides a comprehensive solution for extracting metadata from whole slide images (WSI) to define cohorts for digital pathology research. It's designed to be portable, interoperable, and easily integrated into existing QuPath workflows.

## Features

- **Comprehensive Metadata Extraction**: Extracts image properties, scanner information, magnification, pixel data, scan dates, and file system information
- **Multiple Export Formats**: CSV for spreadsheet analysis, JSON for programmatic access
- **Batch Processing**: Processes entire projects automatically
- **Quality Metrics**: Provides image quality indicators and analysis suggestions
- **Interoperability**: Designed to work with other QuPath scripts and external tools
- **Error Handling**: Robust error handling with detailed logging

## Installation

### Method 1: Manual Installation

1. **Create the script file**:
   - Create a new file named `CohortMetadataExtractor.groovy`
   - Copy the main workflow script into this file
   - Save it in your QuPath scripts directory

2. **Add to QuPath menu**:
   - Create a new file named `MenuSetup.groovy` in your QuPath scripts directory
   - Add the menu integration code (see below)

3. **Configure startup**:
   - Add the menu setup to your QuPath startup scripts

### Method 2: Extension Installation

1. **Create extension structure**:
   ```
   QuPathExtensions/
   └── CohortWorkflow/
       ├── CohortMetadataExtractor.groovy
       ├── MenuSetup.groovy
       ├── config.json
       └── README.md
   ```

2. **Load as extension**:
   - Use QuPath's extension loading mechanism
   - Follow QuPath extension guidelines for your version

## Menu Integration Script

Create a file named `MenuSetup.groovy`:

```groovy
/**
 * Menu Integration for Cohort Metadata Extraction Workflow
 * Add this to your QuPath startup scripts or extension
 */

import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.extensions.GitHubProject
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.scripting.QP

// Create the main menu
def createCohortMenu() {
    def gui = QuPathGUI.getInstance()
    if (!gui) return
    
    def menuBar = gui.getMenuBar()
    
    // Create or find the Analysis menu
    def analysisMenu = menuBar.getMenus().find { it.getText() == "Analyze" }
    if (!analysisMenu) {
        analysisMenu = new Menu("Analyze")
        menuBar.getMenus().add(analysisMenu)
    }
    
    // Create Cohort submenu
    def cohortMenu = new Menu("Cohort Analysis")
    
    // Main metadata extraction item
    def extractMetadataItem = new MenuItem("Extract Cohort Metadata")
    extractMetadataItem.setOnAction { e ->
        runCohortExtraction()
    }
    
    // Quick single image analysis
    def singleImageItem = new MenuItem("Analyze Current Image")
    singleImageItem.setOnAction { e ->
        runSingleImageAnalysis()
    }
    
    // Load existing cohort data
    def loadCohortItem = new MenuItem("Load Cohort Data")
    loadCohortItem.setOnAction { e ->
        loadExistingCohort()
    }
    
    // Configuration
    def configItem = new MenuItem("Configure Extraction")
    configItem.setOnAction { e ->
        showConfiguration()
    }
    
    // Add items to submenu
    cohortMenu.getItems().addAll([
        extractMetadataItem,
        singleImageItem,
        new SeparatorMenuItem(),
        loadCohortItem,
        new SeparatorMenuItem(),
        configItem
    ])
    
    // Add to Analysis menu
    analysisMenu.getItems().add(new SeparatorMenuItem())
    analysisMenu.getItems().add(cohortMenu)
}

def runCohortExtraction() {
    try {
        // Check if project is open
        def project = QP.getProject()
        if (!project) {
            Dialogs.showErrorMessage("Error", "Please open a project before running cohort extraction.")
            return
        }
        
        // Confirm action
        def response = Dialogs.showYesNoDialog("Cohort Extraction", 
            "This will extract metadata from all images in the current project. Continue?")
        
        if (response) {
            // Run the main extraction script
            def scriptPath = "CohortMetadataExtractor.groovy"
            QP.runScript(new File(getScriptDirectory(), scriptPath))
        }
    } catch (Exception ex) {
        Dialogs.showErrorMessage("Error", "Failed to run cohort extraction: ${ex.getMessage()}")
    }
}

def runSingleImageAnalysis() {
    try {
        def imageData = QP.getCurrentImageData()
        if (!imageData) {
            Dialogs.showErrorMessage("Error", "No image is currently open.")
            return
        }
        
        // Run extraction for current image only
        def script = '''
        import qupath.lib.gui.scripting.QPEx
        
        // Create extractor for current image
        def projectEntry = QPEx.getProjectEntry()
        if (projectEntry) {
            def extractor = new CohortMetadataExtractor(projectEntry)
            def metadata = extractor.extractMetadata()
            
            // Display results
            println("Metadata for: ${metadata.image_name}")
            metadata.each { key, value ->
                if (key != "original_metadata") {
                    println("${key}: ${value}")
                }
            }
        }
        '''
        
        QP.runScript(script)
        
    } catch (Exception ex) {
        Dialogs.showErrorMessage("Error", "Failed to analyze current image: ${ex.getMessage()}")
    }
}

def loadExistingCohort() {
    try {
        def file = Dialogs.promptForFile("Select Cohort Metadata File", 
            null, "Cohort files", "*.json", "*.csv")
        
        if (file) {
            if (file.getName().endsWith(".json")) {
                def script = """
                import groovy.json.JsonSlurper
                
                def jsonSlurper = new JsonSlurper()
                def cohortData = jsonSlurper.parse(new File("${file.getAbsolutePath()}"))
                
                println("Loaded cohort data:")
                println("Project: \${cohortData.export_info.project_name}")
                println("Images: \${cohortData.export_info.total_images}")
                println("Export date: \${cohortData.export_info.date}")
                
                // Store in global variable for access
                binding.setVariable("currentCohortData", cohortData)
                println("Cohort data loaded and available as 'currentCohortData'")
                """
                
                QP.runScript(script)
            } else {
                Dialogs.showInfoNotification("CSV File", "CSV file selected. Use your preferred analysis tool to examine the data.")
            }
        }
    } catch (Exception ex) {
        Dialogs.showErrorMessage("Error", "Failed to load cohort data: ${ex.getMessage()}")
    }
}

def showConfiguration() {
    def configDialog = """
Configuration options for Cohort Metadata Extraction:

Current settings:
- Output directory: cohort_metadata/
- Include thumbnails: true
- Thumbnail size: 512px
- Export formats: CSV, JSON

To modify these settings, edit the CohortConfig class
in the CohortMetadataExtractor.groovy script.

Advanced users can create a config.json file with
custom settings that will be automatically loaded.
    """
    
    Dialogs.showInfoNotification("Configuration", configDialog)
}

def getScriptDirectory() {
    // Get the QuPath user directory for scripts
    def userPath = System.getProperty("user.home")
    return new File(userPath, ".qupath/scripts")
}

// Initialize the menu when this script runs
createCohortMenu()
println("Cohort Analysis menu has been added to the Analyze menu.")
```

## Configuration File

Create `config.json` for customizable settings:

```json
{
  "output_settings": {
    "base_directory": "cohort_metadata",
    "csv_filename": "cohort_metadata.csv",
    "json_filename": "cohort_metadata.json",
    "include_processing_log": true
  },
  "extraction_settings": {
    "include_thumbnails": true,
    "thumbnail_size": 512,
    "include_quality_metrics": true,
    "suggested_analysis_pixel_size": 1.0
  },
  "metadata_fields": {
    "required_fields": [
      "image_name", "width_pixels", "height_pixels", 
      "pixel_width_um", "scanner", "scan_date"
    ],
    "optional_fields": [
      "magnification", "compression", "focus_method",
      "file_size_mb", "pyramid_levels"
    ]
  },
  "quality_thresholds": {
    "min_image_area_mm2": 1.0,
    "max_file_size_gb": 5.0,
    "required_pyramid_levels": 3
  },
  "scanner_mappings": {
    "aperio": ["Aperio", "Leica"],
    "hamamatsu": ["Hamamatsu", "NanoZoomer"],
    "ventana": ["Ventana", "iScan"],
    "philips": ["Philips", "IntelliSite"]
  }
}
```

## Interoperability Features

### 1. Python Integration

```python
# Example Python script to work with exported data
import pandas as pd
import json

# Load cohort metadata
def load_cohort_data(json_path):
    with open(json_path, 'r') as f:
        data = json.load(f)
    return pd.DataFrame(data['cohort_metadata'])

# Filter images for analysis
def filter_cohort(df, criteria):
    filtered = df.copy()
    for column, value in criteria.items():
        if isinstance(value, tuple):  # Range filtering
            filtered = filtered[
                (filtered[column] >= value[0]) & 
                (filtered[column] <= value[1])
            ]
        else:
            filtered = filtered[filtered[column] == value]
    return filtered

# Example usage
cohort_df = load_cohort_data('cohort_metadata.json')
high_mag_images = filter_cohort(cohort_df, {
    'estimated_magnification': (20, 40),
    'area_mm2': (10, None)  # Minimum 10 mm²
})
```

### 2. R Integration

```r
# R script for cohort analysis
library(jsonlite)
library(dplyr)

# Load cohort data
load_cohort_data <- function(json_path) {
  data <- fromJSON(json_path)
  return(as.data.frame(data$cohort_metadata))
}

# Statistical analysis
analyze_cohort <- function(cohort_df) {
  summary_stats <- cohort_df %>%
    summarise(
      total_images = n(),
      mean_area_mm2 = mean(area_mm2, na.rm = TRUE),
      mean_magnification = mean(estimated_magnification, na.rm = TRUE),
      scanners = n_distinct(scanner, na.rm = TRUE)
    )
  return(summary_stats)
}
```

### 3. Database Integration

```sql
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
```

## Usage Workflow

1. **Initial Setup**:
   - Install the scripts using one of the methods above
   - Open your QuPath project with WSI files

2. **Extract Metadata**:
   - Navigate to `Analyze > Cohort Analysis > Extract Cohort Metadata`
   - Wait for processing to complete
   - Review the generated files in the output directory

3. **Define Your Cohort**:
   - Open the CSV file in Excel or similar
   - Filter based on your research criteria
   - Create subsets for different analyses

4. **Integration with Analysis Pipelines**:
   - Use the JSON file for programmatic access
   - Import metadata into your preferred analysis environment
   - Use the `suggested_analysis_level` for optimal processing

5. **Quality Control**:
   - Review the processing log for any issues
   - Use quality metrics to identify problematic images
   - Validate metadata accuracy for critical fields

## Advanced Features

### Custom Metadata Extractors

Extend the workflow by adding custom extractors:

```groovy
class CustomMetadataExtractor extends CohortMetadataExtractor {
    
    def extractCustomStainInfo() {
        // Custom logic for stain detection
        def stainInfo = analyzeStainPattern()
        metadata.stain_type = stainInfo.type
        metadata.stain_quality = stainInfo.quality
    }
    
    def extractTissueMetrics() {
        // Custom tissue analysis
        def tissueAnalysis = performTissueSegmentation()
        metadata.tissue_percentage = tissueAnalysis.tissueRatio
        metadata.background_percentage = tissueAnalysis.backgroundRatio
    }
}
```

### Integration with External Tools

The workflow is designed to integrate with:
- **OMERO**: Export metadata for OMERO database integration
- **CellProfiler**: Generate compatible metadata for image analysis pipelines
- **ImageJ/Fiji**: Provide image parameters for macro development
- **Commercial platforms**: Compatible with major digital pathology platforms

## Troubleshooting

### Common Issues

1. **Memory Issues with Large Files**:
   - Increase QuPath's memory allocation
   - Process images in smaller batches
   - Use the single image analysis for testing

2. **Missing Metadata Fields**:
   - Check scanner-specific metadata mappings
   - Update the scanner field mappings in configuration
   - Review original image metadata manually

3. **Export Failures**:
   - Verify write permissions for output directory
   - Check disk space availability
   - Review processing log for specific errors

### Performance Optimization

- **Large Projects**: Consider processing subsets of images
- **Network Storage**: Copy files locally before processing
- **Memory Management**: Close unnecessary projects and images

## Version History

- **v1.0**: Initial release with comprehensive metadata extraction
- **v1.1**: Added menu integration and configuration options
- **v1.2**: Enhanced interoperability features and documentation

## Support and Contributions

For issues, suggestions, or contributions:
1. Check the processing log for detailed error information
2. Verify QuPath version compatibility
3. Test with a small subset of images first
4. Consult QuPath documentation for platform-specific issues

This workflow is designed to be extensible and can be customized for specific research needs or institutional requirements.