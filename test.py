from selenium import webdriver
from selenium.webdriver.edge.service import Service
from selenium.webdriver.edge.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import pandas as pd
import re
# Set up Microsoft Edge options
edge_options = Options()
edge_options.add_argument("--headless")  # Run in headless mode
edge_options.add_argument("--disable-gpu")  # Disable GPU for better compatibility
edge_options.add_argument("--no-sandbox")  # Useful for running in certain environments
edge_options.add_argument("--start-maximized")  # Start maximized

# Specify the path to the EdgeDriver executable
service = Service("C:\\Users\\Sanchit Rastogi\\Downloads\\edgedriver_win64\\msedgedriver.exe")

# Initialize the Edge WebDriver with the service and options
driver = webdriver.Edge(service=service, options=edge_options)

def scrape_amazon_data(search_query):
    try:
        # Open the website
        driver.get("https://www.amazon.in")
        print("Website loaded...")

        # Locate the search bar and enter the query
        search_box = driver.find_element(By.ID, "twotabsearchtextbox")
        search_box.send_keys(search_query)
        search_box.send_keys(Keys.RETURN)

        # Wait for the results to load
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "div.s-main-slot"))
        )
        print("Search results loaded...")

        # Find all product containers
        products = driver.find_elements(By.CSS_SELECTOR, "div.s-main-slot div[data-component-type='s-search-result']")

        # Extract details from each product
        results = []
        for product in products[:10]:  # Limit to first 10 results
            try:
                # Extract title
                title_element = product.find_element(By.CSS_SELECTOR, "h2.a-size-base-plus.a-spacing-none")
                title = title_element.text
                
                # Extract price
                price_element = product.find_element(By.CSS_SELECTOR, "span.a-price-whole")
                price = price_element.text
                
                # Extract link
                link_element = product.find_element(By.CSS_SELECTOR, "a.a-link-normal.s-no-outline")
                link = link_element.get_attribute("href")
                
                # Extract image
                image_element = product.find_element(By.CSS_SELECTOR, "img.s-image")
                image_url = image_element.get_attribute("src")
                
                # Extract weight from title using regex
                weight_match = re.search(r"(\d+(?:\.\d+)?\s?(kg|g|litre|ml))", title, re.IGNORECASE)
                weight = weight_match.group(0) if weight_match else "N/A"
                
                results.append({
                    "title": title,
                    "price": price,
                    "link": link,
                    "image_url": image_url,
                    "weight": weight
                })
            except Exception as e:
                print(f"Error extracting product: {e}")

        # Print the results
        for result in results:
            print(f"Title: {result['title']}")
            print(f"Price: {result['price']}")
            print(f"Link: {result['link']}")
            print(f"Image URL: {result['image_url']}")
            print(f"Weight: {result['weight']}\n")
        
        dataFrame=pd.DataFrame(results)
        dataFrame.to_csv('List.csv',index=False)
        return results 

    except Exception as e:
        print(f"An error occurred: {e}")

    finally:
        # Close the browser
        driver.quit()

def scrape_jiomart_data(search_query):
    try:
        # Open the JioMart website
        driver.get("https://www.jiomart.com")
        print("Website loaded...")

        # Locate the search bar and enter the query
        search_box = driver.find_element(By.ID, "autocomplete-0-input")
        search_box.send_keys(search_query)
        search_box.send_keys(Keys.RETURN)

        # Wait for the search results to load
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "li.ais-InfiniteHits-item"))
        )
        print("Search results loaded...")

        # Find all product containers
        products = driver.find_elements(By.CSS_SELECTOR, "li.ais-InfiniteHits-item")

        # Extract details from each product
        results = []
        for product in products[:10]:  # Limit to first 10 results
            try:
                # Extract title
                title_element = product.find_element(By.CSS_SELECTOR, "div.plp-card-details-name")
                title = title_element.text.strip()
                
                # Extract price
                price_element = product.find_element(By.CSS_SELECTOR, "span.jm-heading-xxs")
                price = price_element.text.strip()
                
                # Extract link
                link_element = product.find_element(By.TAG_NAME, "a")
                link = link_element.get_attribute("href")
                
                # Extract image
                image_element = product.find_element(By.CSS_SELECTOR, "div.plp-card-image img")
                image_url = image_element.get_attribute("src")
                
                # Extract weight from title using regex
                weight_match = re.search(r"(\d+(?:\.\d+)?\s?(kg|g|litre|ml))", title, re.IGNORECASE)
                weight = weight_match.group(0) if weight_match else "N/A"
                
                results.append({
                    "title": title,
                    "price": price,
                    "link": link,
                    "image_url": image_url,
                    "weight": weight
                })
            except Exception as e:
                print(f"Error extracting product: {e}")

        # Print the results
        for result in results:
            print(f"Title: {result['title']}")
            print(f"Price: {result['price']}")
            print(f"Link: {result['link']}")
            print(f"Image URL: {result['image_url']}")
            print(f"Weight: {result['weight']}\n")
        
        # Save results to a CSV file
        data_frame = pd.DataFrame(results)
        data_frame.to_csv("jiomart_products.csv", index=False)
        print("Data saved to 'jiomart_products.csv'")
        
        return results 

    except Exception as e:
        print(f"An error occurred: {e}")

    finally:
        # Close the browser
        driver.quit()

# Test the function
if __name__ == "__main__":
    search_query = "detergent" 
    scraped_data = scrape_jiomart_data(search_query)
    scraped_data1 = scrape_amazon_data(search_query)