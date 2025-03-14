from database import fetch_data_from_mongodb
from algorithm import normalize_prices, rank_products_globally, display_results
from app import app

def process_comparison_data():
    """
    Process the data from MongoDB for price comparison.
    This function can be called independently of the web interface.
    """
    all_results = fetch_data_from_mongodb()
    
    if not all_results:
        print("No data found in MongoDB. Please scrape data first.")
        return None
    
    normalized_products = normalize_prices(all_results)
    ranked_results = rank_products_globally(normalized_products)
    
    return ranked_results

if __name__ == "__main__":
    # You can run this file directly to start the web application
    app.run(debug=True)
    
    # Or uncomment the following lines to run the CLI version
    # ranked_results = process_comparison_data()
    # if ranked_results:
    #     print("Price Comparison Results:")
    #     display_results(ranked_results)
        
