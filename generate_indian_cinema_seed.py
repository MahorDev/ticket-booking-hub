import urllib.request
import json
import random

# Complete list of 60 Indian Movies (10 Hindi, 10 Telugu, 10 Tamil, 10 Malayalam, 10 Kannada, 10 Multi-Language)
INDIAN_MOVIES = [
    # ----------------------------------------------------
    # HINDI (10)
    # ----------------------------------------------------
    {
        "id": 50,
        "title": "3 Idiots",
        "language": "Hindi",
        "genre": "Comedy",
        "duration": 170,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/d/df/3_idiots_poster.jpg",
        "description": "Two friends embark on a quest for a lost buddy while revisiting their college days and recalling the memories of their friend who inspired them to think differently."
    },
    {
        "id": 51,
        "title": "Dangal",
        "language": "Hindi",
        "genre": "Drama",
        "duration": 161,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/9/99/Dangal_Poster.jpg",
        "description": "Former wrestler Mahavir Singh Phogat trains his daughters Geeta and Babita to become India's first world-class female wrestlers, overcoming societal prejudices."
    },
    {
        "id": 52,
        "title": "Sholay",
        "language": "Hindi",
        "genre": "Action",
        "duration": 204,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/5/52/Sholay-poster.jpg",
        "description": "After his family is murdered by notorious bandit Gabbar Singh, a former police officer enlists the services of two small-time criminals to capture him."
    },
    {
        "id": 53,
        "title": "Dilwale Dulhania Le Jayenge",
        "language": "Hindi",
        "genre": "Romance",
        "duration": 189,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/8/80/Dilwale_Dulhania_Le_Jayenge_poster.jpg",
        "description": "Raj and Simran meet on a trip across Europe and fall in love. When Simran returns to India for an arranged marriage, Raj follows to win over her traditional family."
    },
    {
        "id": 54,
        "title": "Lagaan",
        "language": "Hindi",
        "genre": "Drama",
        "duration": 224,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/b/b6/Lagaan.jpg",
        "description": "In Victorian India, the residents of a drought-plagued village stake their future on a game of cricket against their ruthless British rulers to avoid exorbitant land taxes."
    },
    {
        "id": 55,
        "title": "Gangs of Wasseypur",
        "language": "Hindi",
        "genre": "Crime",
        "duration": 321,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/6/6a/Gangs_of_Wasseypur_poster.jpg",
        "description": "A clash between Sultan and Shahid Khan leads to the expulsion of Khan from Wasseypur, igniting a bloody three-generation gang war for political supremacy."
    },
    {
        "id": 56,
        "title": "Taare Zameen Par",
        "language": "Hindi",
        "genre": "Drama",
        "duration": 165,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/b/b4/Taare_Zameen_Par_Like_Stars_on_Earth_poster.png",
        "description": "An eight-year-old boy with dyslexia is thought to be a lazy troublemaker until an unconventional art teacher discovers the real problem behind his struggles."
    },
    {
        "id": 57,
        "title": "Zindagi Na Milegi Dobara",
        "language": "Hindi",
        "genre": "Drama",
        "duration": 155,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/3/3d/Zindaginamilegidobara.jpg",
        "description": "Three friends embark on a three-week bachelor road trip across Spain, where each chooses an extreme sport that challenges their deepest fears and heals old wounds."
    },
    {
        "id": 58,
        "title": "Swades",
        "language": "Hindi",
        "genre": "Drama",
        "duration": 210,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/8/85/Swades_poster.jpg",
        "description": "A successful Indian NASA scientist returns to an isolated village in India to find his childhood nanny, rediscovering his roots and transforming the community."
    },
    {
        "id": 59,
        "title": "Chak De! India",
        "language": "Hindi",
        "genre": "Drama",
        "duration": 153,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/0/0c/Chak_De%21_India.jpg",
        "description": "Kabir Khan, a former star hockey player accused of match-fixing, takes on the nearly impossible task of coaching the unranked Indian women's national hockey team to world victory."
    },

    # ----------------------------------------------------
    # TELUGU (10)
    # ----------------------------------------------------
    {
        "id": 60,
        "title": "Baahubali: The Beginning",
        "language": "Telugu",
        "genre": "Action",
        "duration": 159,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/5/5f/Baahubali_The_Beginning_poster.jpg",
        "description": "In ancient India, an adventurous and daring man becomes involved in a decades-old feud between two warring cousins over the kingdom of Mahishmati."
    },
    {
        "id": 61,
        "title": "Baahubali 2: The Conclusion",
        "language": "Telugu",
        "genre": "Action",
        "duration": 167,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/f/f9/Baahubali_the_Conclusion.jpg",
        "description": "When Shiva, the son of Bahubali, learns about his royal heritage, he sets out to find answers and exact revenge against Bhallaladeva to reclaim his rightful kingdom."
    },
    {
        "id": 62,
        "title": "RRR",
        "language": "Telugu",
        "genre": "Action",
        "duration": 187,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/d/d7/RRR_Poster.jpg",
        "description": "A fearless revolutionary and a legendary British officer forge an unbreakable friendship before discovering each other's true identities and taking down the British Raj."
    },
    {
        "id": 63,
        "title": "Pushpa: The Rise",
        "language": "Telugu",
        "genre": "Action",
        "duration": 179,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/7/75/Pushpa_-_The_Rise_%282021_film%29.jpg",
        "description": "A coolie rises through the ranks of a red sandalwood smuggling syndicate in the Seshachalam Hills of Andhra Pradesh, asserting his dominance over rivals."
    },
    {
        "id": 64,
        "title": "Magadheera",
        "language": "Telugu",
        "genre": "Action",
        "duration": 166,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/1/1a/Magadheera_poster.jpg",
        "description": "A bike stuntman learns that he is the reincarnation of a valiant 17th-century warrior who died protecting his beloved princess, and fights to save her once again."
    },
    {
        "id": 65,
        "title": "Eega",
        "language": "Telugu",
        "genre": "Fantasy",
        "duration": 145,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/b/b3/Eega_Poster.jpg",
        "description": "A murdered man is reincarnated as a common housefly and seeks revenge on his arrogant killer while protecting his girlfriend from danger."
    },
    {
        "id": 66,
        "title": "Rangasthalam",
        "language": "Telugu",
        "genre": "Action",
        "duration": 179,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/5/5d/Rangasthalam.jpg",
        "description": "Chitti Babu, a hearing-impaired boat driver in a rural 1980s village, wages war against an authoritarian village president when his brother's life is threatened."
    },
    {
        "id": 67,
        "title": "Arjun Reddy",
        "language": "Telugu",
        "genre": "Romance",
        "duration": 182,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/4/46/Arjun_Reddy_poster.jpg",
        "description": "A brilliant young orthopedic surgeon with severe anger issues descends into alcoholism and self-destruction after his girlfriend is forced to marry another man."
    },
    {
        "id": 68,
        "title": "Jersey",
        "language": "Telugu",
        "genre": "Drama",
        "duration": 160,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/0/0b/Jersey_2019_poster.jpg",
        "description": "A talented but failed cricketer in his late thirties decides to return to cricket against all odds to fulfill his young son's wish for a jersey as a gift."
    },
    {
        "id": 69,
        "title": "Sita Ramam",
        "language": "Telugu",
        "genre": "Romance",
        "duration": 163,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/e/eb/Sita_Ramam.jpg",
        "description": "An orphaned soldier, Lieutenant Ram, receives an anonymous letter from a woman named Sita Mahalakshmi. He sets out to find her and love blossoms amidst wartime conflict."
    },

    # ----------------------------------------------------
    # TAMIL (10)
    # ----------------------------------------------------
    {
        "id": 70,
        "title": "Vikram",
        "language": "Tamil",
        "genre": "Action",
        "duration": 175,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/9/93/Vikram_2022_poster.jpg",
        "description": "A black-ops squad led by a legendary former agent embarks on a high-stakes mission to dismantle a ruthless drug cartel while seeking vengeance."
    },
    {
        "id": 71,
        "title": "Leo",
        "language": "Tamil",
        "genre": "Action",
        "duration": 164,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/7/75/Leo_%282023_Indian_film%29.jpg",
        "description": "A mild-mannered cafe owner in Himachal Pradesh is pursued by vicious mobsters who suspect him of being a feared former gangster named Leo Das."
    },
    {
        "id": 72,
        "title": "Jailer",
        "language": "Tamil",
        "genre": "Action",
        "duration": 168,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/c/cb/Jailer_2023_Tamil_film_poster.jpg",
        "description": "A retired prison warden leads a quiet family life until his police officer son goes missing while investigating an antique idol smuggling syndicate."
    },
    {
        "id": 73,
        "title": "Kaithi",
        "language": "Tamil",
        "genre": "Action",
        "duration": 145,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/d/d6/Kaithi_poster.jpg",
        "description": "An ex-convict on his way to see his daughter for the first time is forced by a police officer to drive an infected squad of poisoned officers to safety through enemy territory."
    },
    {
        "id": 74,
        "title": "Ponniyin Selvan: Part 1",
        "language": "Tamil",
        "genre": "Action",
        "duration": 167,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/c/c3/Ponniyin_Selvan_I.jpg",
        "description": "Vandiyathevan sets out on a covert journey across the Chola kingdom to deliver a message from Crown Prince Aditha Karikalan amidst a brewing conspiracy."
    },
    {
        "id": 75,
        "title": "Asuran",
        "language": "Tamil",
        "genre": "Action",
        "duration": 141,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/2/29/Asuran_poster.jpg",
        "description": "A farmer from a lower caste goes to extraordinary lengths to protect his hot-headed teenage son after the boy kills an oppressive upper-caste landlord in self-defense."
    },
    {
        "id": 76,
        "title": "Soorarai Pottru",
        "language": "Tamil",
        "genre": "Drama",
        "duration": 153,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/a/ab/Soorarai_Pottru_poster.jpg",
        "description": "Nedumaaran Rajangam, a former Indian Air Force captain, dreams of launching a low-cost airline so that every common man in India can afford to fly."
    },
    {
        "id": 77,
        "title": "Jai Bhim",
        "language": "Tamil",
        "genre": "Drama",
        "duration": 164,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/8/8c/Jai_Bhim_poster.jpg",
        "description": "When a poor tribal man is falsely accused and disappears from police custody, a courageous human rights lawyer takes up the legal battle against systemic injustice."
    },
    {
        "id": 78,
        "title": "Super Deluxe",
        "language": "Tamil",
        "genre": "Drama",
        "duration": 176,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/4/41/Super_Deluxe_poster.jpg",
        "description": "An unfaithful wife, an estranged father, a corrupt cop, and a group of curious teenagers find themselves in unexpected predicaments on one fateful day."
    },
    {
        "id": 79,
        "title": "96",
        "language": "Tamil",
        "genre": "Romance",
        "duration": 158,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/9/96/96_film_poster.jpg",
        "description": "Two high school sweethearts meet at a reunion after twenty-two years and spend an emotionally charged night reminiscing about their nostalgic past."
    },

    # ----------------------------------------------------
    # MALAYALAM (10)
    # ----------------------------------------------------
    {
        "id": 80,
        "title": "Manjummel Boys",
        "language": "Malayalam",
        "genre": "Thriller",
        "duration": 135,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/0/00/Manjummel_Boys_poster.jpg",
        "description": "A group of close-knit friends from a small town in Kerala embark on a vacation to Kodaikanal, where a fun adventure turns into a desperate rescue mission in Guna Caves."
    },
    {
        "id": 81,
        "title": "Aavesham",
        "language": "Malayalam",
        "genre": "Comedy",
        "duration": 158,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/d/d1/Aavesham.jpg",
        "description": "Three college students in Bangalore befriend an eccentric local gangster named Ranga to get back at their college bullies, but find themselves entangled in madness."
    },
    {
        "id": 82,
        "title": "Drishyam",
        "language": "Malayalam",
        "genre": "Thriller",
        "duration": 160,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/4/4e/Drishyam_poster.jpg",
        "description": "A simple family man uses his sharp wits and knowledge of cinema to fabricate an unbreakable alibi when his family commits an accidental murder to defend their home."
    },
    {
        "id": 83,
        "title": "Premam",
        "language": "Malayalam",
        "genre": "Romance",
        "duration": 156,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/d/df/Premam_film_poster.jpg",
        "description": "A young man experiences love at three different stages of his life—school, college, and adulthood—discovering growth, heartbreak, and unexpected happiness."
    },
    {
        "id": 84,
        "title": "Kumbalangi Nights",
        "language": "Malayalam",
        "genre": "Drama",
        "duration": 135,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/c/cb/Kumbalangi_Nights_poster.jpg",
        "description": "Four brothers share a dysfunctional relationship in a small island village in Kochi. Their animosity turns into harmony when a series of crises unites them."
    },
    {
        "id": 85,
        "title": "Lucifer",
        "language": "Malayalam",
        "genre": "Action",
        "duration": 175,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/c/c2/Lucifer_film_poster.jpg",
        "description": "A political godfather dies and a battle for succession ensues. An enigmatic man named Stephen Nedumpally emerges from the shadows to guard the legacy."
    },
    {
        "id": 86,
        "title": "Bramayugam",
        "language": "Malayalam",
        "genre": "Horror",
        "duration": 139,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/4/45/Bramayugam_poster.jpg",
        "description": "In 17th-century Malabar, a fugitive folk singer escapes slavery and seeks refuge in an enigmatic, decaying mansion owned by a sinister aristocratic master."
    },
    {
        "id": 87,
        "title": "Minnal Murali",
        "language": "Malayalam",
        "genre": "Comedy",
        "duration": 158,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/9/99/Minnal_Murali_poster.jpg",
        "description": "A tailor gains superhuman speed and power after being struck by lightning, and must protect his village when an unexpected nemesis emerges with identical powers."
    },
    {
        "id": 88,
        "title": "Bangalore Days",
        "language": "Malayalam",
        "genre": "Drama",
        "duration": 171,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/7/77/Bangalore_Days_poster.jpg",
        "description": "Three close cousins move to Bangalore to pursue their individual dreams, experiencing romance, modern life struggles, and rediscovering family bonds."
    },
    {
        "id": 89,
        "title": "Premalu",
        "language": "Malayalam",
        "genre": "Romance",
        "duration": 156,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/4/4e/Premalu_poster.jpg",
        "description": "A carefree young man moves to Hyderabad for GATE coaching and falls head over heels for a vivacious IT employee, sparking a delightful series of comic misunderstandings."
    },

    # ----------------------------------------------------
    # KANNADA (10)
    # ----------------------------------------------------
    {
        "id": 90,
        "title": "KGF: Chapter 1",
        "language": "Kannada",
        "genre": "Action",
        "duration": 156,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/c/cc/K.G.F_Chapter_1_poster.jpg",
        "description": "Rocky, an ambitious young boy from the slums of Bombay, grows up to become a fearsome mercenary who travels undercover to the Kolar Gold Fields to liberate enslaved miners."
    },
    {
        "id": 91,
        "title": "KGF: Chapter 2",
        "language": "Kannada",
        "genre": "Action",
        "duration": 168,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/d/d0/K.G.F_Chapter_2.jpg",
        "description": "The blood-soaked land of Kolar Gold Fields now has a new overlord: Rocky. As his empire expands, he faces deadly opposition from Adheera and the Indian government."
    },
    {
        "id": 92,
        "title": "Kantara",
        "language": "Kannada",
        "genre": "Action",
        "duration": 148,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/8/84/Kantara_poster.jpeg",
        "description": "Set in a coastal Karnataka village, a hot-blooded Kambala champion battles a steadfast forest officer to defend the sacred traditions and ancestral forest lands."
    },
    {
        "id": 93,
        "title": "777 Charlie",
        "language": "Kannada",
        "genre": "Adventure",
        "duration": 164,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/b/bd/777_Charlie_poster.jpg",
        "description": "Dharma, a lonely and reclusive factory worker stuck in a monotonous life, finds a renewed purpose and unconditional love when an adventurous Labrador pup wanders into his life."
    },
    {
        "id": 94,
        "title": "Ugramm",
        "language": "Kannada",
        "genre": "Action",
        "duration": 145,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/d/da/Ugramm_poster.jpg",
        "description": "A man with a dangerous and violent past strives to live peacefully as a mechanic, until he is forced to unsheathe his wrath to protect a young woman from ruthless mafiosos."
    },
    {
        "id": 95,
        "title": "Lucia",
        "language": "Kannada",
        "genre": "Sci-Fi",
        "duration": 135,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/4/4e/Lucia_Poster.jpg",
        "description": "An usher suffering from severe insomnia takes a mysterious dream-altering pill that allows him to experience the lavish life of a celebrity in his dreams, blurring reality."
    },
    {
        "id": 96,
        "title": "Ulidavaru Kandanthe",
        "language": "Kannada",
        "genre": "Crime",
        "duration": 154,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/0/02/Ulidavaru_Kandante_poster.jpg",
        "description": "A journalist pieces together five different perspectives from witnesses involved in an incident surrounding an antique idol during the festival of Krishna Janmashtami."
    },
    {
        "id": 97,
        "title": "Garuda Gamana Vrishabha Vahana",
        "language": "Kannada",
        "genre": "Crime",
        "duration": 151,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/d/d7/Garuda_Gamana_Vrishabha_Vahana.jpg",
        "description": "Two childhood friends, Shiva and Hari, rise together to rule the underworld of coastal Mangalore, but their contrasting personalities soon ignite a fierce power struggle."
    },
    {
        "id": 98,
        "title": "Kirik Party",
        "language": "Kannada",
        "genre": "Comedy",
        "duration": 165,
        "rating": "U",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/e/eb/Kirik_Party_poster.jpg",
        "description": "Karna and his mischievous gang of engineering college friends experience the highs and lows of youth, campus politics, heartfelt romance, and unexpected tragedy."
    },
    {
        "id": 99,
        "title": "Kavaludaari",
        "language": "Kannada",
        "genre": "Mystery",
        "duration": 148,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/7/77/Kavaludaari_poster.jpg",
        "description": "A bored traffic cop stumbles upon three human skulls unearthed at a metro construction site and secretly teams up with a retired detective to solve a forty-year-old cold case."
    },

    # ----------------------------------------------------
    # MULTI-LANGUAGE / PAN-INDIA HITS (10)
    # ----------------------------------------------------
    {
        "id": 100,
        "title": "Kalki 2898 AD",
        "language": "Telugu, Hindi, Tamil",
        "genre": "Sci-Fi",
        "duration": 181,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/4/4c/Kalki_2898_AD.jpg",
        "description": "In a post-apocalyptic world ruled by Supreme Yaskin in the year 2898 AD, the immortal warrior Ashwatthama awakens to protect the unborn avatar of Vishnu from bounty hunters."
    },
    {
        "id": 101,
        "title": "Salaar: Part 1 - Ceasefire",
        "language": "Telugu, Hindi, Kannada, Tamil",
        "genre": "Action",
        "duration": 175,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/a/a6/Salaar_Part_1_%E2%80%93_Ceasefire.jpg",
        "description": "In the violent sovereign city-state of Khansaar, childhood friends Deva and Varadha find their unbreakable bond tested as they fight for survival and ultimate supremacy."
    },
    {
        "id": 102,
        "title": "Jawan",
        "language": "Hindi, Tamil, Telugu",
        "genre": "Action",
        "duration": 169,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/3/39/Jawan_film_poster.jpg",
        "description": "A jailer of a women's prison leads a dedicated commando team to execute Robin Hood-style heists that expose government corruption and exact justice on corrupt oligarchs."
    },
    {
        "id": 103,
        "title": "Animal",
        "language": "Hindi, Telugu, Tamil",
        "genre": "Action",
        "duration": 201,
        "rating": "A",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/9/90/Animal_%282023_film%29_poster.jpg",
        "description": "Ranvijay, the fiercely obsessive son of India's richest industrialist, returns from exile to wage a merciless, bloody war against the enemies who attempted to assassinate his father."
    },
    {
        "id": 104,
        "title": "Brahmāstra: Part One – Shiva",
        "language": "Hindi, Telugu, Tamil",
        "genre": "Fantasy",
        "duration": 167,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/4/40/Brahm%C4%81stra_Part_One_Shiva.jpg",
        "description": "Shiva, a Mumbai DJ with a supernatural connection to fire, discovers that he is part of the ancient Brahmānsh order and must unlock the divine weapon Brahmāstra."
    },
    {
        "id": 105,
        "title": "Pathaan",
        "language": "Hindi, Tamil, Telugu",
        "genre": "Action",
        "duration": 146,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/c/c3/Pathaan_film_poster.jpg",
        "description": "An exiled Indian RAW agent must team up with a fellow spy to stop Jim, a rogue ex-agent leading private terror outfit Outfit X, from unleashing a devastating biological weapon."
    },
    {
        "id": 106,
        "title": "Stree 2",
        "language": "Hindi, Telugu",
        "genre": "Comedy",
        "duration": 147,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/7/7b/Stree_2.jpg",
        "description": "The town of Chanderi is haunted once more, this time by a terrifying headless demon named Sarkata who abducts modern, progressive women, forcing Vicky and the gang to team up with Stree."
    },
    {
        "id": 107,
        "title": "Hanu-Man",
        "language": "Telugu, Hindi, Tamil",
        "genre": "Fantasy",
        "duration": 158,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/6/65/Hanu_Man_film_poster.jpg",
        "description": "An unassuming young man in the village of Anjanadri gains the cosmic powers of Lord Hanuman via an ancient gemstone, and rises to protect his people from a supervillain."
    },
    {
        "id": 108,
        "title": "Devara: Part 1",
        "language": "Telugu, Hindi, Tamil, Malayalam",
        "genre": "Action",
        "duration": 178,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/c/ce/Devara_Part_1.jpg",
        "description": "In a rugged coastal village, a fearless chieftain named Devara rebels against the smuggling trade, instilling terror in wrongdoers while his timid son struggles to live in his shadow."
    },
    {
        "id": 109,
        "title": "2.0",
        "language": "Tamil, Hindi, Telugu",
        "genre": "Sci-Fi",
        "duration": 147,
        "rating": "UA",
        "poster_url": "https://upload.wikimedia.org/wikipedia/en/c/cf/2.0_film_poster.jpg",
        "description": "When cell phones suddenly fly out of people's hands and swarm into a destructive avian monster across the city, scientist Vaseegaran reassembles his humanoid robot Chitti to battle the threat."
    }
]

def escape_sql(text):
    return text.replace("'", "''")

def build_sql():
    sql = []
    sql.append("-- ========================================================")
    sql.append("-- TICKETHUB INDIAN CINEMA SEED DATA (60 FAMOUS MOVIES)")
    sql.append("-- ========================================================")
    sql.append("USE tickethub_event_db;")
    sql.append("SET FOREIGN_KEY_CHECKS = 0;")

    # Remove previously seeded range for Indian movies (50 to 109)
    sql.append("DELETE FROM show_seats WHERE show_id >= 300;")
    sql.append("DELETE FROM shows WHERE show_id >= 300;")
    sql.append("DELETE FROM events WHERE event_id >= 50 AND event_id <= 109;")
    sql.append("SET FOREIGN_KEY_CHECKS = 1;")
    sql.append("")

    # 1. Insert 60 Movies
    sql.append("-- 1. Insert 60 Famous Indian Movies")
    for m in INDIAN_MOVIES:
        sql.append(
            f"INSERT INTO events (event_id, title, description, event_type, language, duration, rating, genre, status, poster_url) VALUES "
            f"({m['id']}, '{escape_sql(m['title'])}', '{escape_sql(m['description'])}', 'MOVIE', "
            f"'{escape_sql(m['language'])}', {m['duration']}, '{m['rating']}', '{m['genre']}', 'PUBLISHED', "
            f"'{escape_sql(m['poster_url'])}');"
        )
    sql.append("")

    # 2. Get available screens (10 to 27) and schedule 5-6 shows per movie
    # 18 screens exist across 6 venues:
    screens = [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27]
    dates = ["2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20", "2026-10-21"]
    showtimes = [
        ("09:45:00", "12:30:00", 250.00),
        ("13:15:00", "16:00:00", 350.00),
        ("16:45:00", "19:30:00", 400.00),
        ("20:15:00", "23:00:00", 480.00),
        ("22:45:00", "01:30:00", 350.00),
        ("11:00:00", "13:45:00", 300.00)
    ]

    sql.append("-- 2. Insert Shows Schedule (5 to 6 shows per movie)")
    shows_list = []
    show_id_seq = 300
    random.seed(101)

    for idx, m in enumerate(INDIAN_MOVIES):
        e_id = m["id"]
        num_shows = 6 if idx % 2 == 0 else 5
        for s_idx in range(num_shows):
            this_show_id = show_id_seq
            show_date = dates[(idx + s_idx) % len(dates)]
            st_time, end_time, base_price = showtimes[(idx + s_idx) % len(showtimes)]
            chosen_screen_id = screens[(idx * 2 + s_idx) % len(screens)]

            shows_list.append((this_show_id, e_id, chosen_screen_id, show_date, st_time, end_time, base_price))
            sql.append(
                f"INSERT INTO shows (show_id, event_id, screen_id, show_date, start_time, end_time, ticket_price, currency, status) VALUES "
                f"({this_show_id}, {e_id}, {chosen_screen_id}, '{show_date}', '{st_time}', '{end_time}', {base_price:.2f}, 'INR', 'OPEN');"
            )
            show_id_seq += 1

    sql.append("")

    # 3. Insert ShowSeats from existing seats in MySQL for each screen
    # We will write a SQL block that joins existing seats for the screen and inserts into show_seats:
    sql.append("-- 3. Generate Show Seats automatically using existing screen seats")
    for sh_id, e_id, scr_id, sdate, st, et, bprice in shows_list:
        # Insert show_seats for this show from seats table
        # 85% AVAILABLE, 10% BOOKED, 5% BLOCKED
        sql.append(
            f"INSERT INTO show_seats (show_id, seat_id, price, status) "
            f"SELECT {sh_id}, s.seat_id, COALESCE(s.base_price, {bprice:.2f}), "
            f"CASE "
            f"  WHEN (s.seat_id % 10) = 0 THEN 'BOOKED' "
            f"  WHEN (s.seat_id % 20) = 5 THEN 'BLOCKED' "
            f"  ELSE 'AVAILABLE' "
            f"END "
            f"FROM seats s WHERE s.screen_id = {scr_id};"
        )

    sql.append("")
    sql.append("-- DONE")
    return "\n".join(sql), len(INDIAN_MOVIES), len(shows_list)

if __name__ == "__main__":
    sql_text, num_movies, num_shows = build_sql()
    out_file = "/Users/jaikishan/Downloads/TicketHub_Phase1_Implemented/ticketHub/indian_cinema_seed.sql"
    with open(out_file, "w", encoding="utf-8") as f:
        f.write(sql_text)
    print(f"Generated indian_cinema_seed.sql:")
    print(f"  - Total Indian Movies: {num_movies}")
    print(f"  - Total New Shows: {num_shows}")
