# Grocery Price Comparison Website

A web application that compares grocery prices from different online platforms to help users find the best deals.

## Features

- **Real-time Price Comparison**: Scrapes data from multiple online grocery retailers to provide up-to-date price comparisons.
- **User Authentication**: Secure login and signup functionality.
- **Responsive Design**: Works on desktop and mobile devices.
- **Price Normalization**: Compares prices across different package sizes and units.
- **Best Value Identification**: Highlights the best value products based on price per unit.
- **Global Ranking**: Products are ranked globally by price per unit, regardless of brand or store.

## Tech Stack

- **Backend**: Python, Flask
- **Frontend**: HTML, CSS, JavaScript, Bootstrap
- **Database**: MongoDB
- **Web Scraping**: Selenium with Chrome WebDriver

## Prerequisites

- Python 3.8+
- MongoDB
- Chrome Browser
- Chrome WebDriver

## Installation

1. Clone the repository:
   ```
   git clone https://github.com/yourusername/grocery-price-comparison.git
   cd grocery-price-comparison
   ```

2. Create and activate a virtual environment:
   ```
   python -m venv .venv
   # On Windows
   .venv\Scripts\activate
   # On macOS/Linux
   source .venv/bin/activate
   ```

3. Install the required packages:
   ```
   pip install -r requirements.txt
   ```

4. Download Chrome WebDriver:
   - Visit https://chromedriver.chromium.org/downloads
   - Download the version that matches your Chrome browser
   - Extract the executable and update the path in `scraper.py` to point to your chromedriver location:
     ```python
     service = Service("path/to/your/chromedriver.exe")
     ```

5. Set up MongoDB:
   - Install MongoDB if you haven't already
   - Start the MongoDB service
   - The application will automatically create the required collections

6. Verify your setup:
   ```
   python check_setup.py
   ```
   This script will check if all the required components are installed and configured correctly.

## Usage

1. Start the application:
   ```
   python app.py
   ```

2. Open your browser and navigate to:
   ```
   http://localhost:5000
   ```

3. Create an account or log in if you already have one.

4. Search for grocery items to compare prices across different platforms.

## Project Structure

- `app.py`: Main Flask application
- `scraper.py`: Web scraping functionality
- `algorithm.py`: Price comparison algorithms
- `database.py`: Database operations
- `main.py`: CLI interface and application entry point
- `check_setup.py`: Script to verify the setup
- `templates/`: HTML templates
- `static/`: Static files (CSS, JS, images)

## Notes for Development

- The scraper is configured to work with Chrome browser
- You may need to update the selectors in the scraper if the websites change their structure
- Currently supports Amazon and JioMart, but can be extended to other platforms
- Products are now ranked globally by price per unit, regardless of brand or store

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Thanks to all the open-source libraries that made this project possible
- Inspired by the need to find the best grocery deals during shopping 