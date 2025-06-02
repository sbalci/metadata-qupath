/**
 * QuPath 0.6+ Compatible Cohort Metadata Extractor
 * 
 * This version is specifically designed for QuPath 0.6.0-rc3 and later versions
 * with updated API calls and better error handling.
 * 
 * Author: Digital Pathology Workflow System
 * Version: 2.0 (QuPath 0.6+ Compatible)
 */

import qupath.lib.gui.scripting.QPEx
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServer
import qupath.lib.common.GeneralTools
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File

// Configuration
def OUTPUT_DIR = "cohort_metadata"
def CSV_FILENAME = "cohort_metadata_v06.csv"

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

println("Starting QuPath 0.6+ compatible metadata extraction for ${imageEntries.size()} images...")
println("QuPath Version: ${GeneralTools.getVersion()}")

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
def successCount = 0

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
        
        // Extract metadata with QuPath 0.6+ compatible methods
        def metadata = [:]
        
        // Basic info
        metadata.image_name = entry.getImageName()
        metadata.project_name = project.getName()
        metadata.extraction_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
        metadata.qupath_version = GeneralTools.getVersion()
        
        // Image properties - using QuPath 0.6+ compatible methods
        metadata.width_pixels = server.getWidth()
        metadata.height_pixels = server.getHeight()
        metadata.num_channels = server.nChannels()
        metadata.num_z_slices = server.nZSlices()
        metadata.num_timepoints = server.nTimepoints()
        metadata.pyramid_levels = server.nResolutions()
        metadata.is_rgb = server.isRGB()
        
        // Try different methods for image type (QuPath 0.6+ compatibility)
        try {
            metadata.image_class = server.getImageClass()?.toString() ?: "Unknown"
        } catch (Exception e) {
            metadata.image_class = "Unknown"
        }
        
        try {
            metadata.pixel_type = server.getPixelType()?.toString() ?: "Unknown"
        } catch (Exception e) {
            metadata.pixel_type = "Unknown"
        }
        
        try {
            metadata.server_type = server.getServerType() ?: server.getClass().getSimpleName()
        } catch (Exception e) {
            metadata.server_type = server.getClass().getSimpleName()
        }
        
        // Calculate area
        metadata.image_area_pixels = (long)metadata.width_pixels * metadata.height_pixels
        
        // Pixel calibration
        def pixelCalibration = server.getPixelCalibration()
        if (pixelCalibration) {
            metadata.pixel_width_um = pixelCalibration.getPixelWidthMicrons()
            metadata.pixel_height_um = pixelCalibration.getPixelHeightMicrons()
            
            try {
                metadata.pixel_units = pixelCalibration.getPixelWidthUnit()?.toString() ?: "pixel"
            } catch (Exception e) {
                metadata.pixel_units = "pixel"
            }
            
            // Calculate physical dimensions
            if (metadata.pixel_width_um > 0) {
                metadata.width_um = metadata.width_pixels * metadata.pixel_width_um
                metadata.height_um = metadata.height_pixels * metadata.pixel_height_um
                metadata.area_mm2 = (metadata.width_um * metadata.height_um) / 1_000_000
                
                // Estimate magnification (0.25 μm/pixel ≈ 40x)
                metadata.estimated_magnification = Math.round(0.25 / metadata.pixel_width_um * 40)
            }
        }
        
        // File information
        def uris = server.getURIs()
        if (uris && !uris.isEmpty()) {
            def uriString = uris[0].toString()
            metadata.file_uri = uriString
            
            // Extract file path
            try {
                if (uriString.startsWith("file:")) {
                    metadata.file_path = new File(new URI(uriString)).getAbsolutePath()
                } else {
                    metadata.file_path = uriString
                }
                
                // Get file extension
                def fileName = metadata.file_path
                def lastDot = fileName.lastIndexOf('.')
                metadata.file_extension = lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "unknown"
                
                // Get file size
                def file = new File(metadata.file_path)
                if (file.exists()) {
                    metadata.file_size_bytes = file.length()
                    metadata.file_size_mb = Math.round(file.length() / (1024 * 1024) * 100) / 100
                    metadata.last_modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified()))
                }
            } catch (Exception e) {
                metadata.file_path = uriString
                metadata.file_extension = "unknown"
                errors.add("Could not extract file info for ${entry.getImageName()}: ${e.getMessage()}")
            }
        }
        
        // Associated images
        try {
            def associatedImages = server.getAssociatedImageList()
            if (associatedImages) {
                metadata.has_macro_image = associatedImages.contains('macro')
                metadata.has_label_image = associatedImages.contains('label')
                metadata.associated_image_count = associatedImages.size()
                metadata.associated_images = associatedImages.join(', ')
            }
        } catch (Exception e) {
            metadata.associated_image_count = 0
        }
        
        // Quality indicators
        metadata.has_pyramid = server.nResolutions() > 1
        if (metadata.has_pyramid && server.nResolutions() > 1) {
            try {
                def baseWidth = server.getWidth(0)
                def level1Width = server.getWidth(1)
                metadata.pyramid_factor = Math.round((double)baseWidth / level1Width * 100) / 100
            } catch (Exception e) {
                metadata.pyramid_factor = 2.0 // Default assumption
            }
        }
        
        // Suggest analysis level (target ~1 μm/pixel)
        metadata.suggested_analysis_level = 0
        if (metadata.pixel_width_um && metadata.pixel_width_um > 0) {
            def targetPixelSize = 1.0
            for (int level = 0; level < server.nResolutions(); level++) {
                try {
                    def downsample = server.getDownsampleForResolution(level)
                    def effectivePixelSize = metadata.pixel_width_um * downsample
                    if (effectivePixelSize >= targetPixelSize) {
                        metadata.suggested_analysis_level = level
                        break
                    }
                } catch (Exception e) {
                    break
                }
            }
        }
        
        // Extract comprehensive scanner info from metadata
        try {
            def serverMetadata = server.getMetadata()
            if (serverMetadata) {
                // Enhanced metadata extraction with specific field mapping
                serverMetadata.each { key, value ->
                    try {
                        def keyStr = key.toString()
                        def keyLower = keyStr.toLowerCase()
                        def valueStr = value?.toString()
                        
                        // Scanner identification
                        if (keyLower.contains('scannertype') || keyStr == 'ScannerType') {
                            metadata.scanner_type = valueStr
                        } else if (keyLower.contains('scanner') || keyLower.contains('instrument') || keyLower.contains('device')) {
                            metadata.scanner = valueStr
                        } else if (keyStr == 'ScanScope ID' || keyLower.contains('scanscope')) {
                            metadata.scanscope_id = valueStr
                        }
                        
                        // Magnification and calibration
                        else if (keyStr == 'Apparent Magnification' || keyLower.contains('apparent magnification')) {
                            metadata.apparent_magnification = valueStr
                        } else if (keyStr == 'MPP' || keyLower.contains('mpp')) {
                            metadata.mpp = valueStr
                        } else if (keyLower.contains('magnification') || keyLower.contains('objective')) {
                            if (!metadata.objective_magnification) metadata.objective_magnification = valueStr
                        }
                        
                        // Date and time
                        else if (keyStr == 'Date') {
                            metadata.scan_date = valueStr
                        } else if (keyStr == 'Time') {
                            metadata.scan_time = valueStr
                        } else if (keyStr == 'Time Zone' || keyLower.contains('timezone')) {
                            metadata.time_zone = valueStr
                        }
                        
                        // Acquisition settings
                        else if (keyStr == 'Exposure Scale' || keyLower.contains('exposure scale')) {
                            metadata.exposure_scale = valueStr
                        } else if (keyStr == 'Exposure Time' || keyLower.contains('exposure time')) {
                            metadata.exposure_time = valueStr
                        } else if (keyStr == 'Filtered') {
                            metadata.filtered = valueStr
                        } else if (keyStr == 'Gamma') {
                            metadata.gamma = valueStr
                        }
                        
                        // Position and slide info
                        else if (keyStr == 'Left') {
                            metadata.stage_left = valueStr
                        } else if (keyStr == 'Top') {
                            metadata.stage_top = valueStr
                        } else if (keyStr == 'Slide') {
                            metadata.slide_number = valueStr
                        } else if (keyStr == 'Rack') {
                            metadata.rack = valueStr
                        } else if (keyStr == 'SessionMode') {
                            metadata.session_mode = valueStr
                        }
                        
                        // Technical specifications
                        else if (keyStr == 'StripeWidth' || keyLower.contains('stripe')) {
                            metadata.stripe_width = valueStr
                        } else if (keyStr == 'Scan Warning' || keyLower.contains('warning')) {
                            metadata.scan_warning = valueStr
                        } else if (keyStr == 'BigTIFF' || keyLower.contains('bigtiff')) {
                            metadata.big_tiff = valueStr
                        }
                        
                        // Compression details
                        else if (keyStr == 'Compression Type' || keyLower.contains('compression type')) {
                            metadata.compression_type = valueStr
                        } else if (keyStr == 'Compression Quality' || keyLower.contains('compression quality')) {
                            metadata.compression_quality = valueStr
                        } else if (keyStr == 'Compression Ratio' || keyLower.contains('compression ratio')) {
                            metadata.compression_ratio = valueStr
                        } else if (keyLower.contains('compression')) {
                            metadata.compression = valueStr
                        }
                        
                        // Organization and structure
                        else if (keyStr == 'Organization' || keyLower.contains('organization')) {
                            metadata.organization = valueStr
                        } else if (keyStr == 'Tile Width' || keyLower.contains('tile width')) {
                            metadata.tile_width = valueStr
                        } else if (keyStr == 'Tile Height' || keyLower.contains('tile height')) {
                            metadata.tile_height = valueStr
                        } else if (keyStr == 'ICC Profile' || keyLower.contains('icc')) {
                            metadata.icc_profile = valueStr
                        }
                        
                        // Image specifications
                        else if (keyStr == 'Image Type' || keyLower.contains('image type')) {
                            metadata.original_image_type = valueStr
                        } else if (keyStr == 'Image Width' || keyLower.contains('image width')) {
                            metadata.original_width = valueStr
                        } else if (keyStr == 'Image Height' || keyLower.contains('image height')) {
                            metadata.original_height = valueStr
                        } else if (keyStr == 'Image Depth' || keyLower.contains('image depth')) {
                            metadata.image_depth = valueStr
                        } else if (keyStr == 'Image Channels' || keyLower.contains('image channels')) {
                            metadata.original_channels = valueStr
                        } else if (keyStr == 'Image Bit Depth' || keyLower.contains('bit depth')) {
                            metadata.original_bit_depth = valueStr
                        }
                        
                        // Software and version
                        else if (keyLower.contains('software') || keyLower.contains('version')) {
                            if (!metadata.scanner_software) metadata.scanner_software = valueStr
                        }
                        
                        // Description field (often contains useful info)
                        else if (keyStr == 'Description' || keyLower.contains('description')) {
                            metadata.description = valueStr
                        }
                        
                    } catch (Exception e) {
                        // Skip problematic metadata fields
                    }
                }
                
                // Create a combined scan datetime if both date and time are available
                if (metadata.scan_date && metadata.scan_time) {
                    try {
                        metadata.scan_datetime = "${metadata.scan_date} ${metadata.scan_time}"
                        if (metadata.time_zone) {
                            metadata.scan_datetime += " ${metadata.time_zone}"
                        }
                    } catch (Exception e) {
                        // Skip if combination fails
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Could not extract scanner metadata for ${entry.getImageName()}: ${e.getMessage()}")
        }
        
        // Additional computed fields
        metadata.is_fluorescence = metadata.num_channels > 3 || !metadata.is_rgb
        metadata.needs_pyramid = !metadata.has_pyramid && metadata.image_area_pixels > 100_000_000 // >100MP
        
        allMetadata.add(metadata)
        successCount++
        
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
    
    println("SUCCESS: CSV exported to ${csvFile.getAbsolutePath()}")
    
} catch (Exception e) {
    println("ERROR: Failed to export CSV: ${e.getMessage()}")
}

// Create comprehensive summary report
def summaryFile = new File(outputDir, "detailed_summary_v06.txt")
try {
    summaryFile.withWriter { writer ->
        writer.writeLine("QuPath 0.6+ Compatible Metadata Extraction Summary")
        writer.writeLine("Generated: ${new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date())}")
        writer.writeLine("QuPath Version: ${GeneralTools.getVersion()}")
        writer.writeLine("Project: ${project.getName()}")
        writer.writeLine("Output Directory: ${outputDir.getAbsolutePath()}")
        writer.writeLine("=" * 60)
        
        writer.writeLine("Processing Results:")
        writer.writeLine("- Total images in project: ${imageEntries.size()}")
        writer.writeLine("- Successfully processed: ${successCount}")
        writer.writeLine("- Failed: ${errors.size()}")
        writer.writeLine("- Success rate: ${Math.round((successCount * 100.0) / imageEntries.size())}%")
        
        if (!allMetadata.isEmpty()) {
            // Calculate detailed statistics
            def avgWidth = allMetadata.collect { it.width_pixels ?: 0 }.sum() / allMetadata.size()
            def avgHeight = allMetadata.collect { it.height_pixels ?: 0 }.sum() / allMetadata.size()
            
            def pixelSizes = allMetadata.collect { it.pixel_width_um }.findAll { it && it > 0 }
            def avgPixelSize = pixelSizes ? pixelSizes.sum() / pixelSizes.size() : 0
            def minPixelSize = pixelSizes ? pixelSizes.min() : 0
            def maxPixelSize = pixelSizes ? pixelSizes.max() : 0
            
            def scanners = allMetadata.collect { it.scanner }.findAll { it }.unique()
            def formats = allMetadata.collect { it.file_extension }.findAll { it }.countBy { it }
            def serverTypes = allMetadata.collect { it.server_type }.findAll { it }.countBy { it }
            
            def totalSizeMB = allMetadata.collect { it.file_size_mb ?: 0 }.sum()
            def avgSizeMB = totalSizeMB / allMetadata.size()
            
            def withPyramids = allMetadata.count { it.has_pyramid }
            def largImages = allMetadata.count { it.area_mm2 && it.area_mm2 > 100 }
            
            writer.writeLine("\nDetailed Statistics:")
            writer.writeLine("- Average image size: ${Math.round(avgWidth)} x ${Math.round(avgHeight)} pixels")
            writer.writeLine("- Total dataset size: ${Math.round(totalSizeMB)} MB")
            writer.writeLine("- Average file size: ${Math.round(avgSizeMB)} MB")
            
            if (avgPixelSize > 0) {
                writer.writeLine("- Pixel size range: ${String.format('%.3f', minPixelSize)} - ${String.format('%.3f', maxPixelSize)} μm")
                writer.writeLine("- Average pixel size: ${String.format('%.3f', avgPixelSize)} μm")
            }
            
            writer.writeLine("- Images with pyramids: ${withPyramids}/${allMetadata.size()}")
            writer.writeLine("- Large images (>100 mm²): ${largImages}")
            
            writer.writeLine("\nScanner Distribution:")
            if (scanners.isEmpty()) {
                writer.writeLine("- No scanner information detected")
            } else {
                scanners.each { scanner ->
                    def count = allMetadata.count { it.scanner == scanner }
                    writer.writeLine("- ${scanner}: ${count} images")
                }
            }
            
            writer.writeLine("\nFile Format Distribution:")
            formats.each { format, count ->
                writer.writeLine("- ${format.toUpperCase()}: ${count} files")
            }
            
            writer.writeLine("\nServer Type Distribution:")
            serverTypes.each { serverType, count ->
                writer.writeLine("- ${serverType}: ${count} images")
            }
        }
        
        if (!errors.isEmpty()) {
            writer.writeLine("\nErrors Encountered (${errors.size()}):")
            errors.each { error ->
                writer.writeLine("- ${error}")
            }
        }
        
        writer.writeLine("\nRecommendations:")
        if (successCount < imageEntries.size()) {
            writer.writeLine("- Review error log for failed images")
        }
        
        def lowResImages = allMetadata.count { it.pixel_width_um && it.pixel_width_um > 1.0 }
        if (lowResImages > 0) {
            writer.writeLine("- ${lowResImages} images have low resolution (>1.0 μm/pixel)")
        }
        
        def noPyramidImages = allMetadata.count { !it.has_pyramid && it.image_area_pixels > 50_000_000 }
        if (noPyramidImages > 0) {
            writer.writeLine("- ${noPyramidImages} large images lack pyramid structure (may impact performance)")
        }
        
        writer.writeLine("\nOutput Files:")
        writer.writeLine("- Metadata CSV: ${csvFile.getName()}")
        writer.writeLine("- This summary: ${summaryFile.getName()}")
    }
    
    println("Summary exported: ${summaryFile.getAbsolutePath()}")
    
} catch (Exception e) {
    println("ERROR: Failed to export summary: ${e.getMessage()}")
}

// Final report
println("\n" + "=" * 60)
println("QUPATH 0.6+ COMPATIBLE METADATA EXTRACTION COMPLETE")
println("=" * 60)
println("Successfully processed: ${successCount}/${imageEntries.size()} images")
println("Output directory: ${outputDir.getAbsolutePath()}")
println("Files created:")
println("  - ${CSV_FILENAME}")
println("  - detailed_summary_v06.txt")

if (!errors.isEmpty()) {
    println("\nWarning: ${errors.size()} errors encountered. Check the summary file for details.")
}

// Quick statistics
if (!allMetadata.isEmpty()) {
    println("\nQuick Dataset Overview:")
    
    def pixelSizes = allMetadata.collect { it.pixel_width_um }.findAll { it && it > 0 }
    if (pixelSizes) {
        def minPS = pixelSizes.min()
        def maxPS = pixelSizes.max()
        def avgPS = pixelSizes.sum() / pixelSizes.size()
        println("- Pixel size range: ${String.format('%.3f', minPS)} - ${String.format('%.3f', maxPS)} μm (avg: ${String.format('%.3f', avgPS)})")
    }
    
    def totalSize = allMetadata.collect { it.file_size_mb ?: 0 }.sum()
    println("- Total dataset size: ${Math.round(totalSize)} MB")
    
    def formats = allMetadata.collect { it.file_extension }.findAll { it }.countBy { it }
    println("- File formats: ${formats.collect { k, v -> "${k.toUpperCase()}(${v})" }.join(', ')}")
    
    def withPyramids = allMetadata.count { it.has_pyramid }
    println("- Images with pyramids: ${withPyramids}/${allMetadata.size()}")
    
    def scanners = allMetadata.collect { it.scanner }.findAll { it }.unique()
    if (scanners) {
        println("- Scanners detected: ${scanners.join(', ')}")
    }
}

println("\nNext Steps:")
println("1. Open ${CSV_FILENAME} in Excel/LibreOffice to explore your cohort")
println("2. Use the metadata to define inclusion/exclusion criteria")
println("3. Filter images based on quality metrics and requirements")
println("4. Proceed with your analysis using the suggested_analysis_level")
println("=" * 60)

println("\nScript completed successfully!")
println("Total execution time: ${(System.currentTimeMillis() - 0) / 1000} seconds")