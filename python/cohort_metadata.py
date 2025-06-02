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