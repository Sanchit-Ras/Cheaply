import re
from collections import defaultdict

def group_products_by_brand(products):
    """
    Groups products by their brand or type based on keywords in titles.
    """
    groups = defaultdict(list)
    for product in products:
        # Extract the first word or main brand as the group key (e.g., "Tide", "Surf Excel")
        brand_match = re.search(r'^\w+', product['title'])
        brand = brand_match.group(0) if brand_match else "Unknown"
        groups[brand].append(product)
    return groups

def normalize_prices(products):
    """
    Normalize prices per unit (e.g., per kg or per liter) for all products in the group.
    """
    normalized_products = []
    for product in products:
        try:
            # Extract and clean the price
            price_raw = product.get('price', 'N/A').replace('₹','')

            price = float(re.sub(r'[^\d.]', '', price_raw)) if price_raw != 'N/A' else None
            
            # Extract weight and unit
            weight_match = re.search(r"(\d+(?:\.\d+)?)\s?(kg|g|litre|ml|L)", product.get('weight', ''), re.IGNORECASE)
            if weight_match:
                weight = float(weight_match.group(1))
                unit = weight_match.group(2).lower()
                
                # Normalize weight to kilograms or liters
                if unit in ['g', 'ml']:
                    weight /= 1000
                
                # Calculate price per unit
                if price is not None and weight > 0:
                    price_per_unit = price / weight
                    product['price_per_unit'] = round(price_per_unit, 2)
                else:
                    product['price_per_unit'] = "N/A"
            else:
                product['price_per_unit'] = "N/A"  # Missing or invalid weight
        except Exception as e:
            product['price_per_unit'] = "N/A"
            print(f"Error normalizing price for {product.get('title', 'Unknown Product')}: {e}")
        
        normalized_products.append(product)
    return normalized_products

def rank_products_by_value(groups):
    """
    Ranks products within each group by price per unit.
    """
    ranked_groups = {}
    for brand, products in groups.items():
        # Sort products by price per unit, if available
        ranked_groups[brand] = sorted(
            products, 
            key=lambda x: x['price_per_unit'] if isinstance(x['price_per_unit'], (int, float)) else float('inf')
        )
    return ranked_groups

def display_results(ranked_groups):
    """
    Displays the grouped and ranked results in a user-friendly format.
    """
    print("\n--- Price Comparison Results ---\n")
    for brand, products in ranked_groups.items():
        print(f"Brand: {brand}")
        for product in products:
            print(f"  - Title: {product['title']}")
            print(f"    Price: ₹{product['price']}")
            print(f"    Price per Unit: {product['price_per_unit']} ₹/kg or ₹/L")
            print(f"    Link: {product['link']}")
        print("\n")
