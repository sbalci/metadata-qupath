# Practical Usage Examples for QuPath Cohort Metadata Extraction

## Real-World Scenarios

### Scenario 1: Multi-Center Research Study

**Goal**: Standardize image analysis across different institutions with varying scanners and protocols.

**Workflow**:
1. **Extract metadata from all sites**:
   ```groovy
   // Run cohort extraction at each site
   def processor = new CohortBatchProcessor()
   processor.processAllImages()
   ```

2. **Combine metadata from multiple sites**:
   ```python
   import pandas as pd
   import glob
   
   # Combine metadata from multiple sites
   site_files = glob.glob("*/cohort_metadata.csv")
   combined_data = []
   
   for file in site_files:
       site_name = file.split('/')[0]
       df = pd.read_csv(file)
       df['site'] = site_name
       combined_data.append(df)
   
   master_cohort = pd.concat(combined_data, ignore_index=True)
   
   # Analyze scanner distribution
   scanner_summary = master_cohort.groupby(['site', 'scanner']).size()
   print(scanner_summary)
   ```

3. **Standardization recommendations**:
   ```python
   # Find optimal analysis parameters
   optimal_level = master_cohort.groupby('scanner')['suggested_analysis_level'].median()
   target_pixel_size = master_cohort['pixel_width_um'].quantile(0.75)
   
   print(f"Recommended target pixel size: {target_pixel_size:.3f} μm")
   ```

### Scenario 2: Tissue Microarray (TMA) Analysis

**Goal**: Identify and process TMA slides for high-throughput analysis.

**Custom TMA Detection**:
```groovy
class TMAMetadataExtractor extends CohortMetadataExtractor {
    
    def extractTMAInfo() {
        // Detect TMA based on image characteristics
        def isTMA = detectTMAPattern()
        metadata.is_tma = isTMA
        
        if (isTMA) {
            metadata.estimated_core_count = estimateCoreCount()
            metadata.core_diameter_um = estimateCoreDiameter()
            metadata.tma_grid_pattern = detectGridPattern()
        }
    }
    
    private boolean detectTMAPattern() {
        // TMA detection logic based on:
        // - Small total area
        // - Specific aspect ratios
        // - Multiple discrete tissue regions
        
        def area = metadata.area_mm2
        def aspectRatio = (double)metadata.width_pixels / metadata.height_pixels
        
        return area < 500 && aspectRatio > 0.8 && aspectRatio < 1.2
    }
    
    private int estimateCoreCount() {
        // Estimate based on standard TMA dimensions
        def area = metadata.area_mm2
        def coreArea = Math.PI * Math.pow(0.6, 2) // Assuming 1.2mm cores
        return (int)(area / coreArea * 0.7) // 70% packing efficiency
    }
}
```

### Scenario 3: Quality Control Pipeline

**Goal**: Automatically identify images requiring manual review before analysis.

**Quality Control Script**:
```groovy
class QualityControlAnalyzer {
    
    static def runQualityControl(def cohortData) {
        def issues = []
        
        cohortData.cohort_metadata.each { image ->
            // Check for common quality issues
            def imageIssues = checkImageQuality(image)
            if (imageIssues) {
                issues.add([
                    image_name: image.image_name,
                    issues: imageIssues
                ])
            }
        }
        
        return issues
    }
    
    static def checkImageQuality(def metadata) {
        def issues = []
        
        // Check file size (too small might indicate corruption)
        if (metadata.file_size_mb < 10) {
            issues.add("File size unusually small (${metadata.file_size_mb} MB)")
        }
        
        // Check image dimensions
        if (metadata.width_pixels < 1000 || metadata.height_pixels < 1000) {
            issues.add("Image dimensions too small")
        }
        
        // Check pixel size (unrealistic values)
        if (metadata.pixel_width_um < 0.1 || metadata.pixel_width_um > 2.0) {
            issues.add("Unusual pixel size: ${metadata.pixel_width_um} μm")
        }
        
        // Check for missing pyramid
        if (!metadata.has_pyramid) {
            issues.add("No image pyramid - may impact performance")
        }
        
        // Check for very old scan dates (potential archive issues)
        if (metadata.scan_date && metadata.scan_date < "2010-01-01") {
            issues.add("Very old scan date: ${metadata.scan_date}")
        }
        
        return issues.isEmpty() ? null : issues
    }
}

// Run quality control
def qcResults = QualityControlAnalyzer.runQualityControl(currentCohortData)
qcResults.each { result ->
    println("ISSUES in ${result.image_name}:")
    result.issues.each { issue ->
        println("  - ${issue}")
    }
}
```

### Scenario 4: Scanner-Specific Analysis Optimization

**Goal**: Optimize analysis parameters for different scanner types.

**Scanner Optimization**:
```python
import pandas as pd
import matplotlib.pyplot as plt

def optimize_for_scanner(cohort_df):
    scanner_configs = {}
    
    for scanner in cohort_df['scanner'].unique():
        if pd.isna(scanner):
            continue
            
        scanner_data = cohort_df[cohort_df['scanner'] == scanner]
        
        config = {
            'optimal_analysis_level': int(scanner_data['suggested_analysis_level'].median()),
            'typical_pixel_size': scanner_data['pixel_width_um'].median(),
            'typical_magnification': scanner_data['estimated_magnification'].median(),
            'compression_used': scanner_data['compression'].mode().iloc[0] if not scanner_data['compression'].isna().all() else 'Unknown',
            'pyramid_factor': scanner_data['pyramid_factor'].median()
        }
        
        scanner_configs[scanner] = config
    
    return scanner_configs

# Generate scanner-specific configurations
scanner_configs = optimize_for_scanner(cohort_df)

# Export QuPath-compatible settings
for scanner, config in scanner_configs.items():
    print(f"// Configuration for {scanner}")
    print(f"def {scanner.replace(' ', '_').lower()}_config = [")
    print(f"    analysis_level: {config['optimal_analysis_level']},")
    print(f"    target_pixel_size: {config['typical_pixel_size']:.3f},")
    print(f"    expected_magnification: {config['typical_magnification']}")
    print("]\n")
```

## Integration with Analysis Workflows

### Automated Region Selection

```groovy
/**
 * Use cohort metadata to automatically select appropriate regions
 * for downstream analysis based on image characteristics
 */
class AutoRegionSelector {
    
    static def selectAnalysisRegions(def imageMetadata) {
        def regions = []
        
        // Determine analysis strategy based on image size
        def area = imageMetadata.area_mm2
        def analysisLevel = imageMetadata.suggested_analysis_level
        
        if (area > 1000) {
            // Large image - use tiling approach
            regions = createTileRegions(imageMetadata)
        } else if (area > 100) {
            // Medium image - use region sampling
            regions = createSampledRegions(imageMetadata)
        } else {
            // Small image - analyze entire image
            regions = [createFullImageRegion(imageMetadata)]
        }
        
        return regions
    }
    
    static def createTileRegions(def metadata) {
        def tileSize = 2000 // pixels at analysis level
        def overlap = 200   // pixel overlap
        
        def downsample = Math.pow(2, metadata.suggested_analysis_level)
        def effectiveTileSize = tileSize * downsample
        
        def regions = []
        def cols = (int)Math.ceil(metadata.width_pixels / effectiveTileSize)
        def rows = (int)Math.ceil(metadata.height_pixels / effectiveTileSize)
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                def x = col * effectiveTileSize
                def y = row * effectiveTileSize
                def width = Math.min(effectiveTileSize, metadata.width_pixels - x)
                def height = Math.min(effectiveTileSize, metadata.height_pixels - y)
                
                regions.add([
                    x: x, y: y, width: width, height: height,
                    type: "tile", row: row, col: col
                ])
            }
        }
        
        return regions
    }
}
```

### Batch Analysis Script

```groovy
/**
 * Complete workflow: Extract metadata → Quality control → Analysis
 */
class BatchAnalysisWorkflow {
    
    def project
    def cohortData
    def analysisResults = []
    
    def runCompleteWorkflow() {
        // Step 1: Extract metadata
        println("Step 1: Extracting cohort metadata...")
        def processor = new CohortBatchProcessor()
        processor.processAllImages()
        
        // Step 2: Load and validate data
        println("Step 2: Loading and validating data...")
        cohortData = loadCohortData()
        def qcIssues = runQualityControl()
        
        // Step 3: Filter valid images
        def validImages = cohortData.cohort_metadata.findAll { image ->
            !qcIssues.any { it.image_name == image.image_name }
        }
        
        println("Valid images for analysis: ${validImages.size()}")
        
        // Step 4: Run analysis on valid images
        println("Step 3: Running analysis on valid images...")
        validImages.each { imageMetadata ->
            try {
                def result = analyzeImage(imageMetadata)
                analysisResults.add(result)
                println("Completed: ${imageMetadata.image_name}")
            } catch (Exception e) {
                println("Failed: ${imageMetadata.image_name} - ${e.getMessage()}")
            }
        }
        
        // Step 5: Export results
        exportAnalysisResults()
    }
    
    def analyzeImage(def imageMetadata) {
        // Load image
        def entry = project.getImageList().find { 
            it.getImageName() == imageMetadata.image_name 
        }
        
        if (!entry) {
            throw new Exception("Image not found in project")
        }
        
        def imageData = entry.readImageData()
        
        // Set optimal analysis level
        def server = imageData.getServer()
        def analysisLevel = imageMetadata.suggested_analysis_level
        
        // Select regions based on metadata
        def regions = AutoRegionSelector.selectAnalysisRegions(imageMetadata)
        
        // Run analysis for each region
        def regionResults = []
        regions.each { region ->
            // Create ROI for region
            def roi = ROIs.createRectangleROI(
                region.x, region.y, region.width, region.height, 
                ImagePlane.getDefaultPlane()
            )
            
            // Run your specific analysis here
            def result = performAnalysis(imageData, roi, analysisLevel)
            regionResults.add(result)
        }
        
        return [
            image_name: imageMetadata.image_name,
            metadata: imageMetadata,
            regions: regionResults,
            analysis_date: new Date()
        ]
    }
    
    def performAnalysis(def imageData, def roi, int level) {
        // Placeholder for your specific analysis
        // This could be:
        // - Cell detection and classification
        // - Tissue segmentation
        // - Feature extraction
        // - Machine learning inference
        
        return [
            roi_area: roi.getArea(),
            analysis_level: level,
            // Add your analysis results here
        ]
    }
}
```

## Export Templates for Common Platforms

### OMERO Integration

```python
# Template for OMERO metadata import
def create_omero_metadata(cohort_df):
    omero_template = []
    
    for _, row in cohort_df.iterrows():
        metadata = {
            "filename": row['image_name'],
            "scanner": row['scanner'],
            "magnification": row['estimated_magnification'],
            "pixel_size_x": row['pixel_width_um'],
            "pixel_size_y": row['pixel_height_um'],
            "acquisition_date": row['scan_date'],
            "image_width": row['width_pixels'],
            "image_height": row['height_pixels'],
            "file_size": row['file_size_bytes']
        }
        omero_template.append(metadata)
    
    return omero_template
```

### CellProfiler Pipeline Integration

```python
# Generate CellProfiler-compatible metadata
def create_cellprofiler_metadata(cohort_df):
    cp_metadata = cohort_df[['image_name', 'file_path', 'suggested_analysis_level']].copy()
    cp_metadata['Metadata_ImageName'] = cp_metadata['image_name']
    cp_metadata['Metadata_AnalysisLevel'] = cp_metadata['suggested_analysis_level']
    cp_metadata['PathName_OriginalImage'] = cp_metadata['file_path'].str.rsplit('/', n=1).str[0]
    cp_metadata['FileName_OriginalImage'] = cp_metadata['file_path'].str.rsplit('/', n=1).str[1]
    
    return cp_metadata[['Metadata_ImageName', 'Metadata_AnalysisLevel', 
                       'PathName_OriginalImage', 'FileName_OriginalImage']]
```

## Performance Monitoring

```groovy
/**
 * Monitor performance of cohort processing
 */
class PerformanceMonitor {
    
    def startTime
    def checkpoints = []
    
    def start() {
        startTime = System.currentTimeMillis()
        checkpoint("Start")
    }
    
    def checkpoint(String label) {
        def currentTime = System.currentTimeMillis()
        def elapsed = startTime ? currentTime - startTime : 0
        checkpoints.add([
            label: label,
            timestamp: currentTime,
            elapsed_ms: elapsed
        ])
        println("${label}: ${elapsed}ms")
    }
    
    def finish() {
        checkpoint("Finish")
        
        println("\nPerformance Summary:")
        checkpoints.each { cp ->
            println("${cp.label}: ${cp.elapsed_ms}ms")
        }
        
        def totalTime = checkpoints.last().elapsed_ms
        def avgPerImage = project ? totalTime / project.getImageList().size() : 0
        println("Average per image: ${avgPerImage}ms")
    }
}

// Usage in your workflow
def monitor = new PerformanceMonitor()
monitor.start()

// Your processing code here
monitor.checkpoint("Metadata extraction complete")

// More processing...
monitor.checkpoint("Analysis complete")

monitor.finish()
```

This comprehensive workflow provides pathologists with a robust, scalable solution for cohort definition that integrates seamlessly with existing digital pathology pipelines while maintaining portability across different platforms and analysis tools.