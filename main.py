from database import fetch_data_from_mongodb
from algorithm import group_products_by_brand, normalize_prices, rank_products_by_value, display_results

if __name__ == "__main__":
    all_results = fetch_data_from_mongodb()
    
    if not all_results:
        print("No data found in MongoDB. Please scrape data first.")
    else:
        grouped_products = group_products_by_brand(all_results)
        normalized_products = {brand: normalize_prices(products) for brand, products in grouped_products.items()}
        ranked_results = rank_products_by_value(normalized_products)
        print("Price Comparison Results:")
        display_results(ranked_results)
        
