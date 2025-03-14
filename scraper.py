from pymongo import MongoClient
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import re
import os

# MongoDB connection
client = MongoClient("mongodb://localhost:27017/")  # Connect to local MongoDB
db = client["price_comparison"]  # Database name
amazon_collection = db["amazon"]  # Collection for Amazon data
jiomart_collection = db["jiomart"]  # Collection for JioMart data

# Set up Chrome options
chrome_options = Options()
chrome_options.add_argument("--headless")
chrome_options.add_argument("--disable-gpu")
chrome_options.add_argument("--no-sandbox")
chrome_options.add_argument("--start-maximized")
user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
chrome_options.add_argument(f"user-agent={user_agent}")

# Update this path to your chromedriver location
# You can download chromedriver from https://chromedriver.chromium.org/downloads
chromedriver_path = "chromedriver.exe"  # Default path in project directory
if not os.path.exists(chromedriver_path):
    # Try common locations
    possible_paths = [
        "C:\\Users\\Admin\\Downloads\\chromedriver-win64\\chromedriver.exe",
        "/usr/local/bin/chromedriver",
        "/usr/bin/chromedriver"
    ]
    for path in possible_paths:
        if os.path.exists(path):
            chromedriver_path = path
            break

service = Service(chromedriver_path)

def scrape_amazon_data(driver, search_query):
    try:
        # Open Amazon website
        driver.get("https://www.amazon.in")
        print("Amazon website loaded...")

        # Search for the query
        search_box = driver.find_element(By.ID, "twotabsearchtextbox")
        search_box.send_keys(search_query)
        search_box.send_keys(Keys.RETURN)

        # Wait for the results to load
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "div.s-main-slot"))
        )
        print("Amazon search results loaded...")

        # Locate product containers
        products = driver.find_elements(By.CSS_SELECTOR, "div.s-main-slot div[data-component-type='s-search-result']")
        results = []

        for product in products[:10]:  # Process the first 10 products
            try:
                # Extract title
                title_element = product.find_element(By.CSS_SELECTOR, "h2 span")
                title = title_element.text

                # Extract price
                try:
                    price_element = product.find_element(By.CSS_SELECTOR, "span.a-price-whole")
                    price = price_element.text.replace('₹','')
                except Exception:
                    price = "NA"  # Handle missing price

                # Extract link
                link_element = product.find_element(By.CSS_SELECTOR, "a.a-link-normal.s-no-outline")
                link = link_element.get_attribute("href")

                # Extract image
                image_element = product.find_element(By.CSS_SELECTOR, "img.s-image")
                image_url = image_element.get_attribute("src")

                # Extract weight (if mentioned in title)
                weight_match = re.search(r"(\d+(?:\.\d+)?\s?(kg|g|litre|ml|L))", title, re.IGNORECASE)
                weight = weight_match.group(0) if weight_match else "N/A"

                # Append product details
                results.append({
                    "title": title,
                    "price": price,
                    "link": link,
                    "image_url": image_url,
                    "weight": weight,
                    "source": "Amazon"
                })
            except Exception as e:
                print(f"Error extracting product details: {e}")

        # Insert data into MongoDB
        if results:
            amazon_collection.delete_many({})
            amazon_collection.insert_many(results)
            print("Amazon data saved to MongoDB.")
        else:
            print("No results to save.")

        return results

    except Exception as e:
        print(f"Amazon Error: {e}")
        return []


def scrape_jiomart_data(driver, search_query):
    try:
        driver.get("https://www.jiomart.com")
        print("JioMart website loaded...")

        search_box = driver.find_element(By.ID, "autocomplete-0-input")
        search_box.send_keys(search_query)
        search_box.send_keys(Keys.RETURN)

        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "li.ais-InfiniteHits-item"))
        )
        print("JioMart search results loaded...")

        products = driver.find_elements(By.CSS_SELECTOR, "li.ais-InfiniteHits-item")
        results = []
        for product in products[:10]:
            try:
                title = product.find_element(By.CSS_SELECTOR, "div.plp-card-details-name").text.strip()
                price = product.find_element(By.CSS_SELECTOR, "span.jm-heading-xxs").text.strip().replace('₹','')
                link = product.find_element(By.TAG_NAME, "a").get_attribute("href")
                image_url = product.find_element(By.CSS_SELECTOR, "div.plp-card-image img").get_attribute("src")
                weight = re.search(r"(\d+(?:\.\d+)?\s?(kg|g|litre|ml|L))", title, re.IGNORECASE)
                weight = weight.group(0) if weight else "N/A"
                results.append({
                    "title": title, 
                    "price": price, 
                    "link": link, 
                    "image_url": image_url, 
                    "weight": weight,
                    "source": "JioMart"
                })
            except Exception as e:
                print(f"JioMart Error extracting product: {e}")
        
        # Insert data into MongoDB
        if results:
            jiomart_collection.delete_many({})
            jiomart_collection.insert_many(results)
            print("JioMart data saved to MongoDB.")
        else:
            print("No results to save.")
            
        return results

    except Exception as e:
        print(f"JioMart Error: {e}")
        return []


def scrape_data(search_query):
    """
    Main function to scrape data from all sources for a given search query
    """
    driver = webdriver.Chrome(service=service, options=chrome_options)
    try:
        amazon_results = scrape_amazon_data(driver, search_query)
        jiomart_results = scrape_jiomart_data(driver, search_query)
        return amazon_results + jiomart_results
    finally:
        driver.quit()


if __name__ == "__main__":
    search_query = "detergent"  # Default query for testing
    scrape_data(search_query)
