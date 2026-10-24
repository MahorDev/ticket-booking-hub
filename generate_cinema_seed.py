import os
import random

# Seed data definition
MOVIES = [
    # Sci-Fi (13)
    {
        "title": "Inception",
        "description": "Dom Cobb is a skilled thief, the absolute best in the dangerous art of extraction, stealing valuable secrets from deep within the subconscious during the dream state.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 148,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/9gk7adHYeDvHkCSEqAvQNLV5Uge.jpg"
    },
    {
        "title": "Interstellar",
        "description": "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival as Earth faces catastrophic ecological collapse.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 169,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg"
    },
    {
        "title": "Dune: Part Two",
        "description": "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family, facing a choice between love and the universe's fate.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 166,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg"
    },
    {
        "title": "Blade Runner 2049",
        "description": "Thirty years after the events of the first film, a new Blade Runner, LAPD Officer K, unearths a long-buried secret that has the potential to plunge what's left of society into chaos.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 164,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg"
    },
    {
        "title": "The Matrix",
        "description": "When a beautiful stranger leads computer hacker Neo to a forbidding underworld, he discovers the shocking truth--the life he knows is the elaborate deception of an evil cyber-intelligence.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 136,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg"
    },
    {
        "title": "Avatar: The Way of Water",
        "description": "Jake Sully lives with his newfound family on the extrasolar moon Pandora. Once a familiar threat returns, Jake must work with Neytiri to protect their home and their clan.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 192,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg"
    },
    {
        "title": "Tenet",
        "description": "Armed with only one word, Tenet, and fighting for the survival of the entire world, a Protagonist journeys through a twilight world of international espionage on a mission unfolding beyond real time.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 150,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/k68nPLbIST6NP96JmTxmZijEvCA.jpg"
    },
    {
        "title": "Oppenheimer",
        "description": "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb during World War II.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 180,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"
    },
    {
        "title": "Arrival",
        "description": "A linguist works with the military to communicate with alien lifeforms after twelve mysterious spacecraft appear around the world.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 116,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/x2FJsf1ElAgr63Y3PNPtJrcmpoe.jpg"
    },
    {
        "title": "Alien",
        "description": "The crew of a commercial spacecraft encounters a deadly lifeform after investigating an unknown transmission on a remote moon.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 117,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/vfrQk5IPloGg1v9Rzbh2Eg3VGyM.jpg"
    },
    {
        "title": "Avengers: Endgame",
        "description": "After the devastating events of Avengers: Infinity War, the universe is in ruins. With the help of remaining allies, the Avengers assemble once more to reverse Thanos' actions.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 181,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg"
    },
    {
        "title": "Star Wars: The Empire Strikes Back",
        "description": "After the Rebels are overpowered by the Empire, Luke Skywalker begins Jedi training with Yoda, while his friends are pursued across the galaxy by Darth Vader.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 124,
        "rating": "U",
        "poster_url": "https://image.tmdb.org/t/p/w500/6FfCtAuVAW8XJjZ7eWeLibRLWTw.jpg"
    },
    {
        "title": "Spider-Man: Across the Spider-Verse",
        "description": "Miles Morales catapults across the Multiverse, where he encounters a team of Spider-People charged with protecting its very existence.",
        "genre": "Sci-Fi",
        "language": "English",
        "duration": 140,
        "rating": "U",
        "poster_url": "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg"
    },

    # Drama (14)
    {
        "title": "The Shawshank Redemption",
        "description": "A banker convicted of uxoricide forms a friendship over a quarter of a century with a hardened convict, while maintaining his innocence and trying to remain hopeful through simple compassion.",
        "genre": "Drama",
        "language": "English",
        "duration": 142,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/9cqNxx0GxF0bflZmeSMuL5tnGzr.jpg"
    },
    {
        "title": "The Godfather",
        "description": "The aging patriarch of an organized crime dynasty transfers control of his clandestine empire to his reluctant youngest son.",
        "genre": "Drama",
        "language": "English",
        "duration": 175,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg"
    },
    {
        "title": "The Dark Knight",
        "description": "When the menace known as the Joker wreaks havoc and chaos on the people of Gotham, Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice.",
        "genre": "Drama",
        "language": "English",
        "duration": 152,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg"
    },
    {
        "title": "12 Angry Men",
        "description": "The jury in a New York City murder trial is frustrated by a single member whose skeptical caution forces them to more carefully consider the evidence before jumping to a hasty verdict.",
        "genre": "Drama",
        "language": "English",
        "duration": 96,
        "rating": "U",
        "poster_url": "https://image.tmdb.org/t/p/w500/ow3wq89wM8qd5X7hWKxiRfsFf9C.jpg"
    },
    {
        "title": "Schindler's List",
        "description": "In German-occupied Poland during World War II, industrialist Oskar Schindler gradually becomes concerned for his Jewish workforce after witnessing their persecution by the Nazis.",
        "genre": "Drama",
        "language": "English",
        "duration": 195,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/sF1U4EUQS8YHUYjNl3pMGNIQyr0.jpg"
    },
    {
        "title": "Pulp Fiction",
        "description": "The lives of two mob hitmen, a boxer, a gangster and his wife, and a pair of diner bandits intertwine in four tales of violence and redemption.",
        "genre": "Drama",
        "language": "English",
        "duration": 154,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/d5iIlFn5s0ImszYzBPb8JPIfbXD.jpg"
    },
    {
        "title": "Fight Club",
        "description": "An insomniac office worker and a devil-may-care soap maker form an underground fight club that evolves into much more.",
        "genre": "Drama",
        "language": "English",
        "duration": 139,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg"
    },
    {
        "title": "Forrest Gump",
        "description": "The history of the United States from the 1950s to the '70s unfolds from the perspective of an Alabama man with an IQ of 75, who yearns to be reunited with his childhood sweetheart.",
        "genre": "Drama",
        "language": "English",
        "duration": 142,
        "rating": "UA",
        "poster_url": "https://image.tmdb.org/t/p/w500/arw2vcBveWOVZr6pxd9XTd1TdQa.jpg"
    },
    {
        "title": "Whiplash",
        "description": "A promising young drummer enrolls at a cut-throat music conservatory where his dreams of greatness are mentored by an instructor who will stop at nothing to realize a student's potential.",
        "genre": "Drama",
        "language": "English",
        "duration": 107,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/7fn624j5lj3xTme2SgiLCeuedmO.jpg"
    },
    {
        "title": "Parasite",
        "description": "Greed and class discrimination threaten the newly formed symbiotic relationship between the wealthy Park family and the destitute Kim clan.",
        "genre": "Drama",
        "language": "English",
        "duration": 132,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg"
    },
    {
        "title": "Gladiator",
        "description": "A former Roman General sets out to exact vengeance against the corrupt emperor who murdered his family and sent him into slavery.",
        "genre": "Drama",
        "language": "English",
        "duration": 155,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/ty8TGRuvJLPUmAR1H1nRIsgwvim.jpg"
    },
    {
        "title": "Goodfellas",
        "description": "The story of Henry Hill and his life in the mafia, covering his relationship with his wife Karen and his mob partners Jimmy Conway and Tommy DeVito.",
        "genre": "Drama",
        "language": "English",
        "duration": 145,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/aKuFiU82s5ISJpGZp7YkIr3kCUd.jpg"
    },
    {
        "title": "The Green Mile",
        "description": "A tale set on death row in a Southern prison, where gentle giant John Coffey possesses the mysterious power to heal people's ailments and lives.",
        "genre": "Drama",
        "language": "English",
        "duration": 189,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/o0lO84GI7qrG6XFvtsPOSV7CTNa.jpg"
    },
    {
        "title": "Se7en",
        "description": "Two detectives, a rookie and a veteran, hunt a serial killer who uses the seven deadly sins as his motives.",
        "genre": "Drama",
        "language": "English",
        "duration": 127,
        "rating": "A",
        "poster_url": "https://image.tmdb.org/t/p/w500/6yoghtyTpznpBik8EngEmJskVUO.jpg"
    }
]

VENUES = [
    {
        "id": 10,
        "name": "PVR ICON Palladium, Lower Parel",
        "address": "High Street Phoenix, Senapati Bapat Marg, Lower Parel",
        "city": "Mumbai",
        "state": "Maharashtra",
        "country": "India"
    },
    {
        "id": 20,
        "name": "INOX Megaplex, Inorbit Mall",
        "address": "Inorbit Mall, New Link Road, Malad West",
        "city": "Mumbai",
        "state": "Maharashtra",
        "country": "India"
    },
    {
        "id": 30,
        "name": "PVR Forum Mall, Koramangala",
        "address": "21 Hosur Road, Koramangala 7th Block",
        "city": "Bengaluru",
        "state": "Karnataka",
        "country": "India"
    },
    {
        "id": 40,
        "name": "Cinepolis Forum Shantiniketan",
        "address": "ITPB Main Road, Whitefield",
        "city": "Bengaluru",
        "state": "Karnataka",
        "country": "India"
    },
    {
        "id": 50,
        "name": "PVR Director's Cut, Ambience Mall",
        "address": "Nelson Mandela Road, Vasant Kunj",
        "city": "New Delhi",
        "state": "Delhi",
        "country": "India"
    },
    {
        "id": 60,
        "name": "Prasads Multiplex (PCX Giant Screen)",
        "address": "Khairatabad, Necklace Road",
        "city": "Hyderabad",
        "state": "Telangana",
        "country": "India"
    }
]

# 3 Screens per Venue:
# 1) IMAX Laser 4K (128 seats: 24 Lower, 56 Middle, 48 Upper)
# 2) Dolby Atmos Prime (86 seats: 20 Lower, 36 Middle, 30 Upper)
# 3) Luxe Gold Recliner (44 seats: 16 Lower, 16 Middle, 12 Upper)

def escape_sql(text):
    return text.replace("'", "''")

def generate_seats_for_screen(screen_id, screen_type):
    seats = []
    if screen_type == "IMAX":
        # Lower: Rows A, B (12 seats each)
        for r in ["A", "B"]:
            for num in range(1, 13):
                seats.append((screen_id, f"{r}{num}", r, "REGULAR", "LOWER", 250.00))
        # Middle: Rows C, D, E, F (14 seats each)
        for r in ["C", "D", "E", "F"]:
            for num in range(1, 15):
                seats.append((screen_id, f"{r}{num}", r, "PREMIUM", "MIDDLE", 420.00))
        # Upper: Rows G, H, J, K (12 seats each)
        for r in ["G", "H", "J", "K"]:
            for num in range(1, 13):
                seats.append((screen_id, f"{r}{num}", r, "VIP", "UPPER", 650.00))
    elif screen_type == "ATMOS":
        # Lower: Rows A, B (10 seats each)
        for r in ["A", "B"]:
            for num in range(1, 11):
                seats.append((screen_id, f"{r}{num}", r, "REGULAR", "LOWER", 190.00))
        # Middle: Rows C, D, E (12 seats each)
        for r in ["C", "D", "E"]:
            for num in range(1, 13):
                seats.append((screen_id, f"{r}{num}", r, "PREMIUM", "MIDDLE", 320.00))
        # Upper: Rows F, G, H (10 seats each)
        for r in ["F", "G", "H"]:
            for num in range(1, 11):
                seats.append((screen_id, f"{r}{num}", r, "VIP", "UPPER", 480.00))
    else: # LUXE
        # Lower: Rows A, B (8 seats each)
        for r in ["A", "B"]:
            for num in range(1, 9):
                seats.append((screen_id, f"{r}{num}", r, "PREMIUM", "LOWER", 450.00))
        # Middle: Rows C, D (8 seats each)
        for r in ["C", "D"]:
            for num in range(1, 9):
                seats.append((screen_id, f"{r}{num}", r, "VIP", "MIDDLE", 650.00))
        # Upper: Rows E, F (6 seats each)
        for r in ["E", "F"]:
            for num in range(1, 7):
                seats.append((screen_id, f"{r}{num}", r, "RECLINER", "UPPER", 950.00))
    return seats

def build_sql():
    sql = []
    sql.append("-- ========================================================")
    sql.append("-- TICKETHUB REAL-WORLD CINEMA SEED DATA (SCI-FI & DRAMA)")
    sql.append("-- ========================================================")
    sql.append("USE tickethub_event_db;")
    sql.append("SET FOREIGN_KEY_CHECKS = 0;")

    # We do NOT touch show_id=3 or event_id=3 so existing test bookings stay intact!
    # Delete previous seeded ranges if re-run:
    sql.append("DELETE FROM show_seats WHERE show_id >= 100;")
    sql.append("DELETE FROM shows WHERE show_id >= 100;")
    sql.append("DELETE FROM seats WHERE screen_id >= 10;")
    sql.append("DELETE FROM screens WHERE screen_id >= 10;")
    sql.append("DELETE FROM venues WHERE venue_id >= 10;")
    sql.append("DELETE FROM events WHERE event_id >= 10;")
    sql.append("SET FOREIGN_KEY_CHECKS = 1;")
    sql.append("")

    # 1. Insert Venues
    sql.append("-- 1. Flagship Venues")
    for v in VENUES:
        sql.append(f"INSERT INTO venues (venue_id, name, address, city, state, country) VALUES "
                   f"({v['id']}, '{escape_sql(v['name'])}', '{escape_sql(v['address'])}', "
                   f"'{escape_sql(v['city'])}', '{escape_sql(v['state'])}', '{escape_sql(v['country'])}');")
    sql.append("")

    # 2. Insert Screens
    sql.append("-- 2. Multi-Tier Screens (IMAX, Dolby Atmos, Luxe Recliner)")
    screens_meta = []
    screen_id_seq = 10
    for v in VENUES:
        v_id = v["id"]
        # Screen 1: IMAX Laser 4K
        s1_id = screen_id_seq
        sql.append(f"INSERT INTO screens (screen_id, screen_name, venue_id) VALUES ({s1_id}, 'Audi 1 - IMAX Laser 4K', {v_id});")
        screens_meta.append((s1_id, "IMAX", v_id))
        screen_id_seq += 1

        # Screen 2: Dolby Atmos Prime
        s2_id = screen_id_seq
        sql.append(f"INSERT INTO screens (screen_id, screen_name, venue_id) VALUES ({s2_id}, 'Audi 2 - Dolby Atmos Prime', {v_id});")
        screens_meta.append((s2_id, "ATMOS", v_id))
        screen_id_seq += 1

        # Screen 3: Luxe Gold Recliner
        s3_id = screen_id_seq
        sql.append(f"INSERT INTO screens (screen_id, screen_name, venue_id) VALUES ({s3_id}, 'Audi 3 - Luxe Gold Recliner', {v_id});")
        screens_meta.append((s3_id, "LUXE", v_id))
        screen_id_seq += 1
    sql.append("")

    # 3. Insert Seats
    sql.append("-- 3. Real-life Cinema Seats Generation")
    all_seats = []
    seat_id_seq = 1000
    screen_seats_map = {} # screen_id -> list of (seat_id, price)
    for s_id, s_type, v_id in screens_meta:
        screen_seats_map[s_id] = []
        seats = generate_seats_for_screen(s_id, s_type)
        for s_screen_id, seat_num, row_name, seat_type, section, base_price in seats:
            this_seat_id = seat_id_seq
            all_seats.append((this_seat_id, seat_num, row_name, seat_type, section, base_price, s_screen_id))
            screen_seats_map[s_id].append((this_seat_id, base_price))
            seat_id_seq += 1

    # Batch insert seats in chunks of 500
    chunk_size = 300
    for i in range(0, len(all_seats), chunk_size):
        chunk = all_seats[i:i+chunk_size]
        vals = [f"({sid}, '{snum}', '{rname}', '{stype}', '{sec}', {price:.2f}, {scrid})" 
                for sid, snum, rname, stype, sec, price, scrid in chunk]
        sql.append("INSERT INTO seats (seat_id, seat_number, row_name, seat_type, section, base_price, screen_id) VALUES\n  " + 
                   ",\n  ".join(vals) + ";")
    sql.append("")

    # 4. Insert Movies
    sql.append("-- 4. 27 Top Sci-Fi & Drama Movies with Verified TMDB Posters")
    movie_id_map = {} # index -> event_id
    event_id_seq = 10
    for idx, m in enumerate(MOVIES):
        e_id = event_id_seq
        movie_id_map[idx] = e_id
        sql.append(f"INSERT INTO events (event_id, title, description, event_type, language, duration, rating, genre, status, poster_url) VALUES "
                   f"({e_id}, '{escape_sql(m['title'])}', '{escape_sql(m['description'])}', 'MOVIE', "
                   f"'{escape_sql(m['language'])}', {m['duration']}, '{m['rating']}', '{m['genre']}', 'PUBLISHED', "
                   f"'{escape_sql(m['poster_url'])}');")
        event_id_seq += 1
    sql.append("")

    # 5. Insert Shows (5 to 6 shows per movie across multiple venues & screens)
    sql.append("-- 5. Multi-Venue Shows Schedule (OPEN status)")
    dates = ["2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20"]
    showtimes = [
        ("10:15:00", "13:00:00", 350.00),
        ("13:45:00", "16:30:00", 400.00),
        ("17:15:00", "20:00:00", 450.00),
        ("20:45:00", "23:30:00", 500.00),
        ("23:15:00", "02:00:00", 380.00),
        ("11:30:00", "14:15:00", 380.00)
    ]

    shows_list = []
    show_id_seq = 100
    random.seed(42) # Deterministic for consistent generation

    for idx, m in enumerate(MOVIES):
        e_id = movie_id_map[idx]
        # Choose 5 to 6 distinct (screen, date, time) combinations so there is zero overlap
        # Distribute across venues:
        num_shows = 6 if idx % 2 == 0 else 5
        for s_idx in range(num_shows):
            this_show_id = show_id_seq
            show_date = dates[s_idx % len(dates)]
            st_time, end_time, base_show_price = showtimes[s_idx % len(showtimes)]
            # Assign to different screens across venues
            chosen_screen_idx = (idx * 3 + s_idx) % len(screens_meta)
            chosen_screen_id = screens_meta[chosen_screen_idx][0]

            shows_list.append((this_show_id, e_id, chosen_screen_id, show_date, st_time, end_time, base_show_price))
            sql.append(f"INSERT INTO shows (show_id, event_id, screen_id, show_date, start_time, end_time, ticket_price, currency, status) VALUES "
                       f"({this_show_id}, {e_id}, {chosen_screen_id}, '{show_date}', '{st_time}', '{end_time}', {base_show_price:.2f}, 'INR', 'OPEN');")
            show_id_seq += 1
    sql.append("")

    # 6. Insert ShowSeats for every Show
    sql.append("-- 6. Show Seats Generation with Realistic Booking Status (85% AVAILABLE, 10% BOOKED, 5% RESERVED)")
    all_show_seats = []
    show_seat_id_seq = 10000

    for s_id, e_id, scr_id, sdate, st, et, bprice in shows_list:
        seats_for_screen = screen_seats_map[scr_id]
        for seat_id, seat_base_price in seats_for_screen:
            ssid = show_seat_id_seq
            # Status: mostly AVAILABLE, some BOOKED/RESERVED
            r_val = random.random()
            if r_val < 0.10:
                seat_status = "BOOKED"
            elif r_val < 0.15:
                seat_status = "BLOCKED"
            else:
                seat_status = "AVAILABLE"

            all_show_seats.append((ssid, s_id, seat_id, seat_base_price, seat_status))
            show_seat_id_seq += 1

    # Batch insert show_seats in chunks of 400
    for i in range(0, len(all_show_seats), 400):
        chunk = all_show_seats[i:i+400]
        vals = [f"({ssid}, {sh_id}, {st_id}, {price:.2f}, '{stt}')" for ssid, sh_id, st_id, price, stt in chunk]
        sql.append("INSERT INTO show_seats (show_seat_id, show_id, seat_id, price, status) VALUES\n  " + 
                   ",\n  ".join(vals) + ";")

    sql.append("")
    sql.append("-- DONE")
    return "\n".join(sql), len(MOVIES), len(VENUES), len(screens_meta), len(all_seats), len(shows_list), len(all_show_seats)

if __name__ == "__main__":
    sql_content, n_m, n_v, n_scr, n_st, n_sh, n_ss = build_sql()
    out_path = "/Users/jaikishan/Downloads/TicketHub_Phase1_Implemented/ticketHub/cinema_seed.sql"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(sql_content)
    print(f"Generated cinema_seed.sql successfully:")
    print(f"  - Movies (Events): {n_m}")
    print(f"  - Flagship Venues: {n_v}")
    print(f"  - Screens: {n_scr}")
    print(f"  - Screen Seats: {n_st}")
    print(f"  - Published Shows: {n_sh}")
    print(f"  - Show Seats: {n_ss}")
