from pymongo import MongoClient

client = MongoClient("mongodb://localhost:27017/")
db = client["price_comparison"]
amazon_collection = db["amazon"]
jiomart_collection = db["jiomart"]

def fetch_data_from_mongodb():
    """
    Fetches product data from MongoDB for both Amazon and JioMart collections.
    """
    amazon_data = list(amazon_collection.find({}, {'_id': 0}))
    jiomart_data = list(jiomart_collection.find({}, {'_id': 0}))
    return amazon_data + jiomart_data
