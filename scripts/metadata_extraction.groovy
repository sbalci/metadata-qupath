/**
 * QuPath Cohort Definition and Metadata Extraction Workflow
 * 
 * This script extracts comprehensive metadata from whole slide images (WSI)
 * including SVS, TIFF, and other supported formats for cohort definition
 * and downstream analysis preparation.
 * 
 * Author: Digital Pathology Workflow System
 * Version: 1.0
 * Compatible with: QuPath 0.4.x and later
 */

import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.gui.scripting.QPEx
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServer
import qupath.lib.common.GeneralTools
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.nio.file.Paths
import java.util.regex.Pattern

// Configuration
class CohortConfig {
    static final String OUTPUT_DIR = "cohort_metadata"
    static final String CSV_FILENAME = "cohort_metadata.csv"
    static final String JSON_FILENAME = "cohort_metadata.json"
    static final boolean INCLUDE_THUMBNAILS = true
    static final int THUMBNAIL_SIZE = 512
}

// Main workflow class
class CohortMetadataExtractor {
    
    def projectEntry
    def imageData
    def server
    def metadata = [:]
    def errors = []
    
    CohortMetadataExtractor(projectEntry) {
        this.projectEntry = projectEntry
        this.imageData = projectEntry.readImageData()
        this.server = imageData?.getServer()
    }
    
    /**
     * Extract comprehensive metadata from the image
     */
    def extractMetadata() {
        try {
            // Basic file information
            extractBasicInfo()
            
            // Image properties
            extractImageProperties()
            
            // Scanner and acquisition info
            extractScannerInfo()
            
            // Pixel and magnification data
            extractPixelData()
            
            // Additional metadata from image properties
            extractAdditionalMetadata()
            
            // File system information
            extractFileSystemInfo()
            
            // Quality metrics
            extractQualityMetrics()
            
        } catch (Exception e) {
            errors.add("Error extracting metadata: ${e.getMessage()}")
            println("Error processing ${projectEntry.getImageName()}: ${e.getMessage()}")
        }
        
        return metadata
    }
    
    private void extractBasicInfo() {
        metadata.image_name = projectEntry.getImageName()
        metadata.project_name = QPEx.getProject()?.getName() ?: "Unknown"
        metadata.extraction_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
        metadata.qupath_version = GeneralTools.getVersion()
    }
    
    private void extractImageProperties() {
        if (!server) return
        
        metadata.image_type = server.getImageType()?.toString() ?: "Unknown"
        metadata.width_pixels = server.getWidth()
        metadata.height_pixels = server.getHeight()
        metadata.num_channels = server.nChannels()
        metadata.num_z_slices = server.nZSlices()
        metadata.num_timepoints = server.nTimepoints()
        metadata.bit_depth = server.getPixelType()?.toString() ?: "Unknown"
        metadata.rgb = server.isRGB()
        metadata.pyramid_levels = server.nResolutions()
        
        // Calculate image area
        metadata.image_area_pixels = (long)metadata.width_pixels * metadata.height_pixels
    }
    
    private void extractPixelData() {
        if (!server) return
        
        def pixelCalibration = server.getPixelCalibration()
        if (pixelCalibration) {
            metadata.pixel_width_um = pixelCalibration.getPixelWidthMicrons()
            metadata.pixel_height_um = pixelCalibration.getPixelHeightMicrons()
            metadata.pixel_units = pixelCalibration.getPixelWidthUnit()?.toString() ?: "pixel"
            metadata.z_spacing_um = pixelCalibration.getZSpacingMicrons()
            
            // Calculate magnification if pixel size is available
            if (metadata.pixel_width_um > 0) {
                // Typical calculation: 0.25 μm/pixel ≈ 40x magnification
                metadata.estimated_magnification = Math.round(0.25 / metadata.pixel_width_um * 40)
            }
            
            // Calculate physical image dimensions
            if (metadata.pixel_width_um > 0 && metadata.pixel_height_um > 0) {
                metadata.width_um = metadata.width_pixels * metadata.pixel_width_um
                metadata.height_um = metadata.height_pixels * metadata.pixel_height_um
                metadata.area_mm2 = (metadata.width_um * metadata.height_um) / 1_000_000
            }
        }
    }
    
    private void extractScannerInfo() {
        def serverMetadata = server?.getMetadata()
        if (!serverMetadata) return
        
        // Extract scanner information from metadata map
        def metadataMap = serverMetadata.entrySet().collectEntries { 
            [it.key.toString(), it.value?.toString()] 
        }
        
        // Common scanner metadata fields
        def scannerFields = [
            'Scanner': ['scanner', 'Scanner', 'Instrument', 'Device'],
            'ScanDate': ['scan_date', 'ScanDate', 'Date', 'AcquisitionDate', 'DateTime'],
            'ScannerVersion': ['scanner_version', 'ScannerVersion', 'SoftwareVersion', 'Version'],
            'Magnification': ['magnification', 'Magnification', 'ObjectivePower', 'NominalMagnification'],
            'FocusMethod': ['focus_method', 'FocusMethod', 'AutoFocus'],
            'Compression': ['compression', 'Compression', 'CompressionType']
        ]
        
        scannerFields.each { outputKey, possibleKeys ->
            def value = findMetadataValue(metadataMap, possibleKeys)
            if (value) {
                metadata[outputKey.toLowerCase()] = value
            }
        }
        
        // Store all original metadata for reference
        metadata.original_metadata = metadataMap
    }
    
    private void extractAdditionalMetadata() {
        if (!server) return
        
        // File format specific information
        def serverClass = server.getClass().getSimpleName()
        metadata.server_type = serverClass
        
        // URI information
        def uri = server.getURIs()
        if (uri && !uri.isEmpty()) {
            metadata.file_uri = uri[0].toString()
            metadata.file_path = extractFilePath(uri[0].toString())
            metadata.file_extension = getFileExtension(metadata.file_path)
        }
        
        // Associated images (macro, label, etc.)
        def associatedImages = server.getAssociatedImageList()
        if (associatedImages) {
            metadata.associated_images = associatedImages.join(', ')
            metadata.has_macro_image = associatedImages.contains('macro')
            metadata.has_label_image = associatedImages.contains('label')
        }
    }
    
    private void extractFileSystemInfo() {
        def filePath = metadata.file_path
        if (!filePath) return
        
        try {
            def file = new File(filePath)
            if (file.exists()) {
                metadata.file_size_bytes = file.length()
                metadata.file_size_mb = Math.round(file.length() / (1024 * 1024) * 100) / 100
                metadata.last_modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(file.lastModified()))
            }
        } catch (Exception e) {
            errors.add("Could not extract file system info: ${e.getMessage()}")
        }
    }
    
    private void extractQualityMetrics() {
        // Basic quality indicators that can be computed quickly
        try {
            if (server && server.nResolutions() > 1) {
                // Check if image has proper pyramid structure
                metadata.has_pyramid = true
                
                def baseWidth = server.getWidth()
                def level1Width = server.getWidth(1)
                metadata.pyramid_factor = Math.round((double)baseWidth / level1Width * 100) / 100
            } else {
                metadata.has_pyramid = false
            }
            
            // Check for common quality indicators
            metadata.is_fluorescence = metadata.num_channels > 3 || !metadata.rgb
            metadata.suggested_analysis_level = suggestAnalysisLevel()
            
        } catch (Exception e) {
            errors.add("Could not extract quality metrics: ${e.getMessage()}")
        }
    }
    
    private int suggestAnalysisLevel() {
        if (!server) return 0
        
        def basePixelSize = metadata.pixel_width_um ?: 0.25
        def targetPixelSize = 1.0 // Target 1 μm/pixel for analysis
        
        for (int level = 0; level < server.nResolutions(); level++) {
            def downsample = server.getDownsampleForResolution(level)
            def effectivePixelSize = basePixelSize * downsample
            
            if (effectivePixelSize >= targetPixelSize) {
                return level
            }
        }
        return server.nResolutions() - 1
    }
    
    private String findMetadataValue(Map metadataMap, List<String> keys) {
        for (String key : keys) {
            def value = metadataMap.find { k, v -> 
                k.toLowerCase().contains(key.toLowerCase()) 
            }?.value
            if (value) return value
        }
        return null
    }
    
    private String extractFilePath(String uri) {
        try {
            if (uri.startsWith("file:")) {
                return Paths.get(new URI(uri)).toString()
            }
            return uri
        } catch (Exception e) {
            return uri
        }
    }
    
    private String getFileExtension(String filePath) {
        if (!filePath) return null
        def lastDot = filePath.lastIndexOf('.')
        return lastDot > 0 ? filePath.substring(lastDot + 1).toLowerCase() : null
    }
}

// Batch processing class
class CohortBatchProcessor {
    
    def project
    def outputDir
    def allMetadata = []
    def processingLog = []
    
    CohortBatchProcessor() {
        this.project = QPEx.getProject()
        setupOutputDirectory()
    }
    
    def processAllImages() {
        if (!project) {
            Dialogs.showErrorMessage("Error", "No project is open. Please open a project first.")
            return
        }
        
        def imageEntries = project.getImageList()
        if (imageEntries.isEmpty()) {
            Dialogs.showWarningMessage("Warning", "No images found in the current project.")
            return
        }
        
        println("Processing ${imageEntries.size()} images for cohort metadata extraction...")
        
        imageEntries.eachWithIndex { entry, index ->
            println("Processing image ${index + 1}/${imageEntries.size()}: ${entry.getImageName()}")
            
            try {
                def extractor = new CohortMetadataExtractor(entry)
                def metadata = extractor.extractMetadata()
                
                if (metadata) {
                    allMetadata.add(metadata)
                    processingLog.add("SUCCESS: ${entry.getImageName()}")
                } else {
                    processingLog.add("FAILED: ${entry.getImageName()} - No metadata extracted")
                }
                
                if (extractor.errors) {
                    processingLog.addAll(extractor.errors.collect { "ERROR in ${entry.getImageName()}: $it" })
                }
                
            } catch (Exception e) {
                processingLog.add("FAILED: ${entry.getImageName()} - ${e.getMessage()}")
                println("Error processing ${entry.getImageName()}: ${e.getMessage()}")
            }
        }
        
        exportResults()
        showSummary()
    }
    
    private void setupOutputDirectory() {
        def projectDir = project?.getPath()?.getParent()
        if (projectDir) {
            outputDir = new File(projectDir.toFile(), CohortConfig.OUTPUT_DIR)
        } else {
            outputDir = new File(System.getProperty("user.home"), CohortConfig.OUTPUT_DIR)
        }
        
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }
    
    private void exportResults() {
        if (allMetadata.isEmpty()) {
            println("No metadata to export.")
            return
        }
        
        exportToCSV()
        exportToJSON()
        exportProcessingLog()
        
        println("Results exported to: ${outputDir.getAbsolutePath()}")
    }
    
    private void exportToCSV() {
        def csvFile = new File(outputDir, CohortConfig.CSV_FILENAME)
        
        try {
            csvFile.withWriter { writer ->
                // Write header
                def allKeys = allMetadata.collectMany { it.keySet() }.unique().sort()
                writer.writeLine(allKeys.join(','))
                
                // Write data
                allMetadata.each { metadata ->
                    def values = allKeys.collect { key ->
                        def value = metadata[key]
                        if (value == null) return ""
                        
                        // Escape commas and quotes for CSV
                        def stringValue = value.toString()
                        if (stringValue.contains(',') || stringValue.contains('"') || stringValue.contains('\n')) {
                            return '"' + stringValue.replace('"', '""') + '"'
                        }
                        return stringValue
                    }
                    writer.writeLine(values.join(','))
                }
            }
            println("CSV exported: ${csvFile.getAbsolutePath()}")
        } catch (Exception e) {
            println("Error exporting CSV: ${e.getMessage()}")
        }
    }
    
    private void exportToJSON() {
        def jsonFile = new File(outputDir, CohortConfig.JSON_FILENAME)
        
        try {
            def json = new JsonBuilder()
            json {
                export_info {
                    date new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                    qupath_version GeneralTools.getVersion()
                    project_name project?.getName() ?: "Unknown"
                    total_images allMetadata.size()
                }
                cohort_metadata allMetadata
            }
            
            jsonFile.text = json.toPrettyString()
            println("JSON exported: ${jsonFile.getAbsolutePath()}")
        } catch (Exception e) {
            println("Error exporting JSON: ${e.getMessage()}")
        }
    }
    
    private void exportProcessingLog() {
        def logFile = new File(outputDir, "processing_log.txt")
        
        try {
            logFile.withWriter { writer ->
                writer.writeLine("Cohort Metadata Extraction Log")
                writer.writeLine("Generated: ${new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date())}")
                writer.writeLine("=" * 50)
                processingLog.each { writer.writeLine(it) }
            }
        } catch (Exception e) {
            println("Error exporting log: ${e.getMessage()}")
        }
    }
    
    private void showSummary() {
        def successful = processingLog.count { it.startsWith("SUCCESS") }
        def failed = processingLog.count { it.startsWith("FAILED") }
        def errors = processingLog.count { it.startsWith("ERROR") }
        
        def summary = """
Cohort Metadata Extraction Complete!

Summary:
- Total images processed: ${successful + failed}
- Successfully processed: ${successful}
- Failed: ${failed}
- Errors encountered: ${errors}

Output directory: ${outputDir.getAbsolutePath()}

Files generated:
- ${CohortConfig.CSV_FILENAME} (for spreadsheet analysis)
- ${CohortConfig.JSON_FILENAME} (for programmatic access)
- processing_log.txt (detailed log)

Next steps:
1. Review the generated metadata files
2. Use the CSV file to filter and select images for your cohort
3. Import the metadata into your analysis pipeline
4. Use the 'suggested_analysis_level' for optimal processing
        """.trim()
        
        println(summary)
        Dialogs.showInfoNotification("Extraction Complete", 
            "Processed ${successful + failed} images. Check the output directory for results.")
    }
}

// Utility functions for integration with other workflows
class CohortUtils {
    
    /**
     * Load cohort metadata from exported JSON file
     */
    static def loadCohortMetadata(String jsonPath) {
        try {
            def jsonSlurper = new JsonSlurper()
            return jsonSlurper.parse(new File(jsonPath))
        } catch (Exception e) {
            println("Error loading cohort metadata: ${e.getMessage()}")
            return null
        }
    }
    
    /**
     * Filter images based on criteria
     */
    static def filterImages(def cohortData, Map<String, Object> criteria) {
        if (!cohortData?.cohort_metadata) return []
        
        return cohortData.cohort_metadata.findAll { metadata ->
            criteria.every { key, value ->
                def metadataValue = metadata[key]
                if (value instanceof Number && metadataValue instanceof Number) {
                    return metadataValue >= value
                } else if (value instanceof String) {
                    return metadataValue?.toString()?.toLowerCase()?.contains(value.toLowerCase())
                } else if (value instanceof List) {
                    return value.contains(metadataValue)
                }
                return metadataValue == value
            }
        }
    }
    
    /**
     * Get images by scanner type
     */
    static def getImagesByScanner(def cohortData, String scannerType) {
        return filterImages(cohortData, [scanner: scannerType])
    }
    
    /**
     * Get images by magnification range
     */
    static def getImagesByMagnification(def cohortData, int minMag, int maxMag) {
        if (!cohortData?.cohort_metadata) return []
        
        return cohortData.cohort_metadata.findAll { metadata ->
            def mag = metadata.estimated_magnification
            return mag && mag >= minMag && mag <= maxMag
        }
    }
}

// Main execution
println("Starting Cohort Metadata Extraction Workflow...")

def processor = new CohortBatchProcessor()
processor.processAllImages()

println("Workflow completed. Use CohortUtils class methods for further analysis.")

// Example usage for filtering (uncomment to use):
/*
// Load the exported metadata
def cohortData = CohortUtils.loadCohortMetadata("path/to/cohort_metadata.json")

// Filter high-magnification images
def highMagImages = CohortUtils.getImagesByMagnification(cohortData, 20, 40)

// Filter by scanner
def aperioImages = CohortUtils.getImagesByScanner(cohortData, "Aperio")

// Custom filtering
def largeImages = CohortUtils.filterImages(cohortData, [
    area_mm2: 100,  // minimum 100 mm²
    file_extension: "svs"
])
*/