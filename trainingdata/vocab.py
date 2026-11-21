"""Static vocabulary for the synthetic dataset. All entities are fictional/impersonal."""

from __future__ import annotations

from typing import NamedTuple


class Bank(NamedTuple):
    name: str
    short: str
    package: str
    sender: str


class App(NamedTuple):
    name: str
    package: str
    kind: str


class Merchant(NamedTuple):
    id: str
    name: str
    category: str
    type: str


BANKS = [
    Bank("HDFC Bank", "HDFC", "com.snapwork.hdfc", "VK-HDFCBK"),
    Bank("State Bank of India", "SBI", "com.sbi.lotusintouch", "AD-SBIINB"),
    Bank("ICICI Bank", "ICICI", "com.icicibank.mobilebanking", "VM-ICICICB"),
    Bank("Axis Bank", "AXIS", "com.axis.mobile", "AD-AXISBK"),
    Bank("Kotak Mahindra Bank", "KOTAK", "com.kotak.mobilebanking", "VK-KOTAKBK"),
    Bank("Union Bank of India", "UNION", "com.unionbankofindia.unionmobile", "AD-UNIONBK"),
    Bank("Punjab National Bank", "PNB", "com.pnb.mobilebanking", "VM-PNBBK"),
    Bank("Bank of Baroda", "BOB", "com.bankofbaroda.mconnect", "AD-BARODABK"),
    Bank("Canara Bank", "CANARA", "com.canarabank.mobility", "VK-CANBK"),
    Bank("IDFC FIRST Bank", "IDFC", "com.idfcfirstbank.retail", "AD-IDFCBK"),
    Bank("Yes Bank", "YES", "com.yesbank.mobile", "VM-YESBK"),
    Bank("IndusInd Bank", "INDUSIND", "com.indusind.mobilebanking", "AD-INDUSBK"),
    Bank("Federal Bank", "FEDERAL", "com.fedmobile", "VK-FEDBK"),
    Bank("AU Small Finance Bank", "AU", "com.aubank.in", "AD-AUBK"),
    Bank("RBL Bank", "RBL", "com.rbl.mobilebanking", "VM-RBLBK"),
    Bank("Indian Bank", "INDIAN", "com.indianbank.mobilebanking", "AD-INDNBK"),
    Bank("Bank of India", "BOI", "com.bankofindia.mobile", "VK-BOIBK"),
    Bank("Central Bank of India", "CENTRAL", "com.centralbank.mobilebanking", "AD-CENBK"),
    Bank("South Indian Bank", "SIB", "com.sib.mobile", "VM-SIBBK"),
    Bank("Karur Vysya Bank", "KVB", "com.kvb.mobilebanking", "AD-KVBBK"),
    Bank("Bandhan Bank", "BANDHAN", "com.bandhan.mobilebanking", "VK-BANDBK"),
    Bank("DBS Bank India", "DBS", "com.dbs.in.mobilebanking", "AD-DBSBK"),
    Bank("HSBC India", "HSBC", "com.hsbc.mobilebanking", "VM-HSBCBK"),
    Bank("Standard Chartered", "SC", "com.sc.mobilebanking", "AD-SCBANK"),
]

UPI_APPS = [
    App("Google Pay", "com.google.android.apps.nbu.paisa.user", "upi"),
    App("PhonePe", "com.phonepe.app", "upi"),
    App("Paytm", "net.one97.paytm", "upi"),
    App("BHIM", "in.org.npci.upiapp", "upi"),
    App("Amazon Pay", "in.amazon.mShop.android.shopping", "upi"),
    App("CRED UPI", "com.dreamplug.androidapp", "upi"),
    App("Mobikwik", "com.mobikwik_new", "upi"),
    App("Freecharge", "com.freecharge.android", "upi"),
    App("slice", "com.slice", "upi"),
    App("Navi", "com.naviapp", "upi"),
    App("Fi Money", "com.fi.money", "upi"),
    App("Jupiter", "com.jupiter.money", "upi"),
    App("super.money", "com.supermoney.app", "upi"),
    App("Airtel Payments Bank", "com.airtel.airtelpaymentsbank", "upi"),
    App("WhatsApp Pay", "com.whatsapp", "upi"),
]

WALLET_APPS = [
    App("Amazon Pay", "in.amazon.mShop.android.shopping", "wallet"),
    App("Paytm", "net.one97.paytm", "wallet"),
    App("Mobikwik", "com.mobikwik_new", "wallet"),
    App("Freecharge", "com.freecharge.android", "wallet"),
    App("PhonePe", "com.phonepe.app", "wallet"),
]

SOCIAL_APPS = [
    App("Instagram", "com.instagram.android", "social"),
    App("Reddit", "com.reddit.frontpage", "social"),
    App("YouTube", "com.google.android.youtube", "social"),
    App("X", "com.twitter.android", "social"),
    App("Snapchat", "com.snapchat.android", "social"),
    App("Facebook", "com.facebook.katana", "social"),
]

GENERIC_APPS = [
    App("SIMPMusic", "com.simpmusicplayer.offlinesimpmusic", "media"),
    App("Spotify", "com.spotify.music", "media"),
    App("Clock", "com.oneplus.deskclock", "utility"),
    App("Clash of Clans", "com.supercell.clashofclans", "game"),
    App("Files", "com.google.android.documentsui", "utility"),
    App("Play Store", "com.android.vending", "utility"),
    App("Hotstar", "in.startv.hotstar", "media"),
    App("JioTV", "com.jio.tv", "media"),
]

# Apps that push their own payment receipt after a checkout. These matter for the gate
# because their notifications have NO account number and NO UTR — the exact shape that the
# bank-SMS-dominated part of the dataset used to teach the model to reject.
MERCHANT_APPS = [
    App("Swiggy", "in.swiggy.android", "food"),
    App("Zomato", "com.application.zomato", "food"),
    App("Blinkit", "com.grofers.customerapp", "grocery"),
    App("Zepto", "com.zeptoconsumerapp", "grocery"),
    App("BigBasket", "com.bigbasket.mobileapp", "grocery"),
    App("Domino's", "com.Dominos", "food"),
    App("Uber", "com.ubercab", "travel"),
    App("Ola", "com.olacabs.customer", "travel"),
    App("Flipkart", "com.flipkart.android", "shopping"),
    App("Myntra", "com.myntra.android", "shopping"),
]

MERCHANT_TABLE: dict[str, list[tuple[str, str]]] = {
    "Food & Dining": [
        ("swiggy", "Swiggy"), ("zomato", "Zomato"), ("blinkit", "Blinkit"),
        ("bigbasket", "BigBasket"), ("dominos", "Domino's"), ("mcdonalds", "McDonald's"),
        ("kfc", "KFC"), ("starbucks", "Starbucks"), ("chaipoint", "Chai Point"),
        ("eatfit", "EatFit"), ("rebelfoods", "Rebel Foods"), ("zepto", "Zepto"),
    ],
    "Transportation": [
        ("uber", "Uber"), ("ola", "Ola"), ("rapido", "Rapido"), ("bmtc", "BMTC"),
        ("namma_metro", "Namma Metro"), ("irctc", "IRCTC"), ("redbus", "RedBus"),
        ("yulu", "Yulu"), ("blusmart", "BluSmart"), ("meru", "Meru Cabs"),
        ("ather_grid", "Ather Grid"), ("indian_oil", "Indian Oil"),
    ],
    "Shopping": [
        ("amazon", "Amazon"), ("flipkart", "Flipkart"), ("myntra", "Myntra"),
        ("ajio", "Ajio"), ("nykaa", "Nykaa"), ("croma", "Croma"),
        ("reliance_digital", "Reliance Digital"), ("decathlon", "Decathlon"),
        ("lenskart", "Lenskart"), ("tatacliq", "Tata Cliq"), ("meesho", "Meesho"),
        ("firstcry", "FirstCry"),
    ],
    "Bills & Utilities": [
        ("airtel", "Airtel"), ("jio", "Jio"), ("vi", "Vi"), ("bsnl", "BSNL"),
        ("tata_power", "Tata Power"), ("bescom", "BESCOM"), ("bwssb", "BWSSB"),
        ("act_fibernet", "ACT Fibernet"), ("hathway", "Hathway"),
        ("lpg_gas", "LPG Gas"), ("tata_play", "Tata Play"), ("indane", "Indane Gas"),
    ],
    "Entertainment": [
        ("netflix", "Netflix"), ("prime_video", "Prime Video"),
        ("hotstar", "Disney+ Hotstar"), ("spotify", "Spotify"),
        ("bookmyshow", "BookMyShow"), ("pvr", "PVR Cinemas"), ("inox", "INOX"),
        ("zee5", "Zee5"), ("sonyliv", "SonyLIV"), ("gaana", "Gaana"),
        ("youtube_premium", "YouTube Premium"), ("sun_nxt", "Sun NXT"),
    ],
    "Healthcare": [
        ("apollo_pharmacy", "Apollo Pharmacy"), ("practo", "Practo"), ("tmg", "1mg"),
        ("pharmeasy", "PharmEasy"), ("fortis", "Fortis Hospital"),
        ("manipal", "Manipal Hospital"), ("narayana", "Narayana Health"),
        ("medplus", "MedPlus"), ("netmeds", "Netmeds"), ("thyrocare", "Thyrocare"),
        ("lal_pathlabs", "Dr Lal PathLabs"), ("max_hospital", "Max Hospital"),
    ],
    "Education": [
        ("byjus", "Byju's"), ("unacademy", "Unacademy"), ("vedantu", "Vedantu"),
        ("coursera", "Coursera"), ("udemy", "Udemy"), ("great_learning", "Great Learning"),
        ("physics_wallah", "Physics Wallah"), ("upgrad", "upGrad"),
        ("simplilearn", "Simplilearn"), ("scaler", "Scaler"), ("toppr", "Toppr"),
        ("khan_academy", "Khan Academy"),
    ],
    "Housing": [
        ("nobroker", "NoBroker"), ("magicbricks", "MagicBricks"),
        ("housing_com", "Housing.com"), ("acres99", "99acres"),
        ("urban_company", "Urban Company"), ("housejoy", "Housejoy"),
        ("rent_payment", "Rent Payment"), ("maintenance", "Maintenance"),
        ("nestaway", "Nestaway"), ("stanza", "Stanza Living"),
        ("zolo", "Zolo"), ("mygate", "MyGate"),
    ],
    "Insurance": [
        ("lic", "LIC"), ("hdfc_life", "HDFC Life"),
        ("icici_pru", "ICICI Prudential"), ("sbi_life", "SBI Life"),
        ("max_life", "Max Life"), ("bajaj_allianz", "Bajaj Allianz"),
        ("star_health", "Star Health"), ("niva_bupa", "Niva Bupa"),
        ("new_india", "New India Assurance"), ("tata_aig", "Tata AIG"),
        ("policybazaar", "PolicyBazaar"), ("acko", "Acko"),
    ],
    "Investments": [
        ("zerodha_coin", "Zerodha Coin"), ("groww", "Groww"),
        ("indmoney", "INDmoney"), ("kuvera", "Kuvera"),
        ("paytm_money", "Paytm Money"), ("angel_one", "Angel One"),
        ("upstox", "Upstox"), ("fivepaisa", "5paisa"),
        ("motilal", "Motilal Oswal"), ("icici_direct", "ICICI Direct"),
        ("hdfc_sec", "HDFC Securities"), ("kotak_sec", "Kotak Securities"),
    ],
    "Salary": [
        ("infosys", "Infosys"), ("tcs", "TCS"), ("wipro", "Wipro"),
        ("accenture", "Accenture"), ("cognizant", "Cognizant"),
        ("hcltech", "HCLTech"), ("ibm_india", "IBM India"),
        ("capgemini", "Capgemini"), ("deloitte", "Deloitte India"),
        ("amazon_india", "Amazon India"), ("microsoft_india", "Microsoft India"),
        ("flipkart_internet", "Flipkart Internet"),
    ],
    "Refund": [
        ("amazon_refund", "Amazon Refund"), ("flipkart_refund", "Flipkart Refund"),
        ("myntra_refund", "Myntra Refund"), ("swiggy_refund", "Swiggy Refund"),
        ("zomato_refund", "Zomato Refund"), ("uber_refund", "Uber Refund"),
        ("irctc_refund", "IRCTC Refund"), ("ajio_refund", "Ajio Refund"),
        ("nykaa_refund", "Nykaa Refund"), ("bigbasket_refund", "BigBasket Refund"),
        ("mmt_refund", "MakeMyTrip Refund"), ("bms_refund", "BookMyShow Refund"),
    ],
    "Gift": [
        ("gpay_rewards", "Google Pay Rewards"), ("phonepe_rewards", "PhonePe Rewards"),
        ("paytm_cashback", "Paytm Cashback"), ("amazonpay_cashback", "Amazon Pay Cashback"),
        ("cred_rewards", "CRED Rewards"), ("mobikwik_cashback", "Mobikwik Cashback"),
        ("freecharge_cashback", "Freecharge Cashback"), ("bhim_rewards", "BHIM Rewards"),
        ("scratch_card", "Scratch Card"), ("referral_bonus", "Referral Bonus"),
        ("festival_bonus", "Festival Bonus"), ("bank_cashback", "Bank Cashback"),
    ],
    "Investment Returns": [
        ("hdfc_fd", "HDFC FD Interest"), ("sbi_fd", "SBI FD Interest"),
        ("icici_fd", "ICICI FD Interest"), ("axis_fd", "Axis FD Interest"),
        ("kotak_fd", "Kotak FD Interest"), ("pnb_fd", "PNB FD Interest"),
        ("canara_fd", "Canara FD Interest"), ("idfc_fd", "IDFC FD Interest"),
        ("bajaj_fd", "Bajaj Finance FD"), ("shriram_fd", "Shriram FD"),
        ("sbi_dividend", "SBI Dividend"), ("hdfc_dividend", "HDFC Dividend"),
    ],
    "Uncategorized": [
        ("atm", "ATM Withdrawal"), ("cash_deposit", "Cash Deposit"),
        ("p2p", "P2P Transfer"), ("self_transfer", "Self Transfer"),
        ("wallet_load", "Wallet Load"), ("cheque", "Cheque Clearing"),
        ("bank_charges", "Bank Charges"), ("sms_charges", "SMS Charges"),
        ("annual_fee", "Card Annual Fee"), ("gst", "GST Payment"),
        ("loan_emi", "Loan EMI"), ("cc_bill", "Credit Card Bill"),
    ],
}

INCOME_CATEGORIES = ("Salary", "Refund", "Gift", "Investment Returns")
EXPENSE_CATEGORIES = tuple(
    c for c in MERCHANT_TABLE if c not in INCOME_CATEGORIES and c != "Uncategorized"
)
ALL_CATEGORIES = tuple(MERCHANT_TABLE)


def build_merchants() -> list[Merchant]:
    out: list[Merchant] = []
    for category, rows in MERCHANT_TABLE.items():
        for slug, name in rows:
            if category == "Salary":
                kind = "EMPLOYER"
            elif category in INCOME_CATEGORIES:
                kind = "PLATFORM"
            elif category == "Uncategorized":
                kind = "BANK"
            else:
                kind = "MERCHANT"
            out.append(Merchant(f"m_{slug}", name, category, kind))
    return out


PEOPLE = [
    "Ramesh", "Suresh", "Mahesh", "Ganesh", "Rajesh", "Dinesh", "Rahul", "Amit",
    "Priya", "Sneha", "Vikram", "Anjali", "Rohit", "Pooja", "Deepak", "Manoj",
    "Kavita", "Sachin", "Neha", "Sunil", "Vijay", "Arun", "Kiran", "Ajay",
    "Sanjay", "Naveen", "Pradeep", "Santosh", "Sandeep", "Rohan", "Aarav", "Advait",
    "Vihaan", "Kabir", "Arjun", "Dhruv", "Meera", "Diya", "Ananya", "Ishita",
    "Ravi", "Swati", "Bhavna", "Ramesh Kumar", "Suresh Sharma", "Rahul Verma",
    "Priya Sharma", "Amit Patel", "Sneha Rao", "Vikram Singh",
    "Aarav K", "Diya S", "Rohan M", "Ishita R", "Kabir T", "Meera N",
    "Advait P", "Saanvi G", "Vihaan D", "Anaya B", "Reyansh V", "Myra J",
    "Arjun L", "Kiara H", "Dhruv A", "Aadhya C", "Yash W", "Sara F",
    "Neel Q", "Riya Z", "Om E", "Tara I", "Veer O", "Nisha U",
    "Manav Y", "Pooja X", "Kiran S", "Asha M", "Nikhil R", "Divya T",
    "Rahul N", "Sneha P", "Vikram G", "Anjali D", "Suresh B", "Lakshmi V",
    "Harish J", "Deepa L", "Manoj H", "Shruti A", "Gopal C", "Vidya W",
    "Ajay F", "Nandini Q", "Pranav Z", "Ira E", "Tejas I", "Maya O",
]

CITIES = [
    "KORAMANGALA", "INDIRANAGAR", "WHITEFIELD", "JAYANAGAR", "HSR LAYOUT",
    "MARATHAHALLI", "ELECTRONIC CITY", "BTM LAYOUT", "YELAHANKA", "RAJAJINAGAR",
    "MALLESHWARAM", "HEBBAL", "BANASHANKARI", "JP NAGAR", "SARJAPUR ROAD",
]

CHAT_PREVIEWS_FINANCIAL = [
    "₹20/- added to account", "₹20/- has been credited to your bigbasket account",
    "Payment of ₹1,250 completed", "Your order of ₹499 has been delivered",
    "Rs.500 cashback credited", "Sent ₹1,000 to Rohan", "Received ₹250 from Meera",
    "Your bill of ₹799 is due", "Transaction successful", "UPI payment failed",
    "Salary credited", "Your account balance is ₹12,340", "Paid ₹180 to BLINKIT",
    "Refund of ₹299 processed", "Rs.2,000 debited from A/c XX0383",
    "Your payment is pending", "EMI of ₹2,450 due tomorrow",
]

CHAT_PREVIEWS_PLAIN = [
    "Sent a sticker", "Sent you a reel", "Sent you a video", "Sent you a photo",
    "Sent you a picture", "Sent you a document", "Sent you a link", "Sent a voice note",
    "Who will she nominate?", "Happy birthday!", "Check this out", "Meeting at 5pm",
    "Photo", "Voice message (0:12)", "Typing...", "Joined the group",
    "Changed the group name", "See you tomorrow", "Thanks!", "Good morning",
    "Where are you?", "Call me when free", "This is hilarious", "Did you see the match?",
    "Lunch at 1?", "On my way",
]

CHAT_SENDERS = [
    "Family", "College Friends", "Office Team", "Cricket Group", "Apartment Association",
    "Placement Cell", "DSATM CSE", "Hostel Buddies", "Cousins", "Neighbourhood Watch",
    "Project Group", "Alumni 2024", "Gym Friends", "Trip Planning", "Book Club",
    "Music Lovers", "Foodies", "Roommates", "Startup Folks", "Photography Club",
]

CHAT_PEOPLE = [
    "Jyothis K P", "Ravi", "Ananya", "Sohan", "Meera", "Arun", "Priya", "Karthik",
    "Divya", "Naveen", "Shreya", "Vikram", "Pooja", "Manoj", "Lakshmi", "Ajay",
    "Sneha", "Rahul", "Deepa", "Harish", "Nandini", "Gopal", "Ira", "Tejas",
]

INSTAGRAM_USERS = [
    "the.travel.gram", "foodie.diaries", "campus.vibes", "tech.daily", "click.n.klicks",
    "midnight.ramen", "fitness.first", "bookish.owl", "pixel.poet", "weekend.wanderer",
    "chai.lover", "startup.stories", "meme.central", "film.buff", "design.dose",
]

SUBJECTS = [
    "Your weekly job digest", "New opportunities near you", "Your application was viewed",
    "Webinar invitation", "Account statement attached", "Newsletter: This week in tech",
    "You have a new message", "Complete your profile", "Interview scheduled",
]

HEADLINES = [
    "Markets close higher as IT stocks rally", "New metro line opens next month",
    "Monsoon withdraws from the region", "Tech layoffs continue across startups",
    "City traffic advisory for the weekend", "New rules for digital payments explained",
]

SONGS = [
    "Midnight City", "Golden Hour", "Paper Planes", "Ocean Drive", "Neon Nights",
    "Lost in Tokyo", "Summer Rain", "Electric Feel", "Slow Motion", "Wildflower",
]

ARTISTS = ["The Wanderers", "Neon Fox", "Aria Lane", "Silver Coast", "Echo Park"]
