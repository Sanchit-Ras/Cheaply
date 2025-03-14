import re

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

def rank_products_globally(products):
    """
    Ranks all products globally by price per unit.
    """
    ranked_products = sorted(
        products,
        key=lambda x: x['price_per_unit'] if isinstance(x['price_per_unit'], (int, float)) else float('inf')
    )
    return ranked_products

def display_results(ranked_products):
    """
    Displays the ranked results in a simple table format.
    """
    print("\n--- Price Comparison Results ---\n")
    print("-" * 90)
    count=0
    for product in ranked_products:
        title =product['title']
        price = product['price']
        price_per_unit = product['price_per_unit']
        store = "Amazon" if "amazon" in product['link'] else "JioMart"
        link=product['link']
        print(f"Title: {title}")
        print(f"Price: {price}")
        print(f"Price per Unit: {price_per_unit}")
        print(f"Store: {store}")
        print(f"Link: {link}\n")
        count=count+1
    print(f"Total number of products: {count}")
