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
