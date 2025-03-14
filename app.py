from flask import Flask, render_template, request, redirect, url_for, flash, session
from flask_login import LoginManager, UserMixin, login_user, login_required, logout_user, current_user
from pymongo import MongoClient
from werkzeug.security import generate_password_hash, check_password_hash
import os
from scraper import scrape_data
from algorithm import normalize_prices, rank_products_globally

app = Flask(__name__)
app.secret_key = os.urandom(24)

# MongoDB connection
client = MongoClient("mongodb://localhost:27017/")
db = client["price_comparison"]
users_collection = db["users"]
amazon_collection = db["amazon"]
jiomart_collection = db["jiomart"]

# Flask-Login setup
login_manager = LoginManager()
login_manager.init_app(app)
login_manager.login_view = 'login'

class User(UserMixin):
    def __init__(self, user_data):
        self.id = str(user_data['_id'])
        self.username = user_data['username']
        self.email = user_data.get('email', '')

@login_manager.user_loader
def load_user(user_id):
    user_data = users_collection.find_one({"_id": user_id})
    if user_data:
        return User(user_data)
    return None

@app.route('/')
def index():
    if 'username' in session:
        return render_template('home.html', username=session['username'])
    return redirect(url_for('login'))

@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        username = request.form['username']
        password = request.form['password']
        
        user = users_collection.find_one({'username': username})
        
        if user and check_password_hash(user['password'], password):
            user_obj = User(user)
            login_user(user_obj)
            session['username'] = username
            flash('Login successful!', 'success')
            return redirect(url_for('index'))
        else:
            flash('Invalid username or password', 'error')
    
    return render_template('login.html')

@app.route('/signup', methods=['GET', 'POST'])
def signup():
    if request.method == 'POST':
        username = request.form['username']
        email = request.form['email']
        password = request.form['password']
        
        # Check if username already exists
        if users_collection.find_one({'username': username}):
            flash('Username already exists', 'error')
            return redirect(url_for('signup'))
        
        # Create new user
        hashed_password = generate_password_hash(password)
        user_id = users_collection.insert_one({
            'username': username,
            'email': email,
            'password': hashed_password
        }).inserted_id
        
        # Log in the new user
        user_data = users_collection.find_one({'_id': user_id})
        user_obj = User(user_data)
        login_user(user_obj)
        session['username'] = username
        
        flash('Account created successfully!', 'success')
        return redirect(url_for('index'))
    
    return render_template('login.html')

@app.route('/logout')
@login_required
def logout():
    logout_user()
    session.pop('username', None)
    flash('You have been logged out', 'info')
    return redirect(url_for('login'))

@app.route('/search', methods=['POST'])
def search():
    query = request.form['query']
    if not query:
        flash('Please enter a search query', 'error')
        return redirect(url_for('index'))
    
    # Scrape data from websites
    try:
        results = scrape_data(query)
        
        if not results:
            flash('No results found', 'error')
            return render_template('home.html', username=session.get('username'))
        
        # Normalize and rank the results globally
        normalized_products = normalize_prices(results)
        ranked_results = rank_products_globally(normalized_products)
        
        return render_template(
            'home.html', 
            username=session.get('username'), 
            results=ranked_results, 
            query=query
        )
    
    except Exception as e:
        flash(f'Error searching for products: {str(e)}', 'error')
        return render_template('home.html', username=session.get('username'))

@app.route('/about')
def about():
    return render_template('about.html', username=session.get('username'))

if __name__ == '__main__':
    app.run(debug=True) 