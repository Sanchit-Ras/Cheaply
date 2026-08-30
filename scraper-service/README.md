# Cheaply

A modern web application that compares grocery prices from different online platforms to help users find the best deals.

## Features

- **Real-time Price Comparison** — Scrapes data from Amazon India and JioMart to provide up-to-date price comparisons
- **Smart Ranking** — Products ranked globally by price per unit, regardless of brand or store
- **Price Normalisation** — Compares prices across different package sizes and units (kg, g, L, mL)
- **Store Filtering** — Filter results by store (Amazon / JioMart)
- **Sorting Controls** — Sort by price, price per unit, or name
- **Search History** — Recent searches saved per user for quick re-searching
- **Best Value Indicator** — Highlights the best deal with savings percentage
- **User Authentication** — Secure login and signup with password strength validation
- **Responsive Design** — Works on desktop and mobile devices

## Tech Stack

- **Backend**: Python 3.9+, Flask, Gunicorn
- **Frontend**: HTML5, CSS3 (Inter font), JavaScript, Bootstrap 5
- **Database**: MongoDB
- **Web Scraping**: Selenium with Chrome WebDriver
- **Deployment**: Docker, Render

## Prerequisites

- Python 3.9+
- MongoDB (local or cloud via MongoDB Atlas)
- Chrome Browser + ChromeDriver (auto-managed by `webdriver-manager`)

## Quick Start

1. **Clone the repository:**

   ```bash
   git clone https://github.com/yourusername/cheaply.git
   cd cheaply
   ```

2. **Create and activate a virtual environment:**

   ```bash
   python -m venv .venv
   # Windows
   .venv\Scripts\activate
   # macOS/Linux
   source .venv/bin/activate
   ```

3. **Install dependencies:**

   ```bash
   pip install -r requirements.txt
   ```

4. **Set up environment variables:**

   ```bash
   cp .env.example .env
   # Edit .env with your MongoDB URI and secret key
   ```

5. **Run the application:**

   ```bash
   python app.py
   ```

6. **Open your browser:**
   ```
   http://localhost:5000
   ```

## Project Structure

```
Cheaply/
├── app.py              # Flask application (routes, auth, search)
├── config.py           # Centralised configuration & MongoDB connection
├── scraper.py          # Selenium scrapers for Amazon & JioMart
├── algorithm.py        # Price normalisation & ranking algorithms
├── database.py         # MongoDB data access layer
├── main.py             # CLI entry point
├── check_setup.py      # Setup verification script
├── requirements.txt    # Python dependencies
├── Dockerfile          # Production Docker image (gunicorn)
├── build.sh            # Chrome installation script (deployment)
├── .env.example        # Environment variable documentation
├── templates/          # Jinja2 HTML templates
│   ├── base.html       # Base layout with navbar & footer
│   ├── home.html       # Search + product cards + about section
│   ├── login.html      # Login / signup slider
│   └── about.html      # About page with features & how-it-works
└── static/
    ├── css/style.css   # Design system (Inter, tokens, cards, animations)
    ├── js/main.js      # Core JS (tooltips, scroll-reveal, validation)
    ├── js/auth.js      # Auth form interactions
    └── images/         # Logo, SVG illustrations
```

## Docker

```bash
docker build -t cheaply .
docker run -p 5000:5000 -e MONGO_URI="your_mongo_uri" cheaply
```

## Environment Variables

| Variable       | Required | Default                      | Description               |
| -------------- | -------- | ---------------------------- | ------------------------- |
| `MONGO_URI`    | Yes      | `mongodb://localhost:27017/` | MongoDB connection string |
| `SECRET_KEY`   | Yes      | dev default                  | Flask secret key          |
| `DB_NAME`      | No       | `price_comparison`           | MongoDB database name     |
| `FLASK_DEBUG`  | No       | `false`                      | Enable Flask debug mode   |
| `PORT`         | No       | `5000`                       | Server port               |
| `CHROME_BIN`   | No       | auto-detect                  | Chrome binary path        |
| `LOG_LEVEL`    | No       | `INFO`                       | Python logging level      |
| `CORS_ORIGINS` | No       | `*`                          | Allowed CORS origins      |

## License

This project is licensed under the MIT License.
