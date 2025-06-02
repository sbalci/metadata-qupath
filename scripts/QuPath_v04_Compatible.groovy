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
import java.nio.file.Paths
import java.util.regex.Pattern

// Configuration
class CohortConfig {
    static final String OUTPUT_DIR = "cohort_metadata"
    static final String CSV_FILENAME = "cohort_metadata.csv"
    static final String SUMMARY_FILENAME = "extraction_summary.txt"
    static final boolean INCLUDE_THUMBNAILS = false
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
        
        // Use getImageClass() instead of getImageType() for QuPath 0.6+
        try {
            metadata.image_type = server.getImageClass()?.toString() ?: "Unknown"
        } catch (Exception e) {
            metadata.image_type = "Unknown"
        }
        
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
        exportSummary()
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
    
    private void exportSummary() {
        def summaryFile = new File(outputDir, CohortConfig.SUMMARY_FILENAME)
        
        try {
            summaryFile.withWriter { writer ->
                writer.writeLine("Cohort Metadata Extraction Summary")
                writer.writeLine("Generated: ${new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date())}")
                writer.writeLine("QuPath Version: ${GeneralTools.getVersion()}")
                writer.writeLine("Project: ${project?.getName() ?: 'Unknown'}")
                writer.writeLine("Total Images: ${allMetadata.size()}")
                writer.writeLine("=" * 50)
                
                // Calculate summary statistics
                def scanners = allMetadata.collect { it.scanner }.findAll { it }.unique()
                def avgWidth = allMetadata.collect { it.width_pixels }.findAll { it }.sum() / allMetadata.size()
                def avgHeight = allMetadata.collect { it.height_pixels }.findAll { it }.sum() / allMetadata.size()
                def avgPixelSize = allMetadata.collect { it.pixel_width_um }.findAll { it && it > 0 }.sum() / 
                                 allMetadata.collect { it.pixel_width_um }.findAll { it && it > 0 }.size()
                
                writer.writeLine("Summary Statistics:")
                writer.writeLine("Scanners found: ${scanners.join(', ')}")
                writer.writeLine("Average image size: ${Math.round(avgWidth)} x ${Math.round(avgHeight)} pixels")
                writer.writeLine("Average pixel size: ${String.format('%.3f', avgPixelSize)} μm")
                
                // File format distribution
                def formats = allMetadata.collect { it.file_extension }.findAll { it }.countBy { it }
                writer.writeLine("File formats:")
                formats.each { format, count ->
                    writer.writeLine("  ${format}: ${count} files")
                }
            }
            println("Summary exported: ${summaryFile.getAbsolutePath()}")
        } catch (Exception e) {
            println("Error exporting summary: ${e.getMessage()}")
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
- ${CohortConfig.SUMMARY_FILENAME} (summary statistics)
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
     * Load cohort metadata from exported CSV file
     */
    static def loadCohortMetadata(String csvPath) {
        try {
            def file = new File(csvPath)
            if (!file.exists()) {
                println("CSV file not found: ${csvPath}")
                return null
            }
            
            def lines = file.readLines()
            if (lines.isEmpty()) return []
            
            def headers = lines[0].split(',')
            def data = []
            
            for (int i = 1; i < lines.size(); i++) {
                def values = lines[i].split(',')
                def record = [:]
                
                for (int j = 0; j < headers.size() && j < values.size(); j++) {
                    def header = headers[j].trim()
                    def value = values[j].trim()
                    
                    // Try to convert numeric values
                    if (value.isNumber()) {
                        record[header] = value.contains('.') ? Double.parseDouble(value) : Long.parseLong(value)
                    } else if (value.toLowerCase() == 'true' || value.toLowerCase() == 'false') {
                        record[header] = Boolean.parseBoolean(value)
                    } else {
                        record[header] = value.isEmpty() ? null : value
                    }
                }
                data.add(record)
            }
            
            return data
        } catch (Exception e) {
            println("Error loading cohort metadata: ${e.getMessage()}")
            return null
        }
    }
    
    /**
     * Filter images based on criteria
     */
    static def filterImages(def cohortData, Map<String, Object> criteria) {
        if (!cohortData) return []
        
        return cohortData.findAll { metadata ->
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
        if (!cohortData) return []
        
        return cohortData.findAll { metadata ->
            def mag = metadata.estimated_magnification
            return mag && mag >= minMag && mag <= maxMag
        }
    }
    
    /**
     * Get summary statistics from cohort data
     */
    static def getSummaryStats(def cohortData) {
        if (!cohortData || cohortData.isEmpty()) return [:]
        
        def stats = [:]
        stats.total_images = cohortData.size()
        
        // Scanner distribution
        def scanners = cohortData.collect { it.scanner }.findAll { it }.countBy { it }
        stats.scanners = scanners
        
        // Average dimensions
        def widths = cohortData.collect { it.width_pixels }.findAll { it }
        def heights = cohortData.collect { it.height_pixels }.findAll { it }
        stats.avg_width = widths.sum() / widths.size()
        stats.avg_height = heights.sum() / heights.size()
        
        // Pixel size statistics
        def pixelSizes = cohortData.collect { it.pixel_width_um }.findAll { it && it > 0 }
        if (pixelSizes) {
            stats.avg_pixel_size = pixelSizes.sum() / pixelSizes.size()
            stats.min_pixel_size = pixelSizes.min()
            stats.max_pixel_size = pixelSizes.max()
        }
        
        // File format distribution
        def formats = cohortData.collect { it.file_extension }.findAll { it }.countBy { it }
        stats.file_formats = formats
        
        return stats
    }
}

// Main execution
println("Starting Cohort Metadata Extraction Workflow...")

def processor = new CohortBatchProcessor()
processor.processAllImages()

println("Workflow completed. Use CohortUtils class methods for further analysis.")

// Example usage for filtering (uncomment to use):
/*
// Load the exported metadata from CSV
def cohortData = CohortUtils.loadCohortMetadata("path/to/cohort_metadata.csv")

// Get summary statistics
def stats = CohortUtils.getSummaryStats(cohortData)
println("Total images: ${stats.total_images}")
println("Scanners: ${stats.scanners}")

// Filter high-magnification images
def highMagImages = CohortUtils.getImagesByMagnification(cohortData, 20, 40)

// Filter by scanner
def aperioImages = CohortUtils.getImagesByScanner(cohortData, "Aperio")

// Custom filtering
def largeImages = CohortUtils.filterImages(cohortData, [
    area_mm2: 100,  // minimum 100 mm²
    file_extension: "svs"
])

println("Found ${highMagImages.size()} high magnification images")
println("Found ${aperioImages.size()} Aperio images")
println("Found ${largeImages.size()} large images")
*/