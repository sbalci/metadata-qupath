/**
 * Simple QuPath Cohort Metadata Extractor
 * 
 * This is a simplified version that works with basic QuPath Groovy environment
 * without external dependencies. Perfect for testing and basic metadata extraction.
 * 
 * Author: Digital Pathology Workflow System
 * Version: 1.0-Simple
 * Compatible with: QuPath 0.3.x and later
 */

import qupath.lib.gui.scripting.QPEx
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServer
import qupath.lib.common.GeneralTools
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File

// Simple configuration
def OUTPUT_DIR = "cohort_metadata"
def CSV_FILENAME = "simple_cohort_metadata.csv"

// Get current project
def project = QPEx.getProject()
if (!project) {
    println("ERROR: No project is open. Please open a project first.")
    return
}

def imageEntries = project.getImageList()
if (imageEntries.isEmpty()) {
    println("WARNING: No images found in the current project.")
    return
}

println("Starting simple metadata extraction for ${imageEntries.size()} images...")

// Create output directory
def projectDir = project.getPath()?.getParent()
def outputDir = projectDir ? new File(projectDir.toFile(), OUTPUT_DIR) : new File(System.getProperty("user.home"), OUTPUT_DIR)
if (!outputDir.exists()) {
    outputDir.mkdirs()
}

// Prepare CSV file
def csvFile = new File(outputDir, CSV_FILENAME)
def allMetadata = []
def errors = []

// Process each image
imageEntries.eachWithIndex { entry, index ->
    println("Processing ${index + 1}/${imageEntries.size()}: ${entry.getImageName()}")
    
    try {
        def imageData = entry.readImageData()
        def server = imageData?.getServer()
        
        if (!server) {
            errors.add("Could not load server for: ${entry.getImageName()}")
            return
        }
        
        // Extract basic metadata
        def metadata = [:]
        
        // Basic info
        metadata.image_name = entry.getImageName()
        metadata.extraction_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
        
        // Image properties
        metadata.width_pixels = server.getWidth()
        metadata.height_pixels = server.getHeight()
        metadata.num_channels = server.nChannels()
        metadata.num_z_slices = server.nZSlices()
        metadata.pyramid_levels = server.nResolutions()
        
        // Use getImageClass() instead of getImageType() for QuPath 0.6+
        try {
            metadata.image_type = server.getImageClass()?.toString() ?: "Unknown"
        } catch (Exception e) {
            metadata.image_type = "Unknown"
        }
        
        metadata.bit_depth = server.getPixelType()?.toString() ?: "Unknown"
        metadata.is_rgb = server.isRGB()
        
        // Calculate area
        metadata.image_area_pixels = (long)metadata.width_pixels * metadata.height_pixels
        
        // Pixel calibration
        def pixelCalibration = server.getPixelCalibration()
        if (pixelCalibration) {
            metadata.pixel_width_um = pixelCalibration.getPixelWidthMicrons()
            metadata.pixel_height_um = pixelCalibration.getPixelHeightMicrons()
            metadata.pixel_units = pixelCalibration.getPixelWidthUnit()?.toString() ?: "pixel"
            
            // Calculate physical dimensions and estimated magnification
            if (metadata.pixel_width_um > 0) {
                metadata.width_um = metadata.width_pixels * metadata.pixel_width_um
                metadata.height_um = metadata.height_pixels * metadata.pixel_height_um
                metadata.area_mm2 = (metadata.width_um * metadata.height_um) / 1_000_000
                
                // Estimate magnification (0.25 μm/pixel ≈ 40x)
                metadata.estimated_magnification = Math.round(0.25 / metadata.pixel_width_um * 40)
            }
        }
        
        // File information
        def uri = server.getURIs()
        if (uri && !uri.isEmpty()) {
            def uriString = uri[0].toString()
            metadata.file_uri = uriString
            
            // Extract file path
            try {
                if (uriString.startsWith("file:")) {
                    metadata.file_path = new File(new URI(uriString)).getAbsolutePath()
                } else {
                    metadata.file_path = uriString
                }
                
                // Get file extension
                def lastDot = metadata.file_path.lastIndexOf('.')
                metadata.file_extension = lastDot > 0 ? metadata.file_path.substring(lastDot + 1).toLowerCase() : "unknown"
                
                // Get file size
                def file = new File(metadata.file_path)
                if (file.exists()) {
                    metadata.file_size_bytes = file.length()
                    metadata.file_size_mb = Math.round(file.length() / (1024 * 1024) * 100) / 100
                }
            } catch (Exception e) {
                metadata.file_path = uriString
                metadata.file_extension = "unknown"
            }
        }
        
        // Server information
        metadata.server_type = server.getClass().getSimpleName()
        
        // Associated images
        def associatedImages = server.getAssociatedImageList()
        if (associatedImages) {
            metadata.has_macro_image = associatedImages.contains('macro')
            metadata.has_label_image = associatedImages.contains('label')
            metadata.associated_image_count = associatedImages.size()
        }
        
        // Quality indicators
        metadata.has_pyramid = server.nResolutions() > 1
        if (metadata.has_pyramid && server.nResolutions() > 1) {
            def baseWidth = server.getWidth()
            def level1Width = server.getWidth(1)
            metadata.pyramid_factor = Math.round((double)baseWidth / level1Width * 100) / 100
        }
        
        // Suggest analysis level (target ~1 μm/pixel)
        metadata.suggested_analysis_level = 0
        if (metadata.pixel_width_um && metadata.pixel_width_um > 0) {
            def targetPixelSize = 1.0
            for (int level = 0; level < server.nResolutions(); level++) {
                def downsample = server.getDownsampleForResolution(level)
                def effectivePixelSize = metadata.pixel_width_um * downsample
                if (effectivePixelSize >= targetPixelSize) {
                    metadata.suggested_analysis_level = level
                    break
                }
            }
        }
        
        // Extract scanner info from metadata if available
        def serverMetadata = server.getMetadata()
        if (serverMetadata) {
            // Look for common scanner metadata fields
            serverMetadata.each { key, value ->
                def keyStr = key.toString().toLowerCase()
                def valueStr = value?.toString()
                
                if (keyStr.contains('scanner') || keyStr.contains('instrument')) {
                    metadata.scanner = valueStr
                } else if (keyStr.contains('date') || keyStr.contains('time')) {
                    if (!metadata.scan_date) metadata.scan_date = valueStr
                } else if (keyStr.contains('magnification') || keyStr.contains('objective')) {
                    if (!metadata.objective_magnification) metadata.objective_magnification = valueStr
                } else if (keyStr.contains('compression')) {
                    metadata.compression = valueStr
                }
            }
        }
        
        allMetadata.add(metadata)
        
    } catch (Exception e) {
        def errorMsg = "Error processing ${entry.getImageName()}: ${e.getMessage()}"
        errors.add(errorMsg)
        println("ERROR: ${errorMsg}")
    }
}

// Export to CSV
println("\nExporting metadata to CSV...")

try {
    csvFile.withWriter { writer ->
        if (!allMetadata.isEmpty()) {
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
    }
    
    println("CSV exported successfully: ${csvFile.getAbsolutePath()}")
    
} catch (Exception e) {
    println("ERROR: Failed to export CSV: ${e.getMessage()}")
}

// Create summary report
def summaryFile = new File(outputDir, "extraction_summary.txt")
try {
    summaryFile.withWriter { writer ->
        writer.writeLine("Simple Cohort Metadata Extraction Summary")
        writer.writeLine("Generated: ${new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date())}")
        writer.writeLine("QuPath Version: ${GeneralTools.getVersion()}")
        writer.writeLine("Project: ${project.getName()}")
        writer.writeLine("=" * 50)
        
        writer.writeLine("Processing Results:")
        writer.writeLine("- Total images in project: ${imageEntries.size()}")
        writer.writeLine("- Successfully processed: ${allMetadata.size()}")
        writer.writeLine("- Failed: ${errors.size()}")
        
        if (!allMetadata.isEmpty()) {
            // Calculate basic statistics
            def avgWidth = allMetadata.collect { it.width_pixels ?: 0 }.sum() / allMetadata.size()
            def avgHeight = allMetadata.collect { it.height_pixels ?: 0 }.sum() / allMetadata.size()
            
            def pixelSizes = allMetadata.collect { it.pixel_width_um }.findAll { it && it > 0 }
            def avgPixelSize = pixelSizes ? pixelSizes.sum() / pixelSizes.size() : 0
            
            def scanners = allMetadata.collect { it.scanner }.findAll { it }.unique()
            def formats = allMetadata.collect { it.file_extension }.findAll { it }.countBy { it }
            
            writer.writeLine("\nSummary Statistics:")
            writer.writeLine("- Average image size: ${Math.round(avgWidth)} x ${Math.round(avgHeight)} pixels")
            if (avgPixelSize > 0) {
                writer.writeLine("- Average pixel size: ${String.format('%.3f', avgPixelSize)} μm")
            }
            writer.writeLine("- Scanners found: ${scanners.join(', ')}")
            writer.writeLine("- File formats:")
            formats.each { format, count ->
                writer.writeLine("  * ${format}: ${count} files")
            }
        }
        
        if (!errors.isEmpty()) {
            writer.writeLine("\nErrors encountered:")
            errors.each { error ->
                writer.writeLine("- ${error}")
            }
        }
        
        writer.writeLine("\nOutput files:")
        writer.writeLine("- CSV data: ${csvFile.getName()}")
        writer.writeLine("- This summary: ${summaryFile.getName()}")
    }
    
    println("Summary exported: ${summaryFile.getAbsolutePath()}")
    
} catch (Exception e) {
    println("ERROR: Failed to export summary: ${e.getMessage()}")
}

// Final report
println("\n" + "=" * 60)
println("SIMPLE COHORT METADATA EXTRACTION COMPLETE")
println("=" * 60)
println("Processed: ${allMetadata.size()}/${imageEntries.size()} images")
println("Output directory: ${outputDir.getAbsolutePath()}")
println("Files created:")
println("  - ${CSV_FILENAME}")
println("  - extraction_summary.txt")

if (!errors.isEmpty()) {
    println("\nWarning: ${errors.size()} errors encountered. Check the summary file for details.")
}

println("\nNext steps:")
println("1. Open ${CSV_FILENAME} in Excel or similar software")
println("2. Review and filter the data for your cohort definition")
println("3. Use the metadata to guide your analysis parameters")
println("=" * 60)

// Example analysis of the extracted data
if (!allMetadata.isEmpty()) {
    println("\nQuick Analysis:")
    
    def withPixelSize = allMetadata.findAll { it.pixel_width_um && it.pixel_width_um > 0 }
    if (withPixelSize) {
        def minPixelSize = withPixelSize.collect { it.pixel_width_um }.min()
        def maxPixelSize = withPixelSize.collect { it.pixel_width_um }.max()
        println("- Pixel size range: ${String.format('%.3f', minPixelSize)} - ${String.format('%.3f', maxPixelSize)} μm")
        
        def highRes = withPixelSize.findAll { it.pixel_width_um < 0.5 }.size()
        def lowRes = withPixelSize.findAll { it.pixel_width_um > 1.0 }.size()
        println("- High resolution images (<0.5 μm/pixel): ${highRes}")
        println("- Lower resolution images (>1.0 μm/pixel): ${lowRes}")
    }
    
    def withPyramid = allMetadata.findAll { it.has_pyramid }.size()
    println("- Images with pyramids: ${withPyramid}/${allMetadata.size()}")
    
    def largeImages = allMetadata.findAll { it.area_mm2 && it.area_mm2 > 100 }.size()
    println("- Large images (>100 mm²): ${largeImages}")
}

println("\nScript completed successfully!")