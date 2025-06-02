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