"""DEADLANDS lore (Milestone 8): every document, journal, report and transcript the survivor can find.

Each entry: id, category, where it turns up, title, body, and optionally a series and part number.

Categories: research (Harley's notes), character (the four's journals), military, medical,
government, newspaper, radio (transcripts and recordings), missing (missing-person reports),
journal (personal journals, photographs, graffiti).

Places: house, school, medical, police, store, office, military, lab, camp, farm, utility, any;
and story places that only exist in one kind of site: hospital, facility (the Kessler research
facility), evacuation (Camp Meridian evacuation sites), expedition (Harley's expedition camp),
milfacility (Site Calder), boss_<kind> (carried by that boss), trail (written on the fly with a
place in it: %1$s town, %2$s direction, %3$s distance).

The outbreak, for the writers: Project LANTERN at the Kessler Regional Research Laboratory tested
NR-7, a neural regeneration agent, on soldier volunteers. Subject P-0 was Sergeant Elias Harley,
Dr. Harley's younger brother. Day -95: containment breach. Day -90: first cases in Ashford County.
Day -75: Ashford General overwhelmed. Day -60: evacuation to Camp Meridian under Colonel Marcus Hale,
who closed the gates. Day -40: communications fail. Day -20: collapse. Day 0: the survivor wakes
near Harley's camp. The Abomination is Quarantine Ward C; the Butcher is Leon Vask, the lab's animal
handler; the Warden is Colonel Hale; the Experiment is Subject E-12; Patient Zero is Elias.
"""

ENTRIES = []


def L(id, category, places, title, body, series="", part=0):
    ENTRIES.append(dict(id=id, category=category, places=places, title=title, body=body, series=series, part=part))


# =============================================================================================== Harley's research
L("harley_research_1", "research", ["lab", "medical", "camp"], "Research Note 1: Onset",
  "Incubation four to nine hours from a bite, under two from deep tissue exposure. Fever first, then a lucid stretch that fools "
  "everyone, families most of all. Pupils stop responding before the patient stops talking. Write this on every wall you can.",
  "harley_research", 1)
L("harley_research_2", "research", ["lab", "medical", "office"], "Research Note 2: The Brain Stem",
  "NR-7 does exactly what it was built to do. It regenerates. It just does not stop at the wound. It rebuilds the brain stem around "
  "itself and keeps only what feeds it: movement, hunger, pursuit. The rest of the person is not destroyed. It is disconnected.",
  "harley_research", 2)
L("harley_research_3", "research", ["lab", "camp", "medical"], "Research Note 3: Nests",
  "The biomass in the nests is not waste. It is a colony. Samples taken near a nest core show NR-7 in a dormant, almost seed-like "
  "form. Dormant means stable. Stable means I can study it without it studying me back.",
  "harley_research", 3)
L("harley_research_4", "research", ["lab", "office", "camp"], "Research Note 4: Sound",
  "They hunt by sound before sight. A car door carries a hundred blocks on a still night. The screamers are not calling for help; "
  "they are calling the others. Kill the screamer first or leave before it finishes.",
  "harley_research", 4)
L("harley_research_5", "research", ["lab", "medical"], "Research Note 5: An Inhibitor?",
  "If NR-7 can be told to stop, it is told by a protein it makes itself, in tiny amounts, in the first carrier only. The first strain "
  "had a brake. Every copy since has lost it. I need the original. I need to know who P-0 was.",
  "harley_research", 5)
L("harley_research_6", "research", ["lab", "camp", "office"], "Research Note 6: Requirements",
  "To finish an inhibitor I need: fresh tissue from the hospital's pathology freezers, the Kessler trial documents, Patient Zero's "
  "records, whatever the army took from us, and the formula notes Rourke kept to himself. And samples from the big ones, if anyone "
  "is mad enough to get close.",
  "harley_research", 6)
L("harley_research_7", "research", ["lab", "medical", "camp"], "Research Note 7: The Large Forms",
  "Some of them grow. Not bigger zombies: different animals. The agent concentrates in them like a reactor. One sample from any of the "
  "large forms holds more active NR-7 than a hundred of the common infected. Dangerous. Invaluable.",
  "harley_research", 7)
L("harley_research_8", "research", ["lab", "office"], "Research Note 8: For Whoever Reads This",
  "If I do not come back, everything is in the field case. Give it to someone with a lab and a generator. Do not give it to the army. "
  "They had our data for three months before the first case and they used it to plan a fence.",
  "harley_research", 8)

# =============================================================================================== the four
L("harley_journal_1", "character", ["expedition", "camp"], "Harley's Journal: The Expedition",
  "Day -31. Four of us now. The driver, Itzbreadloaf, will not stop talking, which keeps the rest of us awake. FelixSyn keeps watch "
  "like he expects the trees to attack. Imlilylol found us, not the other way round. We go to the nests tomorrow.",
  "harley_journal", 1)
L("harley_journal_2", "character", ["expedition"], "Harley's Journal: Samples",
  "Day -29. Three good samples from the nest by the river. Felix carried me out when the screamer started. I owe him more than I can "
  "write here. Tomorrow we try for the clinic in town. We need a freezer, and a fence that still stands.",
  "harley_journal", 2)
L("harley_journal_3", "character", ["expedition", "camp"], "Harley's Journal: Separated",
  "Day -28. Roadblock. Gunfire from our own side. We scattered. I ran until I could not. I left the field case under the third tent "
  "because I could not carry it and run. If anyone finds this: I am alive. I am going back to the lab road. I will not stop.",
  "harley_journal", 3)
L("itz_log_1", "character", ["store", "utility", "camp"], "Itzbreadloaf's Logbook: Route 9",
  "Monday. Bread, forty trays. Two stops closed, one had a soldier at the door telling me to turn round. Thursday. Nobody signs for "
  "anything any more. Saturday. Dropped the bread at the church because people were hungry and the shop was gone.",
  "itz_log", 1)
L("itz_log_2", "character", ["utility", "camp", "store"], "Itzbreadloaf's Logbook: The Doctor",
  "Picked up a doctor walking the lab road with a case she would not let me touch. Harley. Talked about proteins for an hour. I "
  "understood about four words. She fell asleep against the window with her hand on that case.",
  "itz_log", 2)
L("itz_log_3", "character", ["utility", "camp"], "Itzbreadloaf's Logbook: Repairs",
  "Van threw a belt on the hill. Fixed it with a belt from a lawnmower and a prayer. Felix says I talk to the engine. Felix is right. "
  "Engine says it needs a proper set of parts and two tyres that are not held together by optimism.",
  "itz_log", 3)
L("itz_log_4", "character", ["evacuation", "utility"], "Itzbreadloaf's Logbook: Roadblock",
  "Lost them. Soldiers at the roadblock shot at the van. Not at the dead. At us. I drove until the fuel light came on and then I kept "
  "going. I am fixing her up. When she runs I am going back for every one of them.",
  "itz_log", 4)
L("lily_letter_1", "character", ["camp", "store", "house"], "Imlilylol's Letter to Nobody",
  "I write these and leave them where travellers rest. Maybe one finds someone I know. If you are reading this: the water at the "
  "north pumps is clean, the bridge on the east road is down, and nobody in the bandit towns keeps their word.",
  "lily_letter", 1)
L("lily_letter_2", "character", ["camp", "store"], "Imlilylol's Letter: The Scientist",
  "Walked with a scientist for a while. She looked at the dead the way I look at a map: where did you come from, where are you going. "
  "I liked her. I told her I would find her again. I keep my word, mostly.",
  "lily_letter", 2)
L("lily_letter_3", "character", ["camp", "utility"], "Imlilylol's Letter: Radio",
  "Somebody is still broadcasting on 91.2. Survivors, settlements, the odd idiot singing. If I could get one of the old radio towers "
  "working I could reach the whole county. I could find them.",
  "lily_letter", 3)
L("lily_letter_4", "character", ["camp", "milfacility", "military"], "Imlilylol's Letter: The Airfield",
  "A soldier I traded with swore there is a helicopter at Site Calder. Hidden hangar, a pad painted to look like a car park. They "
  "left in a hurry and left one behind. He wanted three cans of fuel for that story. I gave him two.",
  "lily_letter", 4)
L("felix_notes_1", "character", ["military", "evacuation"], "FelixSyn's Field Notes: Meridian",
  "Assigned to Camp Meridian. Colonel Hale wants a perimeter by dawn. Okafor asked who the perimeter keeps out. Hale did not answer. "
  "I should have asked again.",
  "felix_notes", 1)
L("felix_notes_2", "character", ["military", "evacuation"], "FelixSyn's Field Notes: The Order",
  "Order came down at 0300: gates closed, no exceptions. There were two hundred people outside the wire. Families. I watched Okafor "
  "put her rifle down. Then I put mine down. We walked out through the east gate before they could lock it.",
  "felix_notes", 2)
L("felix_notes_3", "character", ["camp", "military"], "FelixSyn's Field Notes: The Doctor",
  "We found a lab tech who walked out of Kessler with nothing but her badge. She says the army knew. She says Hale knew. I believe "
  "her, because I was there when he read the orders and he did not look surprised. I asked her about a Dr. Harley. She said Harley "
  "got out. That is all she knew. It is more than I had yesterday.",
  "felix_notes", 3)
L("felix_notes_4", "character", ["military", "camp", "evacuation"], "FelixSyn's Field Notes: If I See Him Again",
  "If Hale is still out there, dead or alive, I owe him a conversation. If he is dead and walking, I owe him rest. Either way I want "
  "the orders. The real ones, with his signature. People should know who closed that gate.",
  "felix_notes", 4)
L("trail_itzbreadloaf", "character", ["trail"], "A Note Pinned to a Fuel Pump",
  "Gone to %1$s, %2$s of here, about %3$s blocks. Heard they have a garage and nobody guarding the parts. If you are Harley, or "
  "Felix, or anyone who knows a doctor with a case: wait for me. - the bread guy")
L("trail_imlilylol", "character", ["trail"], "A Letter Left at a Camp",
  "Heading for %1$s, %2$s, perhaps %3$s blocks if the road holds. Trading there for a few days. If this reaches the scientist or "
  "her friends: I have news, and I would rather give it to your face. - Lily")
L("trail_felixsyn", "character", ["trail"], "Words Scratched Inside a Checkpoint Booth",
  "F.S. moving to %1$s. %2$s, %3$s blocks. Holding there. Do not follow if you are army.")

# =============================================================================================== Adam (Phase 5)
L("adam_research_1", "research", ["lab", "facility", "office"], "Adam's Lab Notes: Sample Stability Log",
  "Cold chain for the NR-7 trial batches. Batches one to six degrade within ninety days at four degrees. Batch seven was drawn before "
  "the agent was modified for the trial and holds at minus eighty. If anyone ever needs to know what NR-7 was before they changed it, "
  "batch seven is the answer.",
  "adam_research", 1)
L("adam_research_2", "research", ["lab", "facility"], "Adam's Lab Notes: The Modification",
  "Rourke wants faster regeneration for the trial. Faster means fewer brakes. I put my objection in writing, twice. Both memos came "
  "back with a colonel's initials on them and nothing else.",
  "adam_research", 2)
L("adam_research_3", "research", ["lab", "facility", "office"], "Adam's Lab Notes: Comparison",
  "Ran the trial agent against batch seven under the scope. Batch seven repairs and stops. The trial agent repairs and keeps going. "
  "Whatever turns a person into one of them lives in the difference between those two slides.",
  "adam_research", 3)
L("adam_research_4", "research", ["facility", "lab"], "Adam's Lab Notes: Sealed",
  "Sealed batch seven in a transport case and put it in Harley's freezer, not mine. Mine is on the log they check. Hers is not. "
  "If I'm wrong about all this, I wasted an afternoon. If I'm right, someone will need it.",
  "adam_research", 4)
L("adam_tape_1", "radio", ["lab", "facility", "office"], "Recording: Adam, Voice Memo 1",
  "[Tape hiss] It's me. The phones are down at the lab, so I'm recording this in case the post still works. I'm fine. I'm coming "
  "home. Keep the doors locked and don't open them for anyone in uniform. I love you. All three of you.",
  "adam_tape", 1)
L("adam_tape_2", "radio", ["house", "camp", "office"], "Recording: Adam, Voice Memo 2",
  "[Tape hiss] Day nine, I think. I keep recording these. It helps to talk to you even if you can't hear it. The roads north are "
  "blocked. I'll find another way. I always find another way home. Don't give up on me.",
  "adam_tape", 2)
L("adam_tape_3", "radio", ["camp", "lab"], "Recording: Adam, Voice Memo 3",
  "[Tape hiss] Someone told me a group got out of town before the bridge went. A woman and two kids. Could be anyone. Could be you. "
  "I'm choosing to believe it's you.",
  "adam_tape", 3)
L("adam_photo", "journal", ["office", "lab", "facility"], "Photograph: A Family on a Pier",
  "A creased photograph from a lab coat pocket. A man in glasses, a woman laughing at something off camera, two children squinting "
  "into the sun. On the back, in pen: Summer. Home by seven, I promise.")

# =============================================================================================== the trail of hope (Phase 5 step 3)
L("trail_hope_register", "military", ["military", "evacuation", "police"], "Checkpoint Register, North Road",
  "Page 14 of a soaked register. Name, number in party, destination. Halfway down, in a careful hand: one adult, two children, "
  "destination Pinecrest. Someone has added in pencil beside it: father not with them, still at the lab?")
L("trail_hope_radio", "radio", ["utility", "camp", "military"], "Recording: Night Traffic, 91.2",
  "[Static] ...convoy from the north road reached the reservoir. Forty-one people. Women, kids, two dogs. If you're out there "
  "looking for someone, Pinecrest is taking names. Say it again: Pinecrest is taking names. [Static]")
L("trail_hope_drawing", "journal", ["house", "school", "camp"], "Photograph: A Child's Drawing",
  "A crayon drawing, folded small. A house with every window yellow, three stick figures in a car, a fourth by the door. Under "
  "the fourth one, in big wobbly letters: DAD WILL FIND US.")
L("adam_family_letter", "journal", ["story"], "A Letter on the Kitchen Table",
  "Adam. If you're reading this, you came home. We waited four days. The soldiers say the north road is still open, so we're "
  "going to the reservoir camp at Pinecrest. The kids made me promise to leave the porch light on. There's no power, but I left "
  "it switched on anyway. We are all right. We are together. Home by seven, remember? We're holding you to it. All my love.")

# =============================================================================================== the Asesino (Phase 5)
# The Aberrant (Story Canon 2026-11-12): a Kessler scientist whose cure was wrong, and who gave it to his sick daughter.
L("asesino_file_1", "research", ["lab", "facility", "office"], "Cure Notes, Batch Twelve",
  "Batch twelve reverses NR-7 in the cultures. Sixty hours and not one cell has turned. Rourke says it is nowhere near ready for "
  "people. Rourke goes home to an empty flat. I go home to Mia coughing through the wall, and I count the coughs, and there are more every week.")
L("asesino_file_2", "journal", ["house", "lab"], "A Letter Never Sent",
  "Mia, you were so brave about the needle. You said it felt like a bee. I told you it would make the tired go away, and I believed "
  "it. I need you to know that I believed it. I am sorry I was wrong. I am sorry I was not a better scientist. I am sorry.")
L("asesino_file_3", "medical", ["facility", "lab", "medical"], "Ward C, Bed Four",
  "Patient: Mia Reyes, age 8. Unauthorised compound given by her father, Dr. T. Reyes. She did not wake. Dr. Reyes stayed at the bedside "
  "all night, holding her hand, and would not speak to staff. By the evening he was gone from the ward, along with the rest of batch twelve.")

# The Guest (Story Canon 2026-11-12): a Kessler worker's old dog, sick with cancer, bitten during the outbreak.
L("guest_file_1", "medical", ["house", "medical", "store"], "Vet Appointment Card",
  "Patient: Biscuit (dog, nine years). Owner: D. Mercer, maintenance, Kessler Regional. Bone cancer in the left foreleg; the owner "
  "declined amputation. Pain relief only. We talked about what comes next. He asked for a little more time with her.")
L("guest_file_2", "journal", ["facility", "office", "utility"], "Maintenance Log, Night Shift",
  "Brought Biscuit in again. Security pretends not to see her under my desk. She can't manage the stairs any more, so I carry her. "
  "Alarms going on and off in Wing D since six. If they send us home early we're going to the lake. She always liked the lake.")
L("guest_file_3", "government", ["facility", "military", "police"], "Incident Note, Loading Bay",
  "Maintenance worker Mercer evacuated with minor injuries. Refused treatment until someone found his dog. The dog was bitten by an "
  "escaped subject during the breach and ran. Witnesses say she was not limping any more. She was standing up on her back legs.")

# The subway (world/subway): found in train luggage, ticket booths and hideouts.
L("subway_paper", "newspaper", ["subway"], "The Morning Line, Final Edition",
  "TRAINS RUN TO THE LAST. The transit authority says services north will continue \"for as long as there are drivers\". "
  "Queues at Central reached the street by six. Officials ask passengers not to bring pets, livestock, or anyone who is unwell.")
L("subway_driver_log", "journal", ["subway"], "Driver's Log, Line 2",
  "Last run. Forty on a car built for twenty. Someone in car two was coughing and they made her get off at Mercer Street. "
  "She stood on the platform and watched us go. The tunnel lights went out behind us one by one. I didn't stop. I'm sorry.")
L("subway_child_note", "journal", ["subway"], "A Note in Crayon",
  "MUM I WAITED BY THE TICKET MACHINE LIKE YOU SAID. THE MAN ON THE SPEAKER SAID YOUR NAME WRONG. "
  "I AM GOING WITH THE LADY WITH THE RED BAG. SHE SAYS YOU WILL FIND US AT THE CAMP. I LEFT YOU MY TORCH.")
L("subway_hideout_rules", "journal", ["subway"], "Rules, Pinned Up in a Back Room",
  "1. No light past the barricade. 2. Nobody comes in bitten. Nobody. 3. Water is shared. 4. If the PA comes on, "
  "stay quiet: it brings them down the stairs. 5. Day 31: Marcus didn't come back. 6. We're leaving tonight. Take what you need.")
L("subway_staff_memo", "government", ["subway"], "Transit Staff Memo, Do Not Post",
  "Staff are reminded NOT to repeat the evacuation announcement after the tunnels are sealed. The recording loops on its own. "
  "If a station's power fails, the PA's backup battery will keep it running for some weeks. There is no way to switch it off remotely.")

# =============================================================================================== military
L("mil_lantern_brief", "military", ["military", "lab", "facility"], "Project LANTERN: Briefing",
  "CLASSIFIED. Project LANTERN seeks to restore function to soldiers with catastrophic injuries using the neural regeneration agent "
  "NR-7. Human trials authorised at Kessler Regional Research Laboratory under Dr. V. Rourke. Military oversight: Col. M. Hale.")
L("mil_breach_report", "military", ["military", "facility"], "Incident Report: Kessler Containment",
  "Day -95. Containment failure, Wing D. Subject P-0 unaccounted for. Two staff injured, one missing (V-ASK, L., animal care). Civil "
  "authorities NOT to be informed. Cover story: chemical leak. Signed: Col. M. Hale.")
L("mil_knew", "military", ["military", "milfacility", "office"], "Memorandum: Public Notification",
  "Day -88. Projections indicate county-wide spread within 21 days. Recommend public notification be delayed to prevent disorder "
  "on evacuation routes. Priority: preservation of LANTERN data and personnel. Population management via Camp Meridian.")
L("mil_meridian_orders", "military", ["evacuation", "military"], "Camp Meridian: Standing Orders",
  "1. Screen all arrivals for fever. 2. Fever cases to Ward C. 3. Ward C is not to be opened for any reason. 4. When capacity is "
  "reached, gates close. Capacity is reached when the Colonel says it is reached.")
L("mil_gate_order", "military", ["evacuation", "milfacility"], "Order 0300",
  "All gates to be sealed at 0300. No further admissions. Personnel who refuse this order are to be treated as deserters. "
  "Signed, Colonel Marcus Hale. Below, in another hand: two hundred and twelve people outside. God forgive us.")
L("mil_ward_c", "military", ["evacuation", "medical"], "Ward C Log",
  "Day -58: 40 in Ward C. Day -57: 90. Day -56: staff withdrawn. Day -55: noises from Ward C have changed. Something heavy against "
  "the door. The door is bending outward. Day -54: no further entries.")
L("mil_okafor_1", "military", ["military", "evacuation"], "Pvt. Okafor's Log, 1",
  "They say we are protecting people. Mostly we are counting them. Sergeant says don't count, it gets in your head. Felix counts "
  "anyway, under his breath. Four hundred and six today.", "okafor", 1)
L("mil_okafor_2", "military", ["military", "evacuation"], "Pvt. Okafor's Log, 2",
  "Saw the Colonel take a call in the truck. He said 'how long do we have' and then 'understood' and then he sat there for a long "
  "time. When he came out he told us to double the wire.", "okafor", 2)
L("mil_okafor_3", "military", ["military", "evacuation", "camp"], "Pvt. Okafor's Log, 3",
  "Walked out with Felix. We will be shot if they find us. I don't care. There was a girl at the wire with a stuffed rabbit and I am "
  "not going to be the one who tells her no.", "okafor", 3)
L("mil_okafor_4", "military", ["camp", "house"], "Pvt. Okafor's Log, 4",
  "Bitten. Felix doesn't know. I have maybe a day of being me. Felix, if you find this: you did right. Find the doctor. Make it "
  "mean something.", "okafor", 4)
L("mil_calder_manifest", "military", ["milfacility", "military"], "Site Calder: Evacuation Manifest",
  "Rotary-wing assets: 3. Departed: 2. Remaining: 1, grounded (rotor damage, avionics fault). Leave it. Fuel tanks drained, "
  "turbine components packed for transport and never collected.")
L("mil_calder_access", "military", ["milfacility", "military", "facility"], "Site Calder: Access Levels",
  "Green cards: perimeter, stores. Blue cards: hangar, motor pool. Red cards: command, armoury, the Kessler archive. Lost cards "
  "must be reported to the Colonel personally.")
L("mil_checkpoint_log", "military", ["military", "police"], "Checkpoint 7 Log",
  "0600 quiet. 0900 two cars turned back. 1300 a man with a fever begged to be let through to see his kids. Turned back. 1800 he "
  "came back. Not a man any more. Turned him back with the rifle.")
L("mil_hale_last", "military", ["military", "milfacility", "boss_warden"], "Colonel Hale's Last Entry",
  "I followed every order. I wrote half of them. When the dead reached the command post I locked myself in and I can hear them in "
  "the corridor wearing my officers' faces. If this is judgment, it is fair.")
L("mil_e12_log", "military", ["facility", "lab"], "Observation Log: Subject E-12",
  "E-12 shows enhanced regeneration and limb elongation. Spits a caustic fluid when cornered. Responds to its old name for about "
  "four seconds before it stops. Recommend termination. Request denied by Dr. Rourke: 'we learn more alive'.")
L("mil_e12_escape", "military", ["facility", "military", "boss_experiment"], "Incident: E-12",
  "E-12 breached its enclosure by dissolving the hinges. Last seen heading for the treeline. Search abandoned after the second "
  "team failed to report. Do not approach. It remembers the people who hurt it.")
L("p0_1", "military", ["facility", "lab"], "Patient Zero Records, 1",
  "Subject P-0. Sgt. Elias Harley, 27. Injury: spinal, total paralysis from an IED. Volunteered for NR-7 trial on condition his "
  "family not be told. Sister is a researcher in the civilian wing. Request granted.", "p0", 1)
L("p0_2", "military", ["facility", "lab"], "Patient Zero Records, 2",
  "Day 6 of trial. P-0 moved his toes. Day 9: stood. Day 12: walked the length of the ward and asked for his sister. Request "
  "denied. P-0 cried. We noted elevated NR-7 expression.", "p0", 2)
L("p0_3", "military", ["facility", "milfacility"], "Patient Zero Records, 3",
  "Day 19. P-0 shows aggression in the evenings, lucidity in the mornings. He asked us to stop. He said 'something else is learning "
  "to use my hands'. Dr. Rourke increased the dose.", "p0", 3)
L("p0_4", "military", ["facility", "milfacility"], "Patient Zero Records, 4",
  "Day 24. P-0 produces a regulatory protein no later carrier shows. Rourke calls it 'the brake'. With the brake present, NR-7 "
  "holds. Without it, it consumes. Blood from P-0 is the only source.", "p0", 4)
L("p0_5", "military", ["facility"], "Patient Zero Records, 5",
  "Day 25. P-0 broke restraints during the night shift. He did not attack the orderly. He opened every cell in Wing D and walked "
  "out into the rain. Vask followed him. Neither came back.", "p0", 5)
L("p0_6", "military", ["boss_patient_zero", "milfacility"], "Patient Zero Records, 6: Elias's Note",
  "Found on the body in his own handwriting: H, I am still in here. Not all the time. It keeps me because I am useful to it. If you "
  "find me, take what you need. Make them stop. I am sorry I did not tell you. - E", "p0", 6)

# =============================================================================================== medical
L("med_sato_1", "medical", ["hospital", "medical"], "Dr. Sato's Ward Notes, 1",
  "Day -86. Six fever cases, all from the north of the county. Bloods unremarkable apart from an unidentified protein. Asked the "
  "county lab to look at it. They said Kessler had already asked for our samples.", "sato", 1)
L("med_sato_2", "medical", ["hospital", "medical"], "Dr. Sato's Ward Notes, 2",
  "Day -80. Forty cases. Patients die, then they get up. I have written that sentence three times and crossed it out twice. "
  "Security has stopped answering their radios.", "sato", 2)
L("med_sato_3", "medical", ["hospital"], "Dr. Sato's Ward Notes, 3",
  "Day -76. We have moved everyone who can walk to the top floor and barricaded the stairs. The pathology freezers are full of "
  "samples nobody will ever analyse. If anyone wants them, they are in the basement. Bring a torch.", "sato", 3)
L("med_sato_4", "medical", ["hospital", "medical"], "Dr. Sato's Ward Notes, 4",
  "Day -74. Army helicopter landed on the roof. They took the samples and two of our doctors. Not the patients. I stayed. Somebody "
  "has to.", "sato", 4)
L("med_intake", "medical", ["hospital", "medical"], "Patient Intake Form",
  "Name: illegible. Complaint: bitten by neighbour. Temperature 40.1. Notes: patient insists the neighbour was dead before the bite. "
  "Referred to psychiatry. Psychiatry not answering.")
L("med_autopsy", "medical", ["hospital", "lab"], "Autopsy Report 0412",
  "Brain stem enlarged and fibrous. Higher brain intact but isolated, as if someone cut the wires and left the house standing. "
  "Subject re-animated during the procedure. Procedure discontinued.")
L("med_pharmacy", "medical", ["medical", "store"], "Pharmacy Note",
  "No more antibiotics, they don't work anyway. No more painkillers, people fight over them. Bandages on the left shelf. If you take "
  "the last of something, leave a note so the next person doesn't waste the trip.")
L("med_triage", "medical", ["hospital", "evacuation"], "Triage Card",
  "RED: immediate. YELLOW: delayed. GREEN: minor. BLACK: expectant. Someone has added a fifth colour in marker, grey, with one word "
  "next to it: watch.")
L("med_nurse", "medical", ["hospital", "house"], "Nurse's Note",
  "Mum, I'm staying. They need everyone. Don't come to the hospital whatever you hear on the radio. Lock the door and only open it "
  "for me. I'll knock three times. I love you. - J")
L("med_blood_drive", "medical", ["medical", "school", "office"], "Blood Drive Flyer",
  "Ashford General needs you! Give blood this Saturday at the school gym. Free juice and biscuits. Someone has written across it: "
  "THEY TOOK IT ALL TO KESSLER.")
L("med_ward_c_intake", "medical", ["evacuation", "hospital"], "Ward C Transfer List",
  "Ninety-one names, transferred from Ashford General to Camp Meridian, Ward C, for 'observation'. Not one discharge recorded. The "
  "last page is soaked through and stuck to the clipboard.")
L("med_vask_file", "medical", ["facility", "lab", "boss_butcher"], "Personnel File: Leon Vask",
  "Animal care technician, Kessler. Duties: feeding and restraint of trial subjects. Complaints from staff: 'talks to the subjects', "
  "'brings them meat from home'. Reprimanded twice. Badge never returned.")
L("med_vask_note", "medical", ["facility", "farm"], "A Note in a Butcher's Hand",
  "They are hungry all the time. Nobody else feeds them right. I feed them. They know my step now. They are my animals and nobody is "
  "taking them away from me.")
L("med_sample_label", "medical", ["hospital", "lab"], "Sample Freezer Inventory",
  "Rack 3: NR-7 positive, early. Rack 4: NR-7 positive, late. Rack 5: controls. Rack 6: removed by military personnel, day -74. "
  "Racks 3 and 4 intact. Keep frozen.")

# =============================================================================================== government
L("gov_emergency", "government", ["office", "police", "school"], "County Emergency Declaration",
  "A state of emergency is declared in Ashford County. Residents are asked to remain indoors, report fever cases to the hotline, "
  "and await evacuation instructions. Do not attempt to restrain the sick.")
L("gov_evac_notice", "government", ["school", "house", "office"], "Evacuation Notice",
  "Residents of this district will be evacuated to Camp Meridian. Bring identification, medication and one bag. Pets cannot be "
  "accommodated. Buses depart from the school at 0700. Do not be late. Buses will not wait.")
L("gov_curfew", "government", ["police", "office", "store"], "Curfew Order",
  "A curfew is in effect from dusk to dawn. Anyone outdoors after dark may be detained. Lights must be kept low. Noise must be kept "
  "to a minimum. Stay inside. Stay quiet. Stay alive.")
L("gov_hotline", "government", ["office", "medical"], "Fever Hotline Script",
  "Ask: is the person conscious? Is the person aggressive? Has the person been bitten? If YES to any: advise the caller to leave the "
  "building and wait for assistance. Note: assistance is no longer being dispatched.")
L("gov_census", "government", ["office", "police"], "Census of the Dead",
  "District 4: 1,212 deceased, 380 missing, 94 accounted for. District 5: no response. District 6: no response. Somebody has given "
  "up on the columns halfway down and written the word 'everyone'.")
L("gov_ration", "government", ["store", "office", "school"], "Ration Card",
  "This card entitles the holder to one day's ration at any distribution point. Punched eleven times. The twelfth hole was never "
  "made.")
L("gov_governor", "government", ["office", "military"], "Letter from the Governor's Office",
  "The Governor thanks Colonel Hale for his discretion. The situation in Ashford County must not become a national story before the "
  "LANTERN material is secure. Additional resources will follow. They did not.")
L("gov_school_notice", "government", ["school"], "School Closure Notice",
  "Ashford schools will close until further notice. The gym will serve as a fever screening centre. Parents should collect "
  "children by noon. Uncollected children will be evacuated with staff.")
L("gov_quarantine_map", "government", ["police", "office", "military"], "Quarantine Zone Map",
  "A county map with three red circles drawn on it, each larger than the last, each dated a week apart. The last circle runs off "
  "the edge of the page.")
L("gov_bounty", "government", ["police", "store"], "Notice: Looting",
  "Looters will be shot. This notice is signed by a sheriff who, according to the scrawl beneath it, was himself last seen leaving "
  "town with a pickup full of the hardware store.")

# =============================================================================================== newspaper
L("news_1", "newspaper", ["house", "store", "office"], "Ashford Gazette: Mystery Fever",
  "Day -89. Six residents hospitalised with a fever doctors cannot identify. Ashford General urges calm. A spokesperson for nearby "
  "Kessler Laboratory denied any connection to an 'unscheduled chemical release' reported last week.", "gazette", 1)
L("news_2", "newspaper", ["house", "store", "school"], "Ashford Gazette: Hospital Under Strain",
  "Day -78. Ashford General has suspended visiting hours as fever cases pass two hundred. Police report a rise in 'unprovoked "
  "assaults'. The Mayor asks residents to check on elderly neighbours, from a distance.", "gazette", 2)
L("news_3", "newspaper", ["house", "store", "police"], "Ashford Gazette: Army Arrives",
  "Day -66. Army vehicles were seen on the county road this morning. Officials describe their presence as 'precautionary'. "
  "Residents in the north of the county have been asked to prepare for possible relocation.", "gazette", 3)
L("news_4", "newspaper", ["house", "office"], "Ashford Gazette: Evacuation Begins",
  "Day -61. Buses began carrying residents to Camp Meridian today. Colonel Marcus Hale told the Gazette the camp has 'room for "
  "everyone who needs it'. Queues stretched for two miles.", "gazette", 4)
L("news_5", "newspaper", ["house", "store", "office"], "Ashford Gazette: Meridian Gates Closed",
  "Day -59. Hundreds of families remain outside Camp Meridian after its gates were closed overnight. Army spokesmen refused to "
  "comment. Gunfire was heard near the perimeter. This edition was printed on a hand press.", "gazette", 5)
L("news_6", "newspaper", ["house", "store"], "Ashford Gazette: Last Edition",
  "Day -42. This is the last edition. The phones are down, the power is down, the press is broken and our editor is missing. To "
  "whoever finds this: stay quiet after dark, stay off the main roads, and look after each other.", "gazette", 6)
L("news_school", "newspaper", ["school"], "School Newsletter",
  "This term: the science fair, the bake sale and our trip to the Kessler Laboratory open day! Thank you to Dr. Rourke for showing "
  "the year sixes around. They loved the animals.")
L("news_classified", "newspaper", ["house", "store", "office"], "Classifieds Page",
  "FOR SALE: generator, never used. WANTED: anyone who has seen Mira, age 7, red coat. MISSING: grey cat. SWAP: car for a week of "
  "food. Somebody has circled the generator and written: SOLD, NOT PAID FOR, SORRY.")
L("news_obituaries", "newspaper", ["house", "office"], "Obituaries, Day -70",
  "The obituary page runs to fourteen pages, then stops halfway down the fifteenth, as if the person writing them gave up or ran out "
  "of time.")
L("news_flyer", "newspaper", ["store", "house", "school"], "Church Flyer",
  "Soup kitchen every evening at St. Brendan's. All welcome. Bring a bowl. Below, a later note: soup kitchen closed. All welcome "
  "still. Bring a weapon.")

# =============================================================================================== radio transcripts
L("radio_ebs_1", "radio", ["utility", "house", "police"], "Transcript: Emergency Broadcast",
  "This is the Emergency Broadcast System. A public health emergency is in effect for Ashford County. Remain indoors. Do not travel. "
  "Further information will follow. [The message repeats for eleven hours.]", "ebs", 1)
L("radio_ebs_2", "radio", ["utility", "military"], "Transcript: Emergency Broadcast, Revised",
  "This is the Emergency Broadcast System. Camp Meridian is no longer accepting evacuees. Residents should shelter in place. Do not "
  "approach military checkpoints. [Static. A voice, off-mic: 'They can't say that.']", "ebs", 2)
L("radio_ham_1", "radio", ["house", "farm", "utility"], "Ham Radio Log, Station K4-ASH",
  "Day -45. Talked to a farm out west, a church downtown, two kids in a school attic. All fine for now. Day -41. Church not "
  "answering. Day -40. Nobody answering. Day -39. Keep transmitting anyway.", "ham", 1)
L("radio_ham_2", "radio", ["house", "utility", "camp"], "Ham Radio Log, Station K4-ASH (cont.)",
  "Day -30. Somebody on 91.2. A woman calling herself Lily, trading news between settlements. Gave me the water status for the north "
  "pumps. First good news in weeks.", "ham", 2)
L("radio_pilot", "radio", ["milfacility", "military"], "Transcript: Rotary Chatter",
  "'Calder, Dustoff Two, rotor's chewed, can't make the lift.' 'Dustoff Two, leave the bird, we are pulling out.' 'Copy, leaving the "
  "bird. Somebody should come back for her.' 'Nobody's coming back, Two.'")
L("radio_last", "radio", ["utility", "military", "office"], "Transcript: Final Broadcast, Station WASH",
  "This is WASH, Ashford's voice for forty years. I'm going to leave the music playing when I go. If you can hear it, you're not "
  "alone. Somebody chose that song for you. Goodnight, Ashford.")
L("radio_distress", "radio", ["house", "camp", "farm"], "Recording: Distress Call",
  "'Anyone, please, we're at the farm by the water tower, there's four of us, one's hurt-' [noise] '-they're at the door-' [the "
  "recording ends. The tape has been played many times.]")
L("radio_children", "radio", ["school", "house"], "Recording: Two Voices",
  "'Is it on?' 'It's on.' 'Hello, this is Sam and Priya, we're in the school roof, we have biscuits and a torch. If you're a grown-up "
  "please come. If you're a zombie please don't.' [giggling, then hushed]")
L("radio_military_chatter", "radio", ["military", "evacuation"], "Transcript: Meridian Net",
  "'Meridian Actual, perimeter north reports movement, large group.' 'Hold the line.' 'Sir, the group is civilians.' 'I said hold "
  "the line.' [long pause] 'Holding, sir.'")
L("radio_unknown_1", "radio", ["utility", "lab"], "Recording: 103.8",
  "[Static, then breathing. Then a voice, slow, as if remembering how:] 'H... not... gone.' [Static. The recording loops.]")
L("radio_tower_manual", "radio", ["utility"], "Radio Tower Maintenance Card",
  "If the transmitter is dead: check the fuses, replace the board, reseat the antenna feed. Keep spares in the hut. Spares are, of "
  "course, never in the hut.")
L("radio_settlement", "radio", ["camp", "house", "store"], "Transcript: 91.2, Settlement Roll Call",
  "'This is the holdout at the old school, twelve of us, need antibiotics, have spare rounds.' 'Copy. This is the farm co-op, have "
  "food, need a mechanic.' 'This is Lily, I'll be by both within the week. Keep your gates shut and your kettles on.'")

# =============================================================================================== missing persons
L("missing_mira", "missing", ["house", "store", "school", "police"], "Missing: Mira, Age 7",
  "Red coat, yellow boots, gap in her front teeth. Last seen at the Camp Meridian queue, day -60. If found, bring her to the farm "
  "co-op on the west road. We will wait. We will always wait.")
L("missing_tom", "missing", ["police", "store", "house"], "Missing: Tom Brennan",
  "Bus driver, 54. Took the 0700 school bus to Camp Meridian and did not come back. His wife has written on the bottom: Tom, if you "
  "read this, the dog is fine. Come home.")
L("missing_twins", "missing", ["school", "police", "house"], "Missing: The Okonkwo Twins",
  "Ada and Obi, 12. Separated from their mother at the school evacuation. A later line: Ada found safe at the farm co-op. Obi still "
  "missing. Please keep looking.")
L("missing_wall", "missing", ["police", "store", "office", "evacuation"], "Photograph: The Missing Wall",
  "A photograph of a wall covered in notes and pictures: faces, names, 'have you seen', 'please call', 'I'm at the church'. In the "
  "middle, someone has written in big letters: WE'RE STILL LOOKING.")
L("missing_dog", "missing", ["house", "store", "farm"], "Missing: Biscuit",
  "Golden retriever, friendly, answers to Biscuit, terrified of thunder. Reward: everything we have left, which is not much. Under "
  "it, in a child's writing: and a hug.")
L("missing_grandad", "missing", ["house", "medical"], "Missing: Walter Hughes",
  "82, wanders, may be confused, likes to sit on benches. Needs his heart pills. Last seen walking towards the hospital, day -77. "
  "Someone has added: he was at the hospital, he was helping.")
L("missing_soldier", "missing", ["military", "evacuation", "camp"], "Missing: Pvt. Ada Okafor",
  "Listed as a deserter by Camp Meridian command. Someone has crossed out 'deserter' and written 'hero' above it. Someone else has "
  "crossed that out. A third hand has written both again.")
L("missing_teacher", "missing", ["school"], "Missing: Ms. Delgado",
  "Year six teacher. Stayed behind to make sure every child got on a bus. The last bus left without her. If you know where she is, "
  "please tell her class. They ask every day.")
L("missing_family", "missing", ["house", "camp"], "Missing: The Hendersons",
  "Paul, Ruth, and their son Danny. Left by car for the coast, day -50. If you find this note at our house: we went west. We are "
  "sorry about the mess.")
L("missing_vask", "missing", ["facility", "farm", "police"], "Police Notice: Leon Vask",
  "Wanted for questioning in connection with missing livestock and an assault at Kessler Laboratory. Approach with caution. Last "
  "seen carrying a cleaver and a length of chain.")

# =============================================================================================== personal journals
L("journal_henderson_1", "journal", ["house"], "The Henderson Diary, 1",
  "Paul says it's like the flu. Ruth says it isn't. I'm writing this down because Mum says if things get bad we should remember what "
  "happened. Things are already a bit bad. School is shut. Danny, 10.", "henderson", 1)
L("journal_henderson_2", "journal", ["house"], "The Henderson Diary, 2",
  "Dad boarded up the windows. Mum filled the bath with water. Mr. Lewis next door was walking funny in his garden at night. Dad said "
  "not to look. I looked. Danny.", "henderson", 2)
L("journal_henderson_3", "journal", ["house", "gas_station", "store"], "The Henderson Diary, 3",
  "We're leaving for the coast. Dad says the roads west are clear. Mum cried when we left the house. I left my bike. I hope someone "
  "who needs it finds it. Danny.", "henderson", 3)
L("journal_henderson_4", "journal", ["camp", "store", "farm"], "The Henderson Diary, 4",
  "The car stopped. We walked. Dad hurt his leg. A lady with a big rucksack gave us water and told us which roads were safe. She "
  "said her name was Lily. Mum said she was an angel. Danny.", "henderson", 4)
L("journal_delgado_1", "journal", ["school"], "Ms. Delgado's Register",
  "Register, day -61. Present: 24. Absent: 3. Collected by parents: 9. To be evacuated with staff: 15. I am staying until every one "
  "of them is on a bus.", "delgado", 1)
L("journal_delgado_2", "journal", ["school"], "Ms. Delgado's Register, Later",
  "Buses: 2. Children: 15. Seats: 14. The driver says he'll come back. I sent the last child on my lap seat. I'll wait in the "
  "classroom. I've got marking to do anyway.", "delgado", 2)
L("journal_delgado_3", "journal", ["school"], "Ms. Delgado's Last Lesson",
  "Written large on the classroom board: Today's lesson: courage. Courage is being scared and doing the right thing anyway. Homework: "
  "be brave. Look after each other. I'm proud of every one of you.", "delgado", 3)
L("journal_clerk", "journal", ["store"], "Grocery Clerk's Notes",
  "Day -70: sold out of water. Day -68: sold out of everything. Day -65: manager left. Day -64: I gave the rest away. Day -63: "
  "people came back to thank me. Some came back for other reasons. I'm in the stockroom.")
L("journal_trucker", "journal", ["gas_station", "store", "utility"], "Trucker's Logbook",
  "Load: medical supplies for Ashford General. Destination reached day -73. Nobody to sign. Left the load at the doors and wrote "
  "'for whoever needs it' on the crates. Heading home. Home is six hundred miles. Wish me luck.")
L("journal_priest", "journal", ["house", "office", "camp"], "Father Doyle's Notebook",
  "Day -48. Seventy people sleeping in the church. Day -44. Fifty. Day -40. The radio stopped today and with it, I think, the last "
  "of the hope. I am ringing the bell at noon anyway. It reminds them they're alive.")
L("journal_kid_1", "journal", ["house", "school"], "Sophie's Diary",
  "Dear diary, today we played in the attic because it's safe. Mum says we're having an adventure. I don't think adventures are "
  "supposed to be this quiet. Mum says quiet is good now.", "sophie", 1)
L("journal_kid_2", "journal", ["house"], "Sophie's Diary, Later",
  "Dear diary, a nice soldier came. He had a scar and a funny name and he gave me his chocolate. He said his name was Felix and he "
  "was looking for a doctor. I told him to try the hospital because that's where doctors live.", "sophie", 2)
L("journal_farmer", "journal", ["farm"], "Farm Ledger",
  "Planted late. Harvest early. Fed nine families from the barn this month and turned away none. The dead don't eat corn, which is "
  "the only thing they've got going for them.")
L("journal_mechanic", "journal", ["utility", "gas_station"], "Garage Notice",
  "Closed. Back when the world is. Left the good tools under the bench for anyone who can use them. Left the bad tools on the bench "
  "for anyone who can't tell the difference.")
L("journal_student", "journal", ["school", "house"], "A Student's Essay",
  "'What I want to be when I grow up', by Ben, age 11. I want to be a doctor like the lady at the Kessler open day because she "
  "showed us how cells fix themselves. She said one day we'll fix anything. I hope it's soon.")
L("journal_graffiti_1", "journal", ["any"], "Photograph: Graffiti, 'Quiet'",
  "A photograph of a wall painted in big white letters: THEY HEAR YOU. Underneath, smaller: so whisper. Underneath that, smaller "
  "still: love you mum.")
L("journal_graffiti_2", "journal", ["any"], "Photograph: Graffiti, 'Kessler'",
  "A photograph of a bus shelter with KESSLER DID THIS sprayed across the timetable. Someone has added an arrow pointing to a phone "
  "number for the laboratory's public relations office.")
L("journal_graffiti_3", "journal", ["any"], "Photograph: Graffiti, 'Meridian'",
  "A photograph of a road sign for Camp Meridian. The word MERIDIAN has been crossed out and replaced with LIARS. Bullet holes run "
  "through the arrow.")
L("journal_photo_family", "journal", ["house"], "Photograph: A Family",
  "A family on a beach, squinting into the sun. On the back: 'Best holiday ever. Same time next year!' The date is last summer.")
L("journal_photo_lab", "journal", ["lab", "facility", "office"], "Photograph: Kessler Staff",
  "A staff photo on the laboratory steps. Third from the left, a woman holding a coffee cup is circled in pen: Harley. At the "
  "back, a tall man in a stained apron stands apart from the others.")
L("journal_photo_brothers", "journal", ["facility", "military", "house"], "Photograph: Siblings",
  "A soldier in dress uniform with his arm round a woman in a lab coat, both laughing. On the back: 'E & H, graduation day. Don't "
  "you dare re-enlist.' The soldier has the same eyes as she does.")
L("journal_diner", "journal", ["store"], "Diner Order Pad",
  "Table 4: two coffees, eggs. Table 6: pancakes, no syrup. Table 2: don't serve him, he's sick. Table 2: get out, get out, everyone "
  "get out. The rest of the pad is blank.")
L("journal_bunker", "journal", ["military", "house", "utility"], "Bunker Builder's Notes",
  "Twenty years everyone laughed at the bunker. Nobody is laughing now. Mostly because nobody else is around to laugh. Food for a "
  "year. Air filter. Books. No one to talk to. I should have built it bigger.")
L("journal_ranger", "journal", ["camp", "utility"], "Ranger's Notebook",
  "The wolves left the valley a week before the first case. The birds went quiet a day before. Animals knew. I wish I'd listened to "
  "them instead of the radio.")
L("journal_expedition", "journal", ["expedition"], "Expedition Supply List",
  "Sample jars x40, cold box, field microscope, Felix's spare magazines, Itz's 'lucky' wrench, Lily's maps, enough coffee for Harley. "
  "Someone has added: and enough patience for Itz.")

# =============================================================================================== the bosses
L("boss_ward_c_1", "medical", ["evacuation", "boss_abomination"], "Ward C: Final Inspection",
  "The door to Ward C has been forced outward. Inside, the beds have been dragged into the middle of the room and the walls are "
  "scored to head height. Whatever came out was not one thing. It was all of them, together.")
L("boss_butcher_1", "journal", ["farm", "boss_butcher"], "Leon's Ledger",
  "Fed them Monday. Fed them Tuesday. On Wednesday they took my animals away to the hospital in town. On Thursday I went to get "
  "them back. On Friday I was so hungry. I am still so hungry.")
L("boss_warden_1", "military", ["evacuation", "milfacility"], "Hale's Personal Letter",
  "Margaret, by the time you read this the camp will be closed and I will have done something I cannot undo. I told myself it was "
  "to save the country. It was to save the project. Burn this.")
L("boss_experiment_1", "military", ["facility", "boss_experiment"], "E-12: Name",
  "Subject E-12 was admitted under the name Grace, a volunteer from the county hospital's cleaning staff. She was promised money for "
  "her children. Her children were evacuated to Camp Meridian. Ward C.")
L("boss_pz_1", "military", ["milfacility", "facility"], "Rourke's Confession",
  "I increased the dose because I wanted to see. That is the whole truth. Elias asked me to stop and I wanted to see. Whatever is "
  "walking out there wearing him is my work, not his.")
