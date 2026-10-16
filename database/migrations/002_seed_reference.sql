INSERT INTO assistance_types (
    code, display_name, description,
    requires_driving_licence, requires_healthcare_certificate, requires_first_aid_certificate,
    public_disclaimer
) VALUES
(
    'TRANSPORTATION',
    'Transportation Assistance',
    'Help elders travel to hospitals, appointments, and other destinations.',
    TRUE, FALSE, FALSE,
    'Driving assignments require a verified driving licence. Volunteers are community helpers, not professional taxi services.'
),
(
    'GROCERY_SUPPORT',
    'Grocery / Essential / Medicine Shopping',
    'Help purchase groceries, daily necessities, and medicines.',
    FALSE, FALSE, FALSE,
    'Volunteers may shop on your behalf. They are not pharmacists and cannot prescribe medicines.'
),
(
    'TECHNOLOGY_SUPPORT',
    'Technology Support',
    'Help with phones, apps, video calls, and digital services.',
    FALSE, FALSE, FALSE,
    'Volunteers help with everyday digital tasks. They will never ask for your banking PIN or OTP.'
),
(
    'LONELINESS_SUPPORT',
    'Companionship / Loneliness Support',
    'Provide companionship, conversation, and social interaction.',
    FALSE, FALSE, FALSE,
    'This is friendly companionship, not professional counselling or medical therapy.'
),
(
    'BOOKING_SUPPORT',
    'Booking Support',
    'Help book appointments, transport, tickets, and other services.',
    FALSE, FALSE, FALSE,
    'Volunteers help with bookings. Confirm payment details yourself and never share OTPs.'
),
(
    'HOME_NURSE_ASSISTANCE',
    'Home Nurse Assistance',
    'Help arrange appropriate nursing and home healthcare support.',
    FALSE, TRUE, FALSE,
    'Volunteers help you find and coordinate care. They are not automatically licensed nurses or doctors.'
),
(
    'PET_ASSISTANCE',
    'Pet Assistance',
    'Help with pet feeding, walking, and basic pet-care tasks.',
    FALSE, FALSE, FALSE,
    'This covers basic pet care, not veterinary treatment.'
),
(
    'MINOR_HOME_MAINTENANCE',
    'Minor Home Maintenance',
    'Help with small household tasks and minor repairs.',
    FALSE, FALSE, FALSE,
    'This is help with small tasks, not licensed electrical, gas, or structural work.'
),
(
    'EMERGENCY_ASSISTANCE',
    'Emergency Assistance',
    'Help elders access appropriate urgent support and emergency services.',
    FALSE, FALSE, TRUE,
    'Call 112 or local emergency services for life-threatening emergencies. Verified volunteers may help you reach those services. They are not trained emergency responders unless separately certified.'
);

INSERT INTO matching_config (
    id, skill_weight, distance_weight, availability_weight, qualification_weight, default_radius_km
) VALUES (1, 0.40, 0.25, 0.20, 0.15, 15);
