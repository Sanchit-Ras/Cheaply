import os
import sys
import importlib.util
import subprocess
import platform

def check_python_version():
    """Check if Python version is 3.8 or higher"""
    print(f"Checking Python version...")
    version = sys.version_info
    if version.major >= 3 and version.minor >= 8:
        print(f"✅ Python version {version.major}.{version.minor}.{version.micro} is compatible")
        return True
    else:
        print(f"❌ Python version {version.major}.{version.minor}.{version.micro} is not compatible. Please use Python 3.8 or higher.")
        return False

def check_package_installed(package_name):
    """Check if a Python package is installed"""
    spec = importlib.util.find_spec(package_name)
    if spec is not None:
        print(f"✅ {package_name} is installed")
        return True
    else:
        print(f"❌ {package_name} is not installed. Please run 'pip install {package_name}'")
        return False

def check_mongodb_running():
    """Check if MongoDB is running"""
    print(f"Checking MongoDB connection...")
    try:
        from pymongo import MongoClient
        client = MongoClient("mongodb://localhost:27017/", serverSelectionTimeoutMS=2000)
        client.server_info()  # Will raise an exception if MongoDB is not running
        print(f"✅ MongoDB is running")
        return True
    except Exception as e:
        print(f"❌ MongoDB is not running or not accessible: {e}")
        print("Please make sure MongoDB is installed and running.")
        return False

def check_chromedriver():
    """Check if ChromeDriver is available"""
    print(f"Checking ChromeDriver...")
    
    # Check common locations
    chromedriver_path = "chromedriver.exe" if platform.system() == "Windows" else "chromedriver"
    possible_paths = [
        chromedriver_path,
        os.path.join(os.getcwd(), chromedriver_path),
        "C:\\Users\\Admin\\Downloads\\chromedriver-win64\\chromedriver.exe",
        "/usr/local/bin/chromedriver",
        "/usr/bin/chromedriver"
    ]
    
    for path in possible_paths:
        if os.path.exists(path):
            print(f"✅ ChromeDriver found at: {path}")
            return True
    
    print(f"❌ ChromeDriver not found. Please download it from https://chromedriver.chromium.org/downloads")
    print(f"   and place it in the project directory or update the path in scraper.py")
    return False

def check_chrome_browser():
    """Check if Chrome browser is installed"""
    print(f"Checking Chrome browser...")
    
    system = platform.system()
    try:
        if system == "Windows":
            # Check common Windows Chrome locations
            chrome_paths = [
                os.path.join(os.environ.get('PROGRAMFILES', 'C:\\Program Files'), 'Google\\Chrome\\Application\\chrome.exe'),
                os.path.join(os.environ.get('PROGRAMFILES(X86)', 'C:\\Program Files (x86)'), 'Google\\Chrome\\Application\\chrome.exe')
            ]
            for path in chrome_paths:
                if os.path.exists(path):
                    print(f"✅ Chrome browser found at: {path}")
                    return True
        elif system == "Darwin":  # macOS
            if os.path.exists("/Applications/Google Chrome.app"):
                print(f"✅ Chrome browser found")
                return True
        elif system == "Linux":
            try:
                subprocess.run(["google-chrome", "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
                print(f"✅ Chrome browser found")
                return True
            except (subprocess.SubprocessError, FileNotFoundError):
                pass
        
        print(f"❌ Chrome browser not found. Please install Google Chrome.")
        return False
    except Exception as e:
        print(f"❌ Error checking Chrome browser: {e}")
        return False

def main():
    """Run all checks"""
    print("Running setup checks for Grocery Price Comparison Website...\n")
    
    checks = [
        check_python_version(),
        check_package_installed("flask"),
        check_package_installed("flask_login"),
        check_package_installed("pymongo"),
        check_package_installed("selenium"),
        check_package_installed("werkzeug"),
        check_mongodb_running(),
        check_chromedriver(),
        check_chrome_browser()
    ]
    
    print("\nSummary:")
    if all(checks):
        print("✅ All checks passed! You're ready to run the application.")
        print("   Start the application with: python app.py")
    else:
        print("❌ Some checks failed. Please fix the issues above before running the application.")

if __name__ == "__main__":
    main() 